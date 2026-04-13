package com.truve.platform.musical.show.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.musical.show.domain.constant.ArtistMembershipStatus;
import com.truve.platform.musical.show.domain.constant.MembershipPaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
name = "artist_memberships",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_artist_memberships_user_artist",
			columnNames = {"user_id", "artist_id"}
		)
	}
)
public class ArtistMembership extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private Artist artist;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ArtistMembershipStatus status;

	@Column(name = "order_id", unique = true)
	private String orderId;

	@Column(name = "monthly_amount", nullable = false)
	private Long monthlyAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false)
	private MembershipPaymentMethod paymentMethod;

	@Column(name = "joined_at")
	private LocalDateTime joinedAt;

	@Column(name = "next_billing_at")
	private LocalDateTime nextBillingAt;

	@Builder
	private ArtistMembership(
		UUID userId,
		Artist artist,
		ArtistMembershipStatus status,
		String orderId,
		Long monthlyAmount,
		MembershipPaymentMethod paymentMethod,
		LocalDateTime joinedAt,
		LocalDateTime nextBillingAt
	) {
		this.userId = userId;
		this.artist = artist;
		this.status = status;
		this.orderId = orderId;
		this.monthlyAmount = monthlyAmount;
		this.paymentMethod = paymentMethod;
		this.joinedAt = joinedAt;
		this.nextBillingAt = nextBillingAt;
	}

	public static ArtistMembership preparePayment(
		UUID userId,
		Artist artist,
		String orderId,
		Long monthlyAmount,
		MembershipPaymentMethod paymentMethod
	) {
		return ArtistMembership.builder()
			.userId(userId)
			.artist(artist)
			.status(ArtistMembershipStatus.PAYMENT_PENDING)
			.orderId(orderId)
			.monthlyAmount(monthlyAmount)
			.paymentMethod(paymentMethod)
			.joinedAt(null)
			.nextBillingAt(null)
			.build();
	}

	public boolean hasActiveEntitlement() {
		return status == ArtistMembershipStatus.ACTIVE || status == ArtistMembershipStatus.CANCEL_SCHEDULED;
	}

	public boolean isPaymentPending() {
		return status == ArtistMembershipStatus.PAYMENT_PENDING;
	}

	public void preparePayment(String orderId, Long monthlyAmount, MembershipPaymentMethod paymentMethod) {
		this.status = ArtistMembershipStatus.PAYMENT_PENDING;
		this.orderId = orderId;
		this.monthlyAmount = monthlyAmount;
		this.paymentMethod = paymentMethod;
		this.joinedAt = null;
		this.nextBillingAt = null;
	}

	public void confirm() {
		if (this.status == ArtistMembershipStatus.PAYMENT_PENDING) {
			this.status = ArtistMembershipStatus.ACTIVE;
			this.joinedAt = LocalDateTime.now();
			this.nextBillingAt = this.joinedAt.plusMonths(1);
		}
	}
}