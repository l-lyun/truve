package org.truve.platform.ticketing.service.booking.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.service.BookingService;

import com.truve.platform.common.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookings")
public class BookingController {
	private static final String USER_ID_HEADER = "X-User-Id";

	private final BookingService bookingService;

	@Operation(summary = "예매 내역 생성", description = "예매 및 티켓 정보를 저장합니다.")
	@PostMapping
	public ApiResult<BookingResponse.Create> create(
		@RequestHeader(USER_ID_HEADER) UUID userId,
		@RequestBody @Valid BookingRequest.Create request) {
		return ApiResult.ok(bookingService.create(userId, request));
	}

	@Operation(summary = "예매 주문 정보 조회", description = "좌석 선택 후 생성된 예매 주문 정보를 조회합니다.")
	@GetMapping("/{reservation_number}/order")
	public ApiResult<BookingResponse.Order> getOrder(
		@PathVariable("reservation_number") String reservationNumber
	) {
		return ApiResult.ok(bookingService.getOrder(reservationNumber));
	}

	@Operation(summary = "예매 상세 정보 조회")
	@GetMapping("/{reservation_number}")
	public ApiResult<BookingResponse.ReservationDetail> getDetail(
		@PathVariable("reservation_number") String reservationNumber
	) {
		return ApiResult.ok(bookingService.getDetail(reservationNumber));
	}

	@Operation(summary = "예매 목록 조회", description = "날짜로 필터링된 예매 목록을 조회합니다.")
	@GetMapping()
	public ApiResult<List<BookingResponse.Summary>> getSummaries(
		@RequestHeader(USER_ID_HEADER) UUID userId,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to
	) {
		return ApiResult.ok(bookingService.getSummaries(userId, from, to));
	}

	@Operation(summary = "예매 결제 준비",
		description = "예매 상태를 결제 대기 중으로 변경하고, 예약자 정보를 저장한 후 결제 정보를 생성합니다.")
	@PostMapping("/{reservationNumber}/payment-ready")
	public ApiResult<Void> readyPayment(
		@PathVariable String reservationNumber,
		@RequestBody @Valid BookingRequest.ApplicantInfo request
	) {
		bookingService.paymentReady(reservationNumber, request);
		return ApiResult.ok();
	}

	@Operation(summary = "예매 취소 정보 조회",
		description = "전체 티켓 정보와 선택된 티켓의 취소 수수료를 조회합니다. "
			+ "선택된 티켓이 없을 경우, ticketIds를 null로 요청하면 전체 취소 수수료를 조회합니다.")
	@GetMapping("/{reservation_number}/cancel")
	public ApiResult<BookingResponse.Cancel> getCancel(
		@PathVariable("reservation_number") String reservationNumber,
		@RequestParam(required = false) List<Long> ticketIds
	) {
		return ApiResult.ok(bookingService.getCancel(reservationNumber, ticketIds));
	}
}
