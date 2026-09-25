package com.spring.backend.service.impl;

import com.spring.backend.dto.test.DocumentDto;
import com.spring.backend.dto.test.TestRequest;
import com.spring.backend.dto.test.TestResponse;
import com.spring.backend.entity.Document;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.service.TestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TestServiceImpl implements TestService {

  // private final TestRepository testRepository;
  private final DocumentRepository documentRepository;

  @Override
  public void sayHello() {
    // Validate request
    // Query Db, implement flow code,...

    Random random = new Random();

    // 50% throw exception
    if (random.nextBoolean()) {
      throw new AppException(
        ErrorCode.INTERNAL_SERVER_ERROR,
        Map.of("detail", "Something went wrong. Please contact admin for more information")
      );
    }
  }

  @Override
  public TestResponse greeting(TestRequest request) {
    return TestResponse.builder()
      .greeting("Hello user: " + request.getEmail())
      .build();
  }

  @Override
  public DocumentDto getDocumentById(UUID documentId) {
    Document document = documentRepository.findById(documentId)
      .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
    return DocumentDto.builder()
      .id(document.getId())
      .name(document.getName())
      .build();
  }
}
