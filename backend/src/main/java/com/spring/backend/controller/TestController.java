package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.test.TestRequest;
import com.spring.backend.dto.test.TestResponse;
import com.spring.backend.service.TestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class TestController {
  private final TestService testService;

  @GetMapping
  public ResponseEntity<AppResponse<Void>> sayHello() {
    testService.sayHello();
    return ResponseEntity.ok(AppResponse.success(null));
  }

  @PostMapping
  public ResponseEntity<AppResponse<TestResponse>> greeting(
    @Valid @RequestBody TestRequest request
    ) {
    return ResponseEntity.ok(AppResponse.success(testService.greeting(request)));
  }
}
