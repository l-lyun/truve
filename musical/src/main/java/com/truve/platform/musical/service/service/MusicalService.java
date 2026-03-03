package com.truve.platform.musical.service.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.musical.service.domain.entity.Musical;
import com.truve.platform.musical.service.domain.entity.MusicalActor;
import com.truve.platform.musical.service.domain.entity.MusicalSchedule;
import com.truve.platform.musical.service.domain.entity.MusicalSeatPrice;
import com.truve.platform.musical.service.dto.MusicalResponse;
import com.truve.platform.musical.service.repository.MusicalRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MusicalService {

	private final MusicalRepository musicalRepository;

	@Transactional(readOnly = true)
	public MusicalResponse.Detail getDetail(Long musicalId) {
		Musical musical = musicalRepository.findByIdOrThrow(musicalId);

		List<MusicalResponse.Schedule> schedules = musical.getSchedules().stream()
			.sorted(Comparator.comparing(MusicalSchedule::getDateTime))
			.map(this::toScheduleResponse)
			.toList();

		List<MusicalResponse.SeatPrice> seatPrices = musical.getSeatPrices().stream()
			.sorted(Comparator.comparing(seatPrice -> seatPrice.getSeatGrade().getOrder()))
			.map(this::toSeatPriceResponse)
			.toList();

		return MusicalResponse.Detail.builder()
			.musicalId(musical.getId())
			.title(musical.getTitle())
			.posterUrl(musical.getPosterUrl())
			.stage(musical.getStage())
			.runningTime(musical.getRunningTime())
			.ageLimit(musical.getAgeLimit())
			.priceInfo(musical.getPriceInfo())
			.startDate(musical.getStartDate())
			.endDate(musical.getEndDate())
			.openAt(musical.getOpenAt())
			.ratingAverage(musical.getRatingAverage())
			.weeklyRank(musical.getWeeklyRank())
			.reviewCount(musical.getReviewCount())
			.timeInfo(musical.getTimeInfo())
			.noticeUrl(musical.getNoticeUrl())
			.detailsUrl(musical.getDetailsUrl())
			.schedules(schedules)
			.seatPrices(seatPrices)
			.build();
	}

    // 회차별 배우 목록을 정렬해 응답 순서를 고정한다.
	private MusicalResponse.Schedule toScheduleResponse(MusicalSchedule schedule) {
		List<MusicalResponse.Actor> actors = schedule.getActors().stream()
			.sorted(Comparator.comparing(actor -> actor.getRole().getOrder()))
			.map(this::toActorResponse)
			.toList();

		return MusicalResponse.Schedule.builder()
			.scheduleId(schedule.getId())
			.dateTime(schedule.getDateTime())
			.isAvailable(schedule.getIsAvailable())
			.actors(actors)
			.build();
	}

    // 엔티티를 API 응답용 배우 DTO로 변환한다.
	private MusicalResponse.Actor toActorResponse(MusicalActor actor) {
		return MusicalResponse.Actor.builder()
			.actorId(actor.getActorId())
			.role(actor.getRole().getLabel())
			.name(actor.getName())
			.isLiked(actor.getIsLiked())
			.build();
	}

    // 좌석 등급/가격을 응답 DTO로 변환한다.
	private MusicalResponse.SeatPrice toSeatPriceResponse(MusicalSeatPrice seatPrice) {
		return MusicalResponse.SeatPrice.builder()
			.seatGrade(seatPrice.getSeatGrade().getLabel())
			.price(seatPrice.getPrice())
			.build();
	}
}
