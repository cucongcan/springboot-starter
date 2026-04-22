package com.yourcompany.starter.post.controller;

import com.yourcompany.starter.common.response.ApiResponse;
import com.yourcompany.starter.common.response.PagedResponse;
import com.yourcompany.starter.post.dto.PostResponse;
import com.yourcompany.starter.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/posts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PostResponse>>> getAllPosts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(null, postService.getAllPostsAdmin(pageable)));
    }
}
