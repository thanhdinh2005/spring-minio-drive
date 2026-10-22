package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.search.SearchResponse;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.SearchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

  private final SearchService searchService;

  @GetMapping
  public ResponseEntity<AppResponse<SearchResponse>> search(
    @RequestParam("q") String keyword,
    @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
    @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    SearchResponse response = searchService.search(me.getId(), keyword, pageNo, pageSize);
    return ResponseEntity.ok(AppResponse.success(response));
  }
}