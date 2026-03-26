package com.truve.platform.musical.show.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.truve.platform.musical.show.domain.entity.PromotionBanner;

public interface PromotionBannerRepository extends JpaRepository<PromotionBanner, Long> {

	@Query("""
		select b
		from PromotionBanner b
		where b.isActive = true
		order by b.displayOrder asc, b.id asc
		""")
	List<PromotionBanner> findActiveBanners(Pageable pageable);
}