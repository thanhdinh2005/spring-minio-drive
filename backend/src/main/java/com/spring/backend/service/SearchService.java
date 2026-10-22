package com.spring.backend.service;

import com.spring.backend.dto.search.SearchResponse;

import java.util.UUID;

public interface SearchService {

  SearchResponse search(UUID userId, String keyword, int pageNo, int pageSize);
}