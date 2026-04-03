package com.truve.platform.musical.show.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	@Mock
	private ArtistRepository artistRepository;
	@Mock
	private ArtistMembershipRepository artistMembershipRepository;
	@Mock
	private PaymentPublisher paymentPublisher;

	@InjectMocks
	private MembershipService membershipService;

	@Test
	@DisplayName("멤버십 결제 준비에 성공하면 unified membership을 저장하고 결제 생성 이벤트를 발행한다.")
	void 멤버십_결제준비_성공() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		Artist artist = org.mockito.Mockito.mock(Artist.class);
		ArtistRepository.ArtistDetailProjection artistDetail = org.mockito.Mockito.mock(ArtistRepository.ArtistDetailProjection.class);
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.TOSS_PAY,
			true,
			true,
			true
		);

		when(artistRepository.findDetailById(101L)).thenReturn(java.util.Optional.of(artistDetail));
		when(artistDetail.getArtistName()).thenReturn("고은성");
		when(artistMembershipRepository.findByUserIdAndArtistId(userId, 101L)).thenReturn(java.util.Optional.empty());
		when(artistRepository.getReferenceById(101L)).thenReturn(artist);
		when(artistMembershipRepository.save(any(ArtistMembership.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		MembershipResponse.CreatePayment response = membershipService.createPayment(101L, userId, request);

		ArgumentCaptor<ArtistMembership> membershipCaptor = ArgumentCaptor.forClass(ArtistMembership.class);
		verify(artistMembershipRepository).save(membershipCaptor.capture());

		ArtistMembership savedMembership = membershipCaptor.getValue();
		assertThat(savedMembership.getOrderId()).startsWith("M");
		assertThat(savedMembership.getMonthlyAmount()).isEqualTo(5_000L);
		assertThat(savedMembership.getPaymentMethod()).isEqualTo(MembershipPaymentMethod.TOSS_PAY);
		assertThat(savedMembership.getStatus()).isEqualTo(ArtistMembershipStatus.PAYMENT_PENDING);

		ArgumentCaptor<PaymentEventCommand.Create> paymentCaptor = ArgumentCaptor.forClass(PaymentEventCommand.Create.class);
		verify(paymentPublisher).publish(paymentCaptor.capture());
		assertThat(paymentCaptor.getValue().getOrderId()).isEqualTo(savedMembership.getOrderId());
		assertThat(paymentCaptor.getValue().getAmount()).isEqualTo(5_000L);

		assertThat(response.getArtistId()).isEqualTo(101L);
		assertThat(response.getArtistName()).isEqualTo("고은성");
		assertThat(response.getPlanName()).isEqualTo("월간 멤버십");
		assertThat(response.getAmount()).isEqualTo(5_000L);
		assertThat(response.getPaymentMethod()).isEqualTo("토스 결제");
		assertThat(response.getOrderId()).isEqualTo(savedMembership.getOrderId());
	}

	@Test
	@DisplayName("존재하지 않는 아티스트의 멤버십 결제 준비 요청은 예외를 발생시킨다.")
	void 존재하지않는_아티스트_멤버십_결제준비_실패() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.TOSS_PAY,
			true,
			true,
			true
		);
		when(artistRepository.findDetailById(999L)).thenReturn(java.util.Optional.empty());

		CustomException exception = assertThrows(
			CustomException.class,
			() -> membershipService.createPayment(999L, userId, request)
		);

		assertEquals(ErrorCode.NOT_FOUND_ARTIST, exception.getErrorCode());
		verify(artistMembershipRepository, never()).save(any());
		verify(paymentPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("이미 가입한 아티스트의 멤버십 결제 준비 요청은 예외를 발생시킨다.")
	void 이미가입한_아티스트_멤버십_결제준비_실패() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		ArtistRepository.ArtistDetailProjection artistDetail = org.mockito.Mockito.mock(ArtistRepository.ArtistDetailProjection.class);
		Artist artist = org.mockito.Mockito.mock(Artist.class);
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.BANK_TRANSFER,
			true,
			true,
			true
		);
		when(artistRepository.findDetailById(101L)).thenReturn(java.util.Optional.of(artistDetail));
		when(artistMembershipRepository.findByUserIdAndArtistId(userId, 101L)).thenReturn(
			java.util.Optional.of(
				ArtistMembership.builder()
					.userId(userId)
					.artist(artist)
					.status(ArtistMembershipStatus.ACTIVE)
					.monthlyAmount(5_000L)
					.paymentMethod(MembershipPaymentMethod.BANK_TRANSFER)
					.build()
			)
		);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> membershipService.createPayment(101L, userId, request)
		);

		assertEquals(ErrorCode.ALREADY_JOINED_ARTIST_MEMBERSHIP, exception.getErrorCode());
		verify(artistMembershipRepository, never()).save(any(ArtistMembership.class));
		verify(paymentPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("이미 PAYMENT_PENDING 상태의 멤버십이 있으면 새 주문번호와 결제수단으로 갱신하고 이벤트를 다시 발행한다.")
	void 기존_PENDING_멤버십_주문_갱신() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		ArtistRepository.ArtistDetailProjection artistDetail = org.mockito.Mockito.mock(ArtistRepository.ArtistDetailProjection.class);
		Artist artist = org.mockito.Mockito.mock(Artist.class);
		ArtistMembership existingMembership = ArtistMembership.preparePayment(
			userId,
			artist,
			"M20260331999999",
			5_000L,
			MembershipPaymentMethod.TOSS_PAY
		);
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.BANK_TRANSFER,
			true,
			true,
			true
		);

		when(artistRepository.findDetailById(101L)).thenReturn(java.util.Optional.of(artistDetail));
		when(artistDetail.getArtistName()).thenReturn("고은성");
		when(artistMembershipRepository.findByUserIdAndArtistId(userId, 101L)).thenReturn(java.util.Optional.of(existingMembership));
		when(artistRepository.getReferenceById(101L)).thenReturn(artist);
		when(artistMembershipRepository.save(any(ArtistMembership.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		MembershipResponse.CreatePayment response = membershipService.createPayment(101L, userId, request);

		verify(artistMembershipRepository).save(existingMembership);
		ArgumentCaptor<PaymentEventCommand.Create> paymentCaptor = ArgumentCaptor.forClass(PaymentEventCommand.Create.class);
		verify(paymentPublisher).publish(paymentCaptor.capture());
		assertThat(response.getOrderId()).isNotEqualTo("M20260331999999");
		assertThat(response.getPaymentMethod()).isEqualTo("무통장 입금");
		assertThat(existingMembership.getPaymentMethod()).isEqualTo(MembershipPaymentMethod.BANK_TRANSFER);
		assertThat(existingMembership.getStatus()).isEqualTo(ArtistMembershipStatus.PAYMENT_PENDING);
		assertThat(paymentCaptor.getValue().getOrderId()).isEqualTo(response.getOrderId());
		assertThat(paymentCaptor.getValue().getAmount()).isEqualTo(5_000L);
	}
}
