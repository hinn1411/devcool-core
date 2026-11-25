package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.mapper.MediaDtoMapperImpl;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase;
import com.devcool.domain.media.port.in.PresignUploadUseCase;
import com.devcool.domain.media.port.in.PresignUploadUseCase.PresignedUpload;
import com.devcool.domain.media.port.in.UploadMediaUseCase;
import com.devcool.domain.media.port.in.command.PresignUploadCommand;
import com.devcool.domain.user.port.out.UserPort;
import java.security.Principal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Presigned upload (P3-T14) and the deprecated multipart upload: request shape and headers. */
@WebMvcTest(MediaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MediaDtoMapperImpl.class)
class MediaControllerTest {

  private static final Principal CALLER = new TestingAuthenticationToken("7", null);
  private static final String PRESIGN = "/api/v1/medias/presign-upload";
  private static final String KEY =
      "channel/42/2026/11/19/123e4567-e89b-12d3-a456-426614174000.png";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UploadMediaUseCase uploader;
  @MockitoBean private GetMediaUrlUseCase urlSigner;
  @MockitoBean private PresignUploadUseCase uploadSigner;

  // JwtAuthFilter is still created as a bean in the slice, so its ports need stand-ins.
  @MockitoBean private TokenIssuerPort tokenIssuerPort;
  @MockitoBean private UserPort userPort;

  @Test
  void presignUpload_200_returnsUrlKeyExpiresAt() throws Exception {
    when(uploadSigner.presignUpload(any()))
        .thenReturn(
            new PresignedUpload("https://s3/put?sig", KEY, Instant.parse("2026-11-19T10:05:00Z")));

    mockMvc
        .perform(
            post(PRESIGN)
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"channelId": 42, "contentType": "image/png", "size": 1024}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.url").value("https://s3/put?sig"))
        .andExpect(jsonPath("$.data.key").value(KEY))
        .andExpect(jsonPath("$.data.expiresAt").value("2026-11-19T10:05:00Z"));

    ArgumentCaptor<PresignUploadCommand> sent = ArgumentCaptor.forClass(PresignUploadCommand.class);
    verify(uploadSigner).presignUpload(sent.capture());
    assertThat(sent.getValue()).isEqualTo(new PresignUploadCommand(7L, 42L, "image/png", 1024));
  }

  @Test
  void presignUpload_missingFields_is422_andSignsNothing() throws Exception {
    mockMvc
        .perform(
            post(PRESIGN).principal(CALLER).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableEntity());

    verifyNoInteractions(uploadSigner);
  }

  @Test
  void upload_isStillServed_withDeprecationHeader() throws Exception {
    when(uploader.upload(any())).thenReturn(KEY);
    MockMultipartFile file =
        new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});

    mockMvc
        .perform(
            multipart("/api/v1/medias/upload")
                .file(file)
                .param("channelId", "42")
                .principal(CALLER))
        .andExpect(status().isOk())
        .andExpect(header().string("Deprecation", "true"))
        .andExpect(jsonPath("$.data.key").value(KEY));
  }
}
