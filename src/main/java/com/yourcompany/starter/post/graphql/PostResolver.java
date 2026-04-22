package com.yourcompany.starter.post.graphql;

import com.yourcompany.starter.common.response.PagedResponse;
import com.yourcompany.starter.post.dto.PostRequest;
import com.yourcompany.starter.post.dto.PostResponse;
import com.yourcompany.starter.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PostResolver {

    private final PostService postService;

    @QueryMapping
    public PagedResponse<PostResponse> posts(
            @Argument int page,
            @Argument int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return postService.getAllPosts(pageable);
    }

    @QueryMapping
    public PostResponse post(@Argument Long id) {
        return postService.getPostById(id);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public PagedResponse<PostResponse> myPosts(
            @Argument int page,
            @Argument int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return postService.getMyPosts(userDetails.getUsername(), pageable);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PostResponse createPost(
            @Argument String title,
            @Argument String content,
            @Argument(name = "published") Boolean published,
            @AuthenticationPrincipal UserDetails userDetails) {
        PostRequest request = new PostRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setPublished(published != null && published);
        return postService.createPost(request, userDetails.getUsername());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PostResponse updatePost(
            @Argument Long id,
            @Argument String title,
            @Argument String content,
            @Argument(name = "published") Boolean published,
            @AuthenticationPrincipal UserDetails userDetails) {
        PostRequest request = new PostRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setPublished(published != null && published);
        return postService.updatePost(id, request, userDetails.getUsername());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean deletePost(
            @Argument Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        postService.deletePost(id, userDetails.getUsername());
        return true;
    }
}
