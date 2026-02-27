package com.truve.platform.payment.service.domain.entity;

import java.time.LocalDateTime;

import com.truve.platform.payment.service.domain.constant.Bank;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class VirtualAccount {
	private String accountNumber;
	@Enumerated(EnumType.STRING)
	private Bank bank;
	private String customerName;
	private LocalDateTime dueDate;

	@Builder
	public VirtualAccount(String accountNumber, String bankCode, String customerName, LocalDateTime dueDate) {
		this.accountNumber = accountNumber;
		this.bank = Bank.of(bankCode);
		this.customerName = customerName;
		this.dueDate = dueDate;
	}
}
