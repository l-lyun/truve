package com.truve.platform.musical.show.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.show.dto.MembershipRequest;
import com.truve.platform.musical.show.dto.MembershipResponse;
import com.truve.platform.musical.show.service.MembershipService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/musical")
public class MembershipController {

	private final MembershipService membershipService;

	public MembershipController(MembershipService membershipService) {
		this.membershipService = membershipService;
	}

	@Operation(summary = "아티스트 멤버십 결제 준비", description = "멤버십 가입 결제를 위해 주문을 생성하고 결제를 준비합니다.")
	@PostMapping("/artists/{artistId}/membership/payment")
	public ApiResult<MembershipResponse.CreatePayment> createPayment(
		@PathVariable Long artistId,
		@RequestHeader(name = "X-User-Id") UUID userId,
		@RequestBody @Valid MembershipRequest.CreatePayment request
	) {
		return ApiResult.ok(membershipService.createPayment(artistId, userId, request));
	}

    @Operation(summary = "아티스트 멤버십 가입 완료 정보 조회", description = "결제가 완료된 멤버십의 가입 완료 정보를 조회합니다.")
    @GetMapping("/artists/{artistId}/membership/complete")
    public ApiResult<MembershipResponse.Complete> complete(
            @PathVariable Long artistId,
            @RequestHeader(name = "X-User-Id") UUID userId
    ) {
        return ApiResult.ok(membershipService.complete(artistId, userId));
    }

	@Operation(summary = "내 멤버십 조회", description = "현재 유효한 내 멤버십의 목록을 조회합니다.")
	@GetMapping("/my/membership")
	public ApiResult<MembershipResponse.MyMembership> getMyMembership(
		@RequestHeader(name = "X-User-Id") UUID userId
	) {
		return ApiResult.ok(membershipService.getMyMembership(userId));
	}
}
