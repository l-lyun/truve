package com.truve.platform.musical.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.user.domain.entity.UserViewedShow;

public interface UserViewedShowRepository extends JpaRepository<UserViewedShow, Long> {
}
