package com.spring.backend.service;

import com.spring.backend.dto.test.TestRequest;
import com.spring.backend.dto.test.TestResponse;

public interface TestService {
  void sayHello();
  TestResponse greeting(TestRequest request);
}
