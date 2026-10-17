package com.spring.backend.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import com.spring.backend.AbstractIntegrationTest;
import com.spring.backend.entity.User;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.StorageService;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/**
 * Integration test 2.3/2.4/2.5/2.6/2.7 cho document:
 * HTTP thật + JWT thật + Postgres thật + RustFS thật.
 */
public class DocumentIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private UserRepository userRepo;

  @Autowired
  private S3Client s3;

  /** Spy bean thật: chỉ stub đúng method delete để giả lập RustFS lỗi. */
  @MockitoSpyBean
  private StorageService storageService;

  private final HttpClient http = HttpClient.newHttpClient();

  // ===== 2.3 POST /api/folders/{id}/documents =====

  @Test
  @DisplayName("2.3 upload trả 201, object nằm thật trên RustFS, quota tăng")
  void upload_ok_andStoredOnRustFS() throws Exception {
    String email = createUser();
    String token = login(email);
    UUID folderId = createFolder(token, "Upload", null);
    byte[] bytes = "noi-dung-bao-cao".getBytes();

    MvcResult result = mockMvc.perform(multipart("/folders/{id}/documents", folderId)
        .file(new MockMultipartFile("file", "bao_cao.pdf", "application/pdf", bytes))
        .header("Authorization", bearer(token)))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.name").value("bao_cao.pdf"))
      .andExpect(jsonPath("$.data.size").value(bytes.length))
      .andExpect(jsonPath("$.data.objectKey").exists())
      .andReturn();

    String objectKey = objectMapper.readTree(result.getResponse().getContentAsString())
      .get("data").get("objectKey").asText();

    HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
      .bucket(TEST_BUCKET).key(objectKey).build());
    assertThat(head.contentLength()).isEqualTo(bytes.length);

    User owner = userRepo.findByEmail(email).orElseThrow();
    assertThat(owner.getStorageUsed()).isEqualTo(bytes.length);
  }

  @Test
  @DisplayName("2.3 vượt quota trả 413 DOCUMENT_TOO_LARGE")
  void upload_quotaExceeded_returns413() throws Exception {
    String email = createUser();
    User user = userRepo.findByEmail(email).orElseThrow();
    user.setStorageLimit(10L);
    userRepo.save(user);
    String token = login(email);
    UUID folderId = createFolder(token, "Chật", null);

    mockMvc.perform(multipart("/folders/{id}/documents", folderId)
        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[100]))
        .header("Authorization", bearer(token)))
      .andExpect(status().isPayloadTooLarge())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value("ERR_DOC_002"));
  }

  @Test
  @DisplayName("2.3 upload vào folder người khác trả 403")
  void upload_toStrangerFolder_returns403() throws Exception {
    String ownerToken = newUserToken();
    UUID folderId = createFolder(ownerToken, "Riêng tư", null);

    mockMvc.perform(multipart("/folders/{id}/documents", folderId)
        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", "x".getBytes()))
        .header("Authorization", bearer(newUserToken())))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
  }

  // ===== 2.4 GET /api/documents/{id}/download-url =====

  @Test
  @DisplayName("2.4 presigned URL tải được nội dung thật từ RustFS, expiresIn 300")
  void downloadUrl_downloadsRealContent() throws Exception {
    String token = newUserToken();
    UUID folderId = createFolder(token, "Tai", null);
    byte[] bytes = "du-lieu-that".getBytes();
    UUID docId = upload(token, folderId, "that.pdf", "application/pdf", bytes);

    MvcResult result = mockMvc.perform(get("/documents/{id}/download-url", docId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.expiresIn").value(300))
      .andExpect(jsonPath("$.data.url").exists())
      .andReturn();

    String url = objectMapper.readTree(result.getResponse().getContentAsString())
      .get("data").get("url").asText();

    HttpResponse<byte[]> resp = http.send(
      HttpRequest.newBuilder(URI.create(url)).GET().build(),
      HttpResponse.BodyHandlers.ofByteArray());
    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(resp.body()).isEqualTo(bytes);
  }

  @Test
  @DisplayName("2.4 user khác lấy download-url trả 403, id lạ trả 404")
  void downloadUrl_stranger403_notFound404() throws Exception {
    String token = newUserToken();
    UUID folderId = createFolder(token, "Tai", null);
    UUID docId = upload(token, folderId, "a.pdf", "application/pdf", "x".getBytes());

    mockMvc.perform(get("/documents/{id}/download-url", docId)
        .header("Authorization", bearer(newUserToken())))
      .andExpect(status().isForbidden());

    mockMvc.perform(get("/documents/{id}/download-url", UUID.randomUUID())
        .header("Authorization", bearer(token)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
  }

  // ===== 2.5 GET /api/documents/{id}/preview-url =====

  @Test
  @DisplayName("2.5 preview pdf ok, mime không hỗ trợ trả 400")
  void previewUrl_pdfOk_unsupported400() throws Exception {
    String token = newUserToken();
    UUID folderId = createFolder(token, "Xem", null);
    UUID pdfId = upload(token, folderId, "a.pdf", "application/pdf", "pdf".getBytes());
    UUID zipId = upload(token, folderId, "a.zip", "application/zip", "zip".getBytes());

    mockMvc.perform(get("/documents/{id}/preview-url", pdfId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.expiresIn").value(300));

    mockMvc.perform(get("/documents/{id}/preview-url", zipId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("ERR_REQ_001"));
  }

  // ===== 2.6 PATCH /api/documents/{id} =====

  @Test
  @DisplayName("2.6 đổi tên + di chuyển document")
  void patch_renameAndMove_ok() throws Exception {
    String token = newUserToken();
    UUID srcId = createFolder(token, "Nguon", null);
    UUID destId = createFolder(token, "Dich", null);
    UUID docId = upload(token, srcId, "cu.pdf", "application/pdf", "x".getBytes());

    mockMvc.perform(patch("/documents/{id}", docId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Tên mới.pdf\",\"folderId\":\"" + destId + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("Tên mới.pdf"))
      .andExpect(jsonPath("$.data.folderId").value(destId.toString()));
  }

  @Test
  @DisplayName("2.6 di chuyển vào folder người khác trả 403")
  void patch_moveToStrangerFolder_returns403() throws Exception {
    String token = newUserToken();
    UUID srcId = createFolder(token, "Nguon", null);
    UUID docId = upload(token, srcId, "a.pdf", "application/pdf", "x".getBytes());
    UUID strangerFolder = createFolder(newUserToken(), "Đích lạ", null);

    mockMvc.perform(patch("/documents/{id}", docId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"folderId\":\"" + strangerFolder + "\"}"))
      .andExpect(status().isForbidden());
  }

  // ===== 2.7 DELETE /api/documents/{id} =====

  @Test
  @DisplayName("2.7 owner xóa: DB mất, object RustFS mất, quota giảm")
  void delete_owner_ok() throws Exception {
    String email = createUser();
    String token = login(email);
    UUID folderId = createFolder(token, "Xoa", null);
    byte[] bytes = "xoa-toi-di".getBytes();
    UUID docId = upload(token, folderId, "x.pdf", "application/pdf", bytes);
    String objectKey = docObjectKey(token, folderId, docId);

    mockMvc.perform(delete("/documents/{id}", docId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));

    // DB: download-url giờ 404
    mockMvc.perform(get("/documents/{id}/download-url", docId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isNotFound());

    // RustFS: object đã mất
    assertThat(objectKey).isNotNull();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> s3.headObject(
        HeadObjectRequest.builder().bucket(TEST_BUCKET).key(objectKey).build()))
      .isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class);

    // quota về 0
    assertThat(userRepo.findByEmail(email).orElseThrow().getStorageUsed()).isEqualTo(0L);
  }

  @Test
  @DisplayName("2.7 user khác xóa trả 403 (EDIT share cũng không được xóa)")
  void delete_stranger_returns403() throws Exception {
    String token = newUserToken();
    UUID folderId = createFolder(token, "Xoa", null);
    UUID docId = upload(token, folderId, "a.pdf", "application/pdf", "x".getBytes());

    mockMvc.perform(delete("/documents/{id}", docId)
        .header("Authorization", bearer(newUserToken())))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
  }

  @Test
  @DisplayName("2.7 RustFS lỗi sau commit: vẫn 200, DB nhất quán (không link gãy)")
  void delete_storageFailure_still200AndDbConsistent() throws Exception {
    String token = newUserToken();
    UUID folderId = createFolder(token, "Xoa", null);
    UUID docId = upload(token, folderId, "a.pdf", "application/pdf", "x".getBytes());

    // Giả lập RustFS sập đúng lúc xóa object (after-commit): DB đã commit,
    // lỗi storage chỉ bị log, không rollback, không 500.
    doThrow(new RuntimeException("rustfs down")).when(storageService).delete(anyString());

    mockMvc.perform(delete("/documents/{id}", docId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk());

    // row đã mất thật — không tồn tại trạng thái "DB còn mà object mất"
    mockMvc.perform(get("/documents/{id}/download-url", docId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isNotFound());
  }

  // ===== helpers =====

  private UUID upload(String token, UUID folderId, String filename,
      String contentType, byte[] bytes) throws Exception {
    MvcResult result = mockMvc.perform(multipart("/folders/{id}/documents", folderId)
        .file(new MockMultipartFile("file", filename, contentType, bytes))
        .header("Authorization", bearer(token)))
      .andExpect(status().isCreated())
      .andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(root.get("data").get("id").asText());
  }

  private String docObjectKey(String token, UUID folderId, UUID docId) throws Exception {
    MvcResult result = mockMvc.perform(
        get("/folders/{id}/contents", folderId).header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andReturn();
    JsonNode docs = objectMapper.readTree(result.getResponse().getContentAsString())
      .get("data").get("documents");
    for (JsonNode d : docs) {
      if (d.get("id").asText().equals(docId.toString())) {
        return d.get("objectKey").asText();
      }
    }
    return null;
  }
}
