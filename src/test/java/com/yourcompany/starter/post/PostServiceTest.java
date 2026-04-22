package com.yourcompany.starter.post;

import com.yourcompany.starter.common.exception.ResourceNotFoundException;
import com.yourcompany.starter.common.exception.UnauthorizedException;
import com.yourcompany.starter.common.response.PagedResponse;
import com.yourcompany.starter.post.dto.PostRequest;
import com.yourcompany.starter.post.dto.PostResponse;
import com.yourcompany.starter.post.entity.Post;
import com.yourcompany.starter.post.repository.PostRepository;
import com.yourcompany.starter.post.service.PostService;
import com.yourcompany.starter.user.entity.Role;
import com.yourcompany.starter.user.entity.User;
import com.yourcompany.starter.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostService postService;

    private User owner;
    private User admin;
    private Post post;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).username("owner")
                .roles(Set.of(Role.ROLE_USER)).build();
        admin = User.builder().id(2L).username("admin")
                .roles(Set.of(Role.ROLE_ADMIN)).build();
        post = Post.builder().id(1L).title("Title").content("Content")
                .published(false).author(owner).build();
    }

    @Test
    void getAllPosts_returnsPaged() {
        Pageable pageable = PageRequest.of(0, 10);
        when(postRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));

        PagedResponse<PostResponse> result = postService.getAllPosts(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Title");
    }

    @Test
    void createPost_withValidRequest_savesAndReturns() {
        PostRequest request = new PostRequest();
        request.setTitle("New Post");
        request.setContent("Content here");
        request.setPublished(true);

        Post saved = Post.builder().id(2L).title("New Post").content("Content here")
                .published(true).author(owner).build();

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(postRepository.save(any(Post.class))).thenReturn(saved);

        PostResponse response = postService.createPost(request, "owner");

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getTitle()).isEqualTo("New Post");
        assertThat(response.isPublished()).isTrue();
        assertThat(response.getAuthorUsername()).isEqualTo("owner");
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void updatePost_asOwner_succeeds() {
        PostRequest request = new PostRequest();
        request.setTitle("Updated");
        request.setContent("Updated content");

        when(postRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        PostResponse response = postService.updatePost(1L, request, "owner");

        assertThat(response).isNotNull();
        verify(postRepository).save(post);
    }

    @Test
    void updatePost_asAdmin_succeeds() {
        PostRequest request = new PostRequest();
        request.setTitle("Admin Edit");
        request.setContent("Content");

        when(postRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        PostResponse response = postService.updatePost(1L, request, "admin");

        assertThat(response).isNotNull();
    }

    @Test
    void updatePost_asOtherUser_throwsUnauthorized() {
        PostRequest request = new PostRequest();
        request.setTitle("Hack");
        request.setContent("Content");

        User other = User.builder().id(3L).username("other")
                .roles(Set.of(Role.ROLE_USER)).build();

        when(postRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> postService.updatePost(1L, request, "other"))
                .isInstanceOf(UnauthorizedException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void getPostById_notFound_throwsResourceNotFound() {
        when(postRepository.findByIdWithAuthor(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found with id: 99");
    }

    @Test
    void deletePost_asOwner_deletesPost() {
        when(postRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(post));

        postService.deletePost(1L, "owner");

        verify(postRepository).delete(post);
    }

    @Test
    void deletePost_asOtherUser_throwsUnauthorized() {
        User other = User.builder().id(3L).username("other")
                .roles(Set.of(Role.ROLE_USER)).build();

        when(postRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> postService.deletePost(1L, "other"))
                .isInstanceOf(UnauthorizedException.class);
        verify(postRepository, never()).delete(any());
    }
}
