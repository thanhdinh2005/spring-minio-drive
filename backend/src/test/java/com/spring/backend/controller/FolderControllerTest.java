package com.spring.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.backend.common.PageResponse;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.dto.folder.FolderUpdateDto;
import com.spring.backend.mapper.FolderMapper;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.FolderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class FolderControllerTest {

  @InjectMocks
  private FolderController folderController;

  @Mock private FolderService folderService;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FolderMapper folderMapper = new FolderMapper();

  private UUID ownerId;
  private Authentication authentication;
  private Folder folder;

  @BeforeEach
  void setUp() {
    HandlerMethodArgumentResolver principalResolver = new AuthenticationPrincipalArgumentResolver();
    mockMvc = MockMvcBuilders.standaloneSetup(folderController)
      .setCustomArgumentResolvers(principalResolver)
      .build();

    ownerId = UUID.randomUUID();
    User owner = User.builder()
      .id(ownerId)
      .email("owner@email.com")
      .passwordHash("hash")
      .displayName("owner")
      .role(Role.USER)
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .build();
    authentication = new UsernamePasswordAuthenticationToken(
      new CustomUserDetails(owner, List.of()), null, List.of());
    // standalone MockMvc không có filter chain nên AuthenticationPrincipalArgumentResolver
    // chỉ đọc được từ SecurityContextHolder (không qua TestSecurityContextHolder)
    SecurityContextHolder.getContext().setAuthentication(authentication);

    folder = Folder.builder()
      .id(UUID.randomUUID())
      .name("Docs")
      .owner(owner)
      .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("GET /folder")
  class ListTests {

    @Test
    @DisplayName("should list root folders when parentFolderId is absent")
    void shouldListRoot_whenNoParentParam() throws Exception {
      PageResponse<FolderDto> page = PageResponse.from(
        new PageImpl<>(List.of(folderMapper.toDto(folder)), PageRequest.of(0, 10, Sort.by("updatedAt").descending()), 1));
      given(folderService.findAllByOwnerIdInRoot(0, 10, "updatedAt", "desc", ownerId)).willReturn(page);

      mockMvc.perform(get("/folder"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].name").value("Docs"));

      verify(folderService).findAllByOwnerIdInRoot(0, 10, "updatedAt", "desc", ownerId);
    }

    @Test
    @DisplayName("should list child folders when parentFolderId is present")
    void shouldListChildren_whenParentParamPresent() throws Exception {
      UUID parentId = UUID.randomUUID();
      PageResponse<FolderDto> page = PageResponse.from(
        new PageImpl<>(List.of(folderMapper.toDto(folder)), PageRequest.of(0, 10, Sort.by("updatedAt").descending()), 1));
      given(folderService.findAllByOwnerIdAndParentFolderId(
        anyInt(), anyInt(), anyString(), anyString(), eq(ownerId), eq(parentId))).willReturn(page);

      mockMvc.perform(get("/folder").param("parentFolderId", parentId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].name").value("Docs"));

      verify(folderService).findAllByOwnerIdAndParentFolderId(0, 10, "updatedAt", "desc", ownerId, parentId);
    }

    @Test
    @DisplayName("should get folder by id")
    void shouldGetById() throws Exception {
      given(folderService.findById(folder.getId())).willReturn(folderMapper.toDto(folder));

      mockMvc.perform(get("/folder/{folderId}", folder.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Docs"));
    }
  }

  @Nested
  @DisplayName("POST /folder")
  class CreateTests {

    @Test
    @DisplayName("should create folder and return it")
    void shouldCreate() throws Exception {
      FolderCreateDto dto = FolderCreateDto.builder().name("Docs").parentId(null).build();
      given(folderService.create(any(FolderCreateDto.class), eq(ownerId))).willReturn(folderMapper.toDto(folder));

      mockMvc.perform(post("/folder")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Docs"));

      verify(folderService).create(any(FolderCreateDto.class), eq(ownerId));
    }
  }

  @Nested
  @DisplayName("PUT /folder")
  class UpdateTests {

    @Test
    @DisplayName("should update folder and return it")
    void shouldUpdate() throws Exception {
      FolderUpdateDto dto = FolderUpdateDto.builder().id(folder.getId()).name("Renamed").build();
      Folder renamed = Folder.builder().id(folder.getId()).name("Renamed").owner(folder.getOwner()).build();
      given(folderService.update(any(FolderUpdateDto.class), eq(ownerId))).willReturn(folderMapper.toDto(renamed));

      mockMvc.perform(put("/folder")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Renamed"));
    }
  }

  @Nested
  @DisplayName("DELETE /folder")
  class DeleteTests {

    @Test
    @DisplayName("should soft delete folders and return success")
    void shouldSoftDelete() throws Exception {
      List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

      mockMvc.perform(delete("/folder")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(ids)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

      verify(folderService).deleteSoft(eq(ids), eq(ownerId));
    }
  }
}
