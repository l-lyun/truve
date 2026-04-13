package com.truve.platform.musical.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.board.domain.entity.ArtistBoardPost;

public interface ArtistBoardPostRepository extends JpaRepository<ArtistBoardPost, Long> {

	@EntityGraph(attributePaths = {"artist"})
	List<ArtistBoardPost> findByArtistIdOrderByCreatedAtDescIdDesc(Long artistId);

	@EntityGraph(attributePaths = {"artist"})
	Optional<ArtistBoardPost> findByIdAndArtistId(Long postId, Long artistId);
}
