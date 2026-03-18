package com.truve.platform.musical.seat.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "venue")
@AttributeOverride(name = "id", column = @Column(name = "venue_id"))
public class Venue extends BaseEntity {

	@Column(nullable = false, length = 255)
	private String name;

	@Column(length = 255)
	private String address;

	@Builder
	public Venue(String name, String address) {
		this.name = name;
		this.address = address;
	}
}
