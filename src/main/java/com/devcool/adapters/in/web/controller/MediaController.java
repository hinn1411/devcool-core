package com.devcool.adapters.in.web.controller;

import com.devcool.adapters.in.web.dto.mapper.MediaDtoMapper;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.in.web.util.ApiResponseFactory;
import com.devcool.domain.common.ErrorCode;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase;
import com.devcool.domain.media.port.in.UploadMediaUseCase;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/medias")
@RequiredArgsConstructor
public class MediaController {
  private final UploadMediaUseCase uploadUseCase;
  private final GetMediaUrlUseCase getMediaUrlUseCase;
  private final MediaDtoMapper mapper;

  @PostMapping("/upload")
  public ResponseEntity<ApiSuccessResponse<Map<String, String>>> uploadMedia(@RequestParam("file") MultipartFile file,
                                                                 @RequestParam Integer channelId,
                                                                 Authentication auth) {
    Integer userId = Integer.valueOf(auth.getName());
    UploadMediaCommand command = mapper.toUploadMediaCommand(file, userId, channelId);
    String key = uploadUseCase.upload(command);

    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(),
            "Upload media successfully",
            Map.of("key", key))
    );
  }

  @GetMapping("/presigned-url")
  public ResponseEntity<ApiSuccessResponse<Map<String, String>>> getPresignedUrl(
      @RequestParam String key
  ) {
    GetMediaUrlUseCase.PresignedUrlResult result = getMediaUrlUseCase.getPresignedUrl(key);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(),
            "Get presigned url successfully",
            Map.of("url", result.url(),
                "expiresAt", result.expiresAt().toString())
        )
    );
  }
}
