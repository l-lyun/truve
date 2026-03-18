package com.truve.platform.musical.show.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "show_casting")
public class ShowCasting extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "show_id", nullable = false)
	private Show show;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private Artist artist;

	@Column(name = "role_name")
	private String roleName;

	@Column(name = "`order`")
	private Integer castingOrder;

	@Column(name = "profile_img", length = 500)
	private String profileImg;

	@Builder
	private ShowCasting(Show show, Artist artist, String roleName, Integer castingOrder, String profileImg) {
		this.show = show;
		this.artist = artist;
		this.roleName = roleName;
		this.castingOrder = castingOrder;
		this.profileImg = profileImg;
	}
}
