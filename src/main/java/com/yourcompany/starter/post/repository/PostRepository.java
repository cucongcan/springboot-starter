package com.yourcompany.starter.post.repository;

import com.yourcompany.starter.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByAuthorId(Long authorId, Pageable pageable);

    Page<Post> findByPublishedTrue(Pageable pageable);

    Page<Post> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
    Optional<Post> findByIdWithAuthor(@Param("id") Long id);
}
