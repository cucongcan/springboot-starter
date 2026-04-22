package com.yourcompany.starter.post.controller;

import com.yourcompany.starter.common.response.ApiResponse;
import com.yourcompany.starter.common.response.PagedResponse;
import com.yourcompany.starter.post.dto.PostRequest;
import com.yourcompany.starter.post.dto.PostResponse;
import com.yourcompany.starter.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PostResponse>>> getAllPosts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(null, postService.getAllPosts(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(null, postService.getPostById(id)));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagedResponse<PostResponse>>> getMyPosts(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(null,
                postService.getMyPosts(userDetails.getUsername(), pageable)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody PostRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        PostResponse response = postService.createPost(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        PostResponse response = postService.updatePost(id, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Post updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        postService.deletePost(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Post deleted successfully", null));
    }
}
