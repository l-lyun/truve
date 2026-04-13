package com.truve.platform.musical.show.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.show.domain.constant.ArtistMembershipStatus;
import com.truve.platform.musical.show.domain.constant.MembershipPaymentMethod;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.domain.entity.ArtistMembership;
import com.truve.platform.musical.show.dto.MembershipRequest;
import com.truve.platform.musical.show.dto.MembershipResponse;
import com.truve.platform.musical.show.external.kafka.PaymentEventCommand;
import com.truve.platform.musical.show.external.kafka.PaymentPublisher;
import com.truve.platform.musical.show.repository.ArtistMembershipRepository;
import com.truve.platform.musical.show.repository.ArtistRepository;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {
	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final Long ARTIST_ID = 101L;
	private static final String ARTIST_NAME = "고은성";
	private static final long MONTHLY_AMOUNT = 5_000L;

	@Mock
	private ArtistRepository artistRepository;
	@Mock
	private ArtistMembershipRepository artistMembershipRepository;
	@Mock
	private PaymentPublisher paymentPublisher;
	@Mock
	private com.truve.platform.musical.s3.S3Service s3Service;

	@InjectMocks
	private MembershipService membershipService;

	@Test
	@DisplayName("멤버십 결제 준비에 성공하면 unified membership을 저장하고 결제 생성 이벤트를 발행한다.")
	void 멤버십_결제준비_성공() {
		Artist artist = org.mockito.Mockito.mock(Artist.class);
		ArtistRepository.ArtistDetailProjection artistDetail = mockArtistDetailProjection();
		MembershipRequest.CreatePayment request = createPaymentRequest(MembershipPaymentMethod.TOSS_PAY);

		when(artistRepository.findDetailById(ARTIST_ID)).thenReturn(java.util.Optional.of(artistDetail));
		when(artistMembershipRepository.findByUserIdAndArtistId(USER_ID, ARTIST_ID)).thenReturn(java.util.Optional.empty());
		when(artistRepository.getReferenceById(ARTIST_ID)).thenReturn(artist);
		when(artistMembershipRepository.save(any(ArtistMembership.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		MembershipResponse.CreatePayment response = membershipService.createPayment(ARTIST_ID, USER_ID, request);

		ArgumentCaptor<ArtistMembership> membershipCaptor = ArgumentCaptor.forClass(ArtistMembership.class);
		verify(artistMembershipRepository).save(membershipCaptor.capture());

		ArtistMembership savedMembership = membershipCaptor.getValue();
		assertThat(savedMembership.getOrderId()).startsWith("M");
		assertThat(savedMembership.getMonthlyAmount()).isEqualTo(MONTHLY_AMOUNT);
		assertThat(savedMembership.getPaymentMethod()).isEqualTo(MembershipPaymentMethod.TOSS_PAY);
		assertThat(savedMembership.getStatus()).isEqualTo(ArtistMembershipStatus.PAYMENT_PENDING);

		ArgumentCaptor<PaymentEventCommand.Create> paymentCaptor = ArgumentCaptor.forClass(PaymentEventCommand.Create.class);
		verify(paymentPublisher).publish(paymentCaptor.capture());
		assertThat(paymentCaptor.getValue().getOrderId()).isEqualTo(savedMembership.getOrderId());
		assertThat(paymentCaptor.getValue().getAmount()).isEqualTo(MONTHLY_AMOUNT);

		assertThat(response.getArtistId()).isEqualTo(ARTIST_ID);
		assertThat(response.getArtistName()).isEqualTo(ARTIST_NAME);
		assertThat(response.getPlanName()).isEqualTo("월간 멤버십");
		assertThat(response.getAmount()).isEqualTo(MONTHLY_AMOUNT);
		assertThat(response.getPaymentMethod()).isEqualTo("토스 결제");
		assertThat(response.getOrderId()).isEqualTo(savedMembership.getOrderId());
	}

	@Test
	@DisplayName("존재하지 않는 아티스트의 멤버십 결제 준비 요청은 예외를 발생시킨다.")
	void 존재하지않는_아티스트_멤버십_결제준비_실패() {
		MembershipRequest.CreatePayment request = createPaymentRequest(MembershipPaymentMethod.TOSS_PAY);
		when(artistRepository.findDetailById(999L)).thenReturn(java.util.Optional.empty());

		CustomException exception = assertThrows(
			CustomException.class,
			() -> membershipService.createPayment(999L, USER_ID, request)
		);

		assertEquals(ErrorCode.NOT_FOUND_ARTIST, exception.getErrorCode());
		verify(artistMembershipRepository, never()).save(any());
		verify(paymentPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("이미 가입한 아티스트의 멤버십 결제 준비 요청은 예외를 발생시킨다.")
	void 이미가입한_아티스트_멤버십_결제준비_실패() {
		ArtistRepository.ArtistDetailProjection artistDetail = org.mockito.Mockito.mock(ArtistRepository.ArtistDetailProjection.class);
		Artist artist = org.mockito.Mockito.mock(Artist.class);
		MembershipRequest.CreatePayment request = createPaymentRequest(MembershipPaymentMethod.BANK_TRANSFER);
		when(artistRepository.findDetailById(ARTIST_ID)).thenReturn(java.util.Optional.of(artistDetail));
		when(artistMembershipRepository.findByUserIdAndArtistId(USER_ID, ARTIST_ID)).thenReturn(
			java.util.Optional.of(
				ArtistMembership.builder()
					.userId(USER_ID)
					.artist(artist)
					.status(ArtistMembershipStatus.ACTIVE)
					.monthlyAmount(MONTHLY_AMOUNT)
					.paymentMethod(MembershipPaymentMethod.BANK_TRANSFER)
					.build()
			)
		);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> membershipService.createPayment(ARTIST_ID, USER_ID, request)
		);

		assertEquals(ErrorCode.ALREADY_JOINED_ARTIST_MEMBERSHIP, exception.getErrorCode());
		verify(artistMembershipRepository, never()).save(any(ArtistMembership.class));
		verify(paymentPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("이미 PAYMENT_PENDING 상태의 멤버십이 있으면 새 주문번호와 결제수단으로 갱신하고 이벤트를 다시 발행한다.")
	void 기존_PENDING_멤버십_주문_갱신() {
		ArtistRepository.ArtistDetailProjection artistDetail = mockArtistDetailProjection();
		Artist artist = org.mockito.Mockito.mock(Artist.class);
		ArtistMembership existingMembership = ArtistMembership.preparePayment(
			USER_ID,
			artist,
			"M20260331999999",
			MONTHLY_AMOUNT,
			MembershipPaymentMethod.TOSS_PAY
		);
		MembershipRequest.CreatePayment request = createPaymentRequest(MembershipPaymentMethod.BANK_TRANSFER);

		when(artistRepository.findDetailById(ARTIST_ID)).thenReturn(java.util.Optional.of(artistDetail));
		when(artistMembershipRepository.findByUserIdAndArtistId(USER_ID, ARTIST_ID)).thenReturn(java.util.Optional.of(existingMembership));
		when(artistRepository.getReferenceById(ARTIST_ID)).thenReturn(artist);
		when(artistMembershipRepository.save(any(ArtistMembership.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		MembershipResponse.CreatePayment response = membershipService.createPayment(ARTIST_ID, USER_ID, request);

		verify(artistMembershipRepository).save(existingMembership);
		ArgumentCaptor<PaymentEventCommand.Create> paymentCaptor = ArgumentCaptor.forClass(PaymentEventCommand.Create.class);
		verify(paymentPublisher).publish(paymentCaptor.capture());
		assertThat(response.getOrderId()).isNotEqualTo("M20260331999999");
		assertThat(response.getPaymentMethod()).isEqualTo("무통장 입금");
		assertThat(existingMembership.getPaymentMethod()).isEqualTo(MembershipPaymentMethod.BANK_TRANSFER);
		assertThat(existingMembership.getStatus()).isEqualTo(ArtistMembershipStatus.PAYMENT_PENDING);
		assertThat(paymentCaptor.getValue().getOrderId()).isEqualTo(response.getOrderId());
		assertThat(paymentCaptor.getValue().getAmount()).isEqualTo(MONTHLY_AMOUNT);
	}

	@Test
	@DisplayName("내 멤버십이 없으면 빈 목록과 0 summary를 반환한다.")
	void 내_멤버십_빈_응답() {
		when(artistMembershipRepository.findCurrentMemberships(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.anyCollection(),
			org.mockito.ArgumentMatchers.any(LocalDateTime.class)
		)).thenReturn(java.util.List.of());

		MembershipResponse.MyMembership response = membershipService.getMyMembership(USER_ID);

		assertThat(response.getSummary().getActiveMembershipCount()).isEqualTo(0);
		assertThat(response.getSummary().getMonthlyPaymentAmount()).isEqualTo(0);
		assertThat(response.getMemberships()).isEmpty();
	}

	@Test
	@DisplayName("내 멤버십 조회는 ACTIVE와 CANCEL_SCHEDULED를 함께 보여주고 월 결제 총액은 ACTIVE만 합산한다.")
	void 내_멤버십_조회_성공() {
		Artist activeArtist = org.mockito.Mockito.mock(Artist.class);
		Artist cancelScheduledArtist = org.mockito.Mockito.mock(Artist.class);

		ArtistMembership activeMembership = ArtistMembership.builder()
			.userId(USER_ID)
			.artist(activeArtist)
			.status(ArtistMembershipStatus.ACTIVE)
			.orderId("M20260408111111")
			.monthlyAmount(MONTHLY_AMOUNT)
			.paymentMethod(MembershipPaymentMethod.TOSS_PAY)
			.joinedAt(LocalDateTime.now().minusDays(5))
			.nextBillingAt(LocalDateTime.now().plusDays(20))
			.build();
		ArtistMembership cancelScheduledMembership = ArtistMembership.builder()
			.userId(USER_ID)
			.artist(cancelScheduledArtist)
			.status(ArtistMembershipStatus.CANCEL_SCHEDULED)
			.orderId("M20260408222222")
			.monthlyAmount(7_000L)
			.paymentMethod(MembershipPaymentMethod.BANK_TRANSFER)
			.joinedAt(LocalDateTime.now().minusDays(10))
			.nextBillingAt(LocalDateTime.now().plusDays(3))
			.build();

		when(activeArtist.getId()).thenReturn(1L);
		when(activeArtist.getName()).thenReturn("이재환");
		when(activeArtist.getProfileImg()).thenReturn("leejaehwan.png");
		when(cancelScheduledArtist.getId()).thenReturn(2L);
		when(cancelScheduledArtist.getName()).thenReturn("고은성");
		when(cancelScheduledArtist.getProfileImg()).thenReturn("goeunsung.png");
		when(s3Service.getImageUrl("leejaehwan.png")).thenReturn("https://cdn/leejaehwan.png");
		when(s3Service.getImageUrl("goeunsung.png")).thenReturn("https://cdn/goeunsung.png");
		when(artistMembershipRepository.findCurrentMemberships(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.anyCollection(),
			org.mockito.ArgumentMatchers.any(LocalDateTime.class)
		)).thenReturn(java.util.List.of(activeMembership, cancelScheduledMembership));

		MembershipResponse.MyMembership response = membershipService.getMyMembership(USER_ID);

		assertThat(response.getSummary().getActiveMembershipCount()).isEqualTo(2);
		assertThat(response.getSummary().getMonthlyPaymentAmount()).isEqualTo(5_000L);
		assertThat(response.getMemberships()).hasSize(2);
		assertThat(response.getMemberships().get(0).getMembershipId()).isEqualTo(activeMembership.getId());
		assertThat(response.getMemberships().get(0).getArtistId()).isEqualTo(1L);
		assertThat(response.getMemberships().get(0).getProfileImageUrl()).isEqualTo("https://cdn/leejaehwan.png");
		assertThat(response.getMemberships().get(0).getStatus()).isEqualTo("ACTIVE");
		assertThat(response.getMemberships().get(0).getStatusLabel()).isEqualTo("멤버십 가입중");
		assertThat(response.getMemberships().get(0).isCancelable()).isTrue();
		assertThat(response.getMemberships().get(1).getStatus()).isEqualTo("CANCEL_SCHEDULED");
		assertThat(response.getMemberships().get(1).getStatusLabel()).isEqualTo("해지 예정");
		assertThat(response.getMemberships().get(1).isCancelable()).isFalse();
	}

	private ArtistRepository.ArtistDetailProjection mockArtistDetailProjection() {
		ArtistRepository.ArtistDetailProjection artistDetail = org.mockito.Mockito.mock(ArtistRepository.ArtistDetailProjection.class);
		when(artistDetail.getArtistName()).thenReturn(ARTIST_NAME);
		return artistDetail;
	}

	private MembershipRequest.CreatePayment createPaymentRequest(MembershipPaymentMethod paymentMethod) {
		return new MembershipRequest.CreatePayment(paymentMethod, true, true, true);
	}
}
