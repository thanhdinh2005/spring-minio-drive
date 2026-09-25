package com.spring.backend.service;

import com.spring.backend.dto.test.DocumentDto;
import com.spring.backend.dto.test.TestRequest;
import com.spring.backend.dto.test.TestResponse;

import java.util.UUID;

public interface TestService {
  void sayHello();
  TestResponse greeting(TestRequest request);
  DocumentDto getDocumentById(UUID documentId);
}
