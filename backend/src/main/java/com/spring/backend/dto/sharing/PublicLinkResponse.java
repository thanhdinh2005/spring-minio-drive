package com.spring.backend.dto.sharing;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicLinkResponse {

    private String publicToken;
    private String publicUrl;
}