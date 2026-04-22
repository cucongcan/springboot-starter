package com.yourcompany.starter.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    private String content;

    private boolean published = false;
}
