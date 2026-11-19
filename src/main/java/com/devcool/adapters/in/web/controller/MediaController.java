package com.devcool.adapters.in.web.controller;

import com.devcool.adapters.in.web.dto.mapper.MediaDtoMapper;
import com.devcool.adapters.in.web.dto.request.PresignUploadRequest;
import com.devcool.adapters.in.web.dto.response.PresignUploadResponse;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.in.web.util.ApiResponseFactory;
import com.devcool.domain.common.ErrorCode;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase;
import com.devcool.domain.media.port.in.PresignUploadUseCase;
import com.devcool.domain.media.port.in.UploadMediaUseCase;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/medias")
@RequiredArgsConstructor
public class MediaController {
  private final UploadMediaUseCase uploadUseCase;
  private final GetMediaUrlUseCase getMediaUrlUseCase;
  private final PresignUploadUseCase presignUploadUseCase;
  private final MediaDtoMapper mapper;

  /** Deprecated: bytes go through the API. Use presign-upload. */
  @Operation(
      summary = "Upload media through the API",
      description = "Deprecated: use POST /api/v1/medias/presign-upload and PUT to S3.",
      deprecated = true)
  @PostMapping("/upload")
  public ResponseEntity<ApiSuccessResponse<Map<String, String>>> uploadMedia(
      @RequestParam("file") MultipartFile file, @RequestParam Long channelId, Authentication auth) {
    Long userId = Long.valueOf(auth.getName());
    UploadMediaCommand command = mapper.toUploadMediaCommand(file, userId, channelId);
    String key = uploadUseCase.upload(command);

    return ResponseEntity.ok()
        .header("Deprecation", "true")
        .body(
            ApiResponseFactory.success(
                HttpStatus.OK,
                ErrorCode.OK.code(),
                "Upload media successfully",
                Map.of("key", key)));
  }

  @Operation(
      summary = "Get a presigned S3 PUT URL",
      description = "PUT the file with the declared Content-Type and size, then SEND the key.")
  @PostMapping("/presign-upload")
  public ResponseEntity<ApiSuccessResponse<PresignUploadResponse>> presignUpload(
      @Valid @RequestBody PresignUploadRequest request, Authentication auth) {
    Long userId = Long.valueOf(auth.getName());
    PresignUploadUseCase.PresignedUpload upload =
        presignUploadUseCase.presignUpload(mapper.toPresignUploadCommand(request, userId));
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK,
            ErrorCode.OK.code(),
            "Presigned upload URL created",
            mapper.toPresignUploadResponse(upload)));
  }

  @GetMapping("/presigned-url")
  public ResponseEntity<ApiSuccessResponse<Map<String, String>>> getPresignedUrl(
      @RequestParam String key, Authentication auth) {
    Long userId = Long.valueOf(auth.getName());
    GetMediaUrlUseCase.PresignedUrlResult result = getMediaUrlUseCase.getPresignedUrl(userId, key);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK,
            ErrorCode.OK.code(),
            "Get presigned url successfully",
            Map.of("url", result.url(), "expiresAt", result.expiresAt().toString())));
  }
}
