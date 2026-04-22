package com.yourcompany.starter.post.dto;

import com.yourcompany.starter.post.entity.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class PostResponse {

    private final Long id;
    private final String title;
    private final String content;
    private final boolean published;
    private final String authorUsername;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static PostResponse from(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .published(post.isPublished())
                .authorUsername(post.getAuthor().getUsername())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
