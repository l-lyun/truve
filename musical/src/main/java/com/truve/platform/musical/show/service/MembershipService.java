package com.truve.platform.musical.show.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.musical.show.domain.entity.ArtistMembership;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.dto.MembershipRequest;
import com.truve.platform.musical.show.dto.MembershipResponse;
import com.truve.platform.musical.show.external.kafka.PaymentEventCommand;
import com.truve.platform.musical.show.external.kafka.PaymentPublisher;
import com.truve.platform.musical.show.repository.ArtistMembershipRepository;
import com.truve.platform.musical.show.repository.ArtistRepository;
import com.truve.platform.musical.show.util.MembershipOrderIdGenerator;

@Service
public class MembershipService {
	private static final long MONTHLY_MEMBERSHIP_AMOUNT = 5_000L;
	private static final DateTimeFormatter COMPLETE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd.");

	private final ArtistRepository artistRepository;
	private final ArtistMembershipRepository artistMembershipRepository;
	private final PaymentPublisher paymentPublisher;

	public MembershipService(
		ArtistRepository artistRepository,
		ArtistMembershipRepository artistMembershipRepository,
		PaymentPublisher paymentPublisher
	) {
		this.artistRepository = artistRepository;
		this.artistMembershipRepository = artistMembershipRepository;
		this.paymentPublisher = paymentPublisher;
	}

	@Transactional
	public MembershipResponse.CreatePayment createPayment(
		Long artistId,
		UUID userId,
		MembershipRequest.CreatePayment request
	) {
		ArtistRepository.ArtistDetailProjection artistDetail = artistRepository.findDetailById(artistId)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_ARTIST));

		ArtistMembership membership = artistMembershipRepository.findByUserIdAndArtistId(userId, artistId)
			.orElse(null);
		Preconditions.validate(membership == null || !membership.hasActiveEntitlement(), ErrorCode.ALREADY_JOINED_ARTIST_MEMBERSHIP);

		Artist artist = artistRepository.getReferenceById(artistId);
		String orderId = MembershipOrderIdGenerator.generate();
		if (membership == null) {
			membership = ArtistMembership.preparePayment(userId, artist, orderId, MONTHLY_MEMBERSHIP_AMOUNT, request.getPaymentMethod());
		} else {
			membership.preparePayment(orderId, MONTHLY_MEMBERSHIP_AMOUNT, request.getPaymentMethod());
		}
		ArtistMembership savedMembership;
		try {
			savedMembership = artistMembershipRepository.save(membership);
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(ErrorCode.ALREADY_JOINED_ARTIST_MEMBERSHIP);
		}

		paymentPublisher.publish(PaymentEventCommand.Create.of(savedMembership));

		return MembershipResponse.CreatePayment.of(
			artistId,
			artistDetail.getArtistName(),
			savedMembership.getMonthlyAmount(),
			savedMembership.getOrderId(),
			savedMembership.getPaymentMethod().getDisplayName()
		);
	}

	@Transactional(readOnly = true)
	public MembershipResponse.Complete complete(Long artistId, UUID userId) {
		ArtistRepository.ArtistDetailProjection artistDetail = artistRepository.findDetailById(artistId)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_ARTIST));

		ArtistMembership membership = artistMembershipRepository.findByUserIdAndArtistId(userId, artistId)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_ARTIST_MEMBERSHIP));

		Preconditions.validate(membership.hasActiveEntitlement(), ErrorCode.MEMBERSHIP_PAYMENT_NOT_COMPLETED);

		return MembershipResponse.Complete.of(
			artistId,
			artistDetail.getArtistName(),
			membership.getMonthlyAmount(),
			formatCompleteDate(membership.getJoinedAt()),
			formatCompleteDate(membership.getNextBillingAt())
		);
	}

	@Transactional
	public void confirm(String orderId) {
		activate(orderId);
	}

	@Transactional
	public void depositReceive(String orderId) {
		activate(orderId);
	}

	private void activate(String orderId) {
		artistMembershipRepository.findByOrderId(orderId)
			.ifPresent(ArtistMembership::confirm);
	}

	private String formatCompleteDate(LocalDateTime value) {
		return value == null ? null : value.format(COMPLETE_DATE_FORMATTER);
	}
}