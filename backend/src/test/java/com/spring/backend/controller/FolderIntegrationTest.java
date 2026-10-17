package com.spring.backend.controller;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import tools.jackson.databind.JsonNode;
import com.spring.backend.AbstractIntegrationTest;
import com.spring.backend.repository.UserRepository;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

/**
 * Integration test 2.1/2.2/2.3 (upload trong contents)/2.6/2.7 cho folder:
 * HTTP thật + JWT thật + Postgres thật + RustFS thật.
 */
public class FolderIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private UserRepository userRepo;

  @Autowired
  private S3Client s3;

  // ===== 2.1 POST /api/folders =====

  @Test
  @DisplayName("2.1 tạo thư mục gốc trả 201")
  void createRoot_returns201() throws Exception {
    String token = newUserToken();

    mockMvc.perform(post("/folders")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Tài liệu dự án\",\"parentFolderId\":null}"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.name").value("Tài liệu dự án"))
      .andExpect(jsonPath("$.data.parentFolderId").doesNotExist())
      .andExpect(jsonPath("$.data.ownerId").exists());
  }

  @Test
  @DisplayName("2.1 tên chứa ký tự cấm trả 400 INVALID_FOLDER_NAME")
  void create_invalidName_returns400() throws Exception {
    String token = newUserToken();

    mockMvc.perform(post("/folders")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"a/b\",\"parentFolderId\":null}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value("ERR_FOLDER_003"));
  }

  @Test
  @DisplayName("2.1 parentFolderId không tồn tại trả 404 FOLDER_NOT_FOUND")
  void create_parentNotFound_returns404() throws Exception {
    String token = newUserToken();

    mockMvc.perform(post("/folders")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Con\",\"parentFolderId\":\"" + UUID.randomUUID() + "\"}"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
  }

  @Test
  @DisplayName("2.1 parent của user khác trả 403 FOLDER_ACCESS_DENIED")
  void create_parentOfStranger_returns403() throws Exception {
    String ownerToken = newUserToken();
    UUID parentId = createFolder(ownerToken, "Riêng tư", null);
    String strangerToken = newUserToken();

    mockMvc.perform(post("/folders")
        .header("Authorization", bearer(strangerToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Chen ngang\",\"parentFolderId\":\"" + parentId + "\"}"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
  }

  // ===== 2.2 GET /api/folders/{id}/contents =====

  @Test
  @DisplayName("2.2 contents liệt kê subFolders + documents trực tiếp")
  void contents_listsSubFoldersAndDocuments() throws Exception {
    String token = newUserToken();
    UUID parentId = createFolder(token, "Cha", null);
    UUID childId = createFolder(token, "Con 1", parentId);

    byte[] pdf = "pdf-bytes".getBytes();
    mockMvc.perform(multipart("/folders/{id}/documents", parentId)
        .file(new MockMultipartFile("file", "bao_cao.pdf", "application/pdf", pdf))
        .header("Authorization", bearer(token)))
      .andExpect(status().isCreated());

    mockMvc.perform(get("/folders/{id}/contents", parentId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.folder.id").value(parentId.toString()))
      .andExpect(jsonPath("$.data.subFolders[0].id").value(childId.toString()))
      .andExpect(jsonPath("$.data.subFolders[0].name").value("Con 1"))
      .andExpect(jsonPath("$.data.documents[0].name").value("bao_cao.pdf"))
      .andExpect(jsonPath("$.data.documents[0].size").value(pdf.length))
      .andExpect(jsonPath("$.data.documents[0].mimeType").value("application/pdf"));
  }

  @Test
  @DisplayName("2.2 folder của user khác trả 403")
  void contents_stranger_returns403() throws Exception {
    String ownerToken = newUserToken();
    UUID folderId = createFolder(ownerToken, "Riêng tư", null);

    mockMvc.perform(get("/folders/{id}/contents", folderId)
        .header("Authorization", bearer(newUserToken())))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
  }

  @Test
  @DisplayName("2.2 folder không tồn tại trả 404")
  void contents_notFound_returns404() throws Exception {
    mockMvc.perform(get("/folders/{id}/contents", UUID.randomUUID())
        .header("Authorization", bearer(newUserToken())))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
  }

  // ===== 2.6 PATCH /api/folders/{id} =====

  @Test
  @DisplayName("2.6 đổi tên + di chuyển folder")
  void patch_renameAndMove_ok() throws Exception {
    String token = newUserToken();
    UUID folderA = createFolder(token, "A", null);
    UUID folderB = createFolder(token, "B", null);

    // đổi tên
    mockMvc.perform(patch("/folders/{id}", folderA)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Tên mới\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("Tên mới"));

    // di chuyển A vào B
    mockMvc.perform(patch("/folders/{id}", folderA)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentFolderId\":\"" + folderB + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.parentFolderId").value(folderB.toString()));
  }

  @Test
  @DisplayName("2.6 di chuyển vào chính nó / con cháu trả 400")
  void patch_moveIntoItselfOrDescendant_returns400() throws Exception {
    String token = newUserToken();
    UUID parentId = createFolder(token, "Cha", null);
    UUID childId = createFolder(token, "Con", parentId);

    // vào chính nó
    mockMvc.perform(patch("/folders/{id}", parentId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentFolderId\":\"" + parentId + "\"}"))
      .andExpect(status().isBadRequest());

    // cha vào con (cycle)
    mockMvc.perform(patch("/folders/{id}", parentId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentFolderId\":\"" + childId + "\"}"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("2.6 sửa folder / đích của user khác trả 403")
  void patch_stranger_returns403() throws Exception {
    String ownerToken = newUserToken();
    UUID folderId = createFolder(ownerToken, "Riêng tư", null);
    UUID destId = createFolder(newUserToken(), "Đích lạ", null);
    String strangerToken = newUserToken();

    // sửa folder người khác
    mockMvc.perform(patch("/folders/{id}", folderId)
        .header("Authorization", bearer(strangerToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Hack\"}"))
      .andExpect(status().isForbidden());

    // di chuyển vào đích người khác
    UUID ownFolder = createFolder(strangerToken, "Của tôi", null);
    mockMvc.perform(patch("/folders/{id}", ownFolder)
        .header("Authorization", bearer(strangerToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentFolderId\":\"" + destId + "\"}"))
      .andExpect(status().isForbidden());
  }

  // ===== 2.7 DELETE /api/folders/{id} =====

  @Test
  @DisplayName("2.7 xóa cứng cascade subFolders + documents + object RustFS + trừ quota")
  void deleteHard_cascade() throws Exception {
    String email = createUser();
    String token = login(email);
    UUID parentId = createFolder(token, "Cha", null);
    UUID childId = createFolder(token, "Con", parentId);

    String key1 = uploadAndReturnKey(token, parentId, "a.pdf", "application/pdf", "file-one".getBytes());
    String key2 = uploadAndReturnKey(token, childId, "b.png", "image/png", "file-two!".getBytes());

    mockMvc.perform(delete("/folders/{id}", parentId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));

    // DB: cha + con đều mất
    mockMvc.perform(get("/folders/{id}/contents", parentId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isNotFound());
    mockMvc.perform(get("/folders/{id}/contents", childId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isNotFound());

    // RustFS: object thật đã bị xóa (after-commit chạy xong trước khi response trả về)
    assertObjectGone(key1);
    assertObjectGone(key2);

    // quota về 0
    assertThat(userRepo.findByEmail(email).orElseThrow().getStorageUsed()).isEqualTo(0L);
  }

  @Test
  @DisplayName("2.7 user khác xóa trả 403 (EDIT share cũng không được xóa)")
  void deleteHard_stranger_returns403() throws Exception {
    String ownerToken = newUserToken();
    UUID folderId = createFolder(ownerToken, "Riêng tư", null);

    mockMvc.perform(delete("/folders/{id}", folderId)
        .header("Authorization", bearer(newUserToken())))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
  }

  @Test
  @DisplayName("không JWT bị từ chối (403 do project chưa custom authentication entry point)")
  void unauthenticated_returns403() throws Exception {
    mockMvc.perform(get("/folders/{id}/contents", UUID.randomUUID()))
      .andExpect(status().isForbidden());
  }

  // ===== helpers =====

  private String uploadAndReturnKey(String token, UUID folderId, String filename,
      String contentType, byte[] bytes) throws Exception {
    var result = mockMvc.perform(multipart("/folders/{id}/documents", folderId)
        .file(new MockMultipartFile("file", filename, contentType, bytes))
        .header("Authorization", bearer(token)))
      .andExpect(status().isCreated())
      .andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return root.get("data").get("objectKey").asText();
  }

  private void assertObjectGone(String objectKey) {
    assertThatThrownBy(() -> s3.headObject(HeadObjectRequest.builder()
        .bucket(TEST_BUCKET)
        .key(objectKey)
        .build()))
      .isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class)
      .satisfies(e -> assertThat(
        ((software.amazon.awssdk.services.s3.model.S3Exception) e).statusCode()).isEqualTo(404));
  }
}
