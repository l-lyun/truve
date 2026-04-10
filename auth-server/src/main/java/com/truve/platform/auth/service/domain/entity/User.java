package com.truve.platform.auth.service.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.truve.platform.common.constants.AuthProvider;
import com.truve.platform.common.constants.UserRole;
import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

	@Column(nullable = false, unique = true, updatable = false)
	private UUID publicId;

	@Email
	@Column(nullable = false, unique = true)
	private String email;

	@Column(unique = true)
	private String nickname;

	// TODO: 기획 논의 이후 비밀번호 정책 정규식 설정
	private String password;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private AuthProvider provider;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private UserRole role;

	private String oAuthUserId;

	private String oAuthAccessToken;

	private String oAuthRefreshToken;

	@Column(nullable = false)
	private boolean serviceTermsAgreed;

	@Column(nullable = false)
	private boolean electronicFinanceTermsAgreed;

	@Column(nullable = false)
	private boolean privacyCollectionAgreed;

	@Column(nullable = false)
	private boolean marketingInfoAgreed;

	@Column(nullable = false)
	private boolean emailNotificationAgreed;

	private LocalDateTime withdrawnAt;

	@Column(nullable = false)
	private boolean over14Agreed;


	@Builder
	private User(
		UUID publicId,
		String email,
		String nickname,
		String password,
		AuthProvider provider,
		UserRole role,
		String oAuthUserId,
		String oAuthAccessToken,
		String oAuthRefreshToken,
		boolean serviceTermsAgreed,
		boolean electronicFinanceTermsAgreed,
		boolean privacyCollectionAgreed,
		boolean marketingInfoAgreed,
		boolean emailNotificationAgreed,
		boolean over14Agreed
	) {
		this.publicId = publicId;
		this.email = email;
		this.nickname = nickname;
		this.password = password;
		this.provider = provider;
		this.role = role;
		this.oAuthUserId = oAuthUserId;
		this.oAuthAccessToken = oAuthAccessToken;
		this.oAuthRefreshToken = oAuthRefreshToken;
		this.serviceTermsAgreed = serviceTermsAgreed;
		this.electronicFinanceTermsAgreed = electronicFinanceTermsAgreed;
		this.privacyCollectionAgreed = privacyCollectionAgreed;
		this.marketingInfoAgreed = marketingInfoAgreed;
		this.emailNotificationAgreed = emailNotificationAgreed;
		this.over14Agreed = over14Agreed;
	}

	public static User createLocalUser(
		String email,
		String nickname,
		String password,
		boolean serviceTermsAgreed,
		boolean electronicFinanceTermsAgreed,
		boolean privacyCollectionAgreed,
		boolean marketingInfoAgreed,
		boolean emailNotificationAgreed,
		boolean over14Agreed
	) {
		return User.builder()
			.publicId(UUID.randomUUID())
			.email(email)
			.nickname(nickname)
			.password(password)
			.provider(AuthProvider.LOCAL)
			.role(UserRole.MEMBER)
			.serviceTermsAgreed(serviceTermsAgreed)
			.electronicFinanceTermsAgreed(electronicFinanceTermsAgreed)
			.privacyCollectionAgreed(privacyCollectionAgreed)
			.marketingInfoAgreed(marketingInfoAgreed)
			.emailNotificationAgreed(emailNotificationAgreed)
			.over14Agreed(over14Agreed)
			.build();
	}

	public static User createOAuthUser(
		String email,
		AuthProvider provider,
		String oAuthUserId,
		String oAuthAccessToken,
		String oAuthRefreshToken
	) {
		return User.builder()
			.publicId(UUID.randomUUID())
			.email(email)
			.nickname(null)
			.provider(provider)
			.role(UserRole.MEMBER)
			.oAuthUserId(oAuthUserId)
			.oAuthAccessToken(oAuthAccessToken)
			.oAuthRefreshToken(oAuthRefreshToken)
			.serviceTermsAgreed(false)
			.electronicFinanceTermsAgreed(false)
			.privacyCollectionAgreed(false)
			.marketingInfoAgreed(false)
			.emailNotificationAgreed(false)
			.over14Agreed(false)
			.build();
	}

	public static User createOAuthUser(
		String email,
		String nickname,
		AuthProvider provider,
		String oAuthUserId,
		String oAuthAccessToken,
		String oAuthRefreshToken,
		boolean serviceTermsAgreed,
		boolean electronicFinanceTermsAgreed,
		boolean privacyCollectionAgreed,
		boolean marketingInfoAgreed,
		boolean emailNotificationAgreed,
		boolean over14Agreed
	) {
		return User.builder()
			.publicId(UUID.randomUUID())
			.email(email)
			.nickname(nickname)
			.provider(provider)
			.role(UserRole.MEMBER)
			.oAuthUserId(oAuthUserId)
			.oAuthAccessToken(oAuthAccessToken)
			.oAuthRefreshToken(oAuthRefreshToken)
			.serviceTermsAgreed(serviceTermsAgreed)
			.electronicFinanceTermsAgreed(electronicFinanceTermsAgreed)
			.privacyCollectionAgreed(privacyCollectionAgreed)
			.marketingInfoAgreed(marketingInfoAgreed)
			.emailNotificationAgreed(emailNotificationAgreed)
			.over14Agreed(over14Agreed)
			.build();
	}

	public void updateNickname(String nickname) {
		this.nickname = nickname;
	}

	public void updateMarketingInfoAgreed(boolean marketingInfoAgreed) {
		this.marketingInfoAgreed = marketingInfoAgreed;
	}

	public void updateEmailNotificationAgreed(boolean emailNotificationAgreed) {
		this.emailNotificationAgreed = emailNotificationAgreed;
	}

	public void withdraw() {
		this.withdrawnAt = LocalDateTime.now();
	}

	public boolean isWithdrawn() {
		return withdrawnAt != null;
	}

	public void reactivate(
		String nickname,
		String password,
		boolean serviceTermsAgreed,
		boolean electronicFinanceTermsAgreed,
		boolean privacyCollectionAgreed,
		boolean marketingInfoAgreed,
		boolean emailNotificationAgreed,
		boolean over14Agreed
	) {
		this.nickname = nickname;
		this.password = password;
		this.serviceTermsAgreed = serviceTermsAgreed;
		this.electronicFinanceTermsAgreed = electronicFinanceTermsAgreed;
		this.privacyCollectionAgreed = privacyCollectionAgreed;
		this.marketingInfoAgreed = marketingInfoAgreed;
		this.emailNotificationAgreed = emailNotificationAgreed;
		this.over14Agreed = over14Agreed;
		this.withdrawnAt = null;
	}
}
