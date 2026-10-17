package com.spring.backend.dto.document;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PresignedUrlDto {
    private String url;
    private int expiresIn;
}
