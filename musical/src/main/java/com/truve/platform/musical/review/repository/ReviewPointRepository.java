package com.truve.platform.musical.review.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.review.dao.ReviewPointCountDao;
import com.truve.platform.musical.review.domain.entity.ReviewPoint;


public interface ReviewPointRepository extends JpaRepository<ReviewPoint, Long> {

	@Query(
		"""
	select new com.truve.platform.musical.review.dao.ReviewPointCountDao(
		rp.reviewPointType.point,
		count(rp)
		)
	from ReviewPoint rp
	join rp.review r
	where r.showId = :showId
		and r.deletedAt is null
	group by rp.reviewPointType.point
	"""
	)
	List<ReviewPointCountDao> countPointsByShowId(@Param("showId") Long showId);
}
