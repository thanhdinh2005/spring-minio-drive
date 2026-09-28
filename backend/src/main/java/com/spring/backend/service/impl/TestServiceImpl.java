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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

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
  private final S3Client s3Client;

  @Value("${storage.bucket}")
  private String bucket;

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
    HeadBucketRequest test = HeadBucketRequest.builder()
      .bucket(bucket)
      .build();

    s3Client.headBucket(test);

    System.out.println("================================");
    System.out.println("S3 connection successful!");
    System.out.println("Endpoint: " + s3Client.serviceClientConfiguration());
    System.out.println("Bucket: " + bucket);
    System.out.println("================================");

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
