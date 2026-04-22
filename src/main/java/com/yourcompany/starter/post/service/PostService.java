package com.yourcompany.starter.post.service;

import com.yourcompany.starter.common.exception.ResourceNotFoundException;
import com.yourcompany.starter.common.exception.UnauthorizedException;
import com.yourcompany.starter.common.response.PagedResponse;
import com.yourcompany.starter.post.dto.PostRequest;
import com.yourcompany.starter.post.dto.PostResponse;
import com.yourcompany.starter.post.entity.Post;
import com.yourcompany.starter.post.repository.PostRepository;
import com.yourcompany.starter.user.entity.Role;
import com.yourcompany.starter.user.entity.User;
import com.yourcompany.starter.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getAllPosts(Pageable pageable) {
        Page<PostResponse> page = postRepository.findAll(pageable).map(PostResponse::from);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(Long id) {
        Post post = postRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", id));
        return PostResponse.from(post);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getMyPosts(String username, Pageable pageable) {
        User user = findUserByUsername(username);
        Page<PostResponse> page = postRepository.findByAuthorId(user.getId(), pageable)
                .map(PostResponse::from);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getAllPostsAdmin(Pageable pageable) {
        Page<PostResponse> page = postRepository.findAll(pageable).map(PostResponse::from);
        return PagedResponse.from(page);
    }

    public PostResponse createPost(PostRequest request, String username) {
        User author = findUserByUsername(username);

        Post post = Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .published(request.isPublished())
                .author(author)
                .build();

        return PostResponse.from(postRepository.save(post));
    }

    public PostResponse updatePost(Long id, PostRequest request, String username) {
        Post post = postRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", id));

        if (!post.getAuthor().getUsername().equals(username) && !isAdmin(username)) {
            throw new UnauthorizedException("Not authorized to update this post");
        }

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setPublished(request.isPublished());

        return PostResponse.from(postRepository.save(post));
    }

    public void deletePost(Long id, String username) {
        Post post = postRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", id));

        if (!post.getAuthor().getUsername().equals(username) && !isAdmin(username)) {
            throw new UnauthorizedException("Not authorized to delete this post");
        }

        postRepository.delete(post);
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.getRoles().contains(Role.ROLE_ADMIN))
                .orElse(false);
    }
}
