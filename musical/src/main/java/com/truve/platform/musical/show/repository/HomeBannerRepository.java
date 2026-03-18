package com.truve.platform.musical.show.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.truve.platform.musical.show.domain.entity.HomeBanner;

public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {

	@Query("""
		select b
		from HomeBanner b
		where b.isActive = true
		order by b.displayOrder asc, b.id asc
		""")
	List<HomeBanner> findActiveBanners(Pageable pageable);
}
