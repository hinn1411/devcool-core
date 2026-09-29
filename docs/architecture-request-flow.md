# Request flow through all layers

A worked example of how a request crosses the hexagonal layers. Moved out of `CLAUDE.md` so it doesn't cost context on every turn; `CLAUDE.md` links here.

Example: `POST /api/v1/medias/upload` (media upload)

```
HTTP Request (MultipartFile + channelId)
  │
  ▼ adapters/in/web/controller/MediaController.java
    - Extracts userId from Spring Security Authentication principal
    - Calls MediaDtoMapper.toUploadMediaCommand(file, userId, channelId)
  │
  ▼ adapters/in/web/dto/mapper/MediaDtoMapper.java
    - Maps MultipartFile metadata → UploadMediaCommand record
      (file, size, contentType, userId, channelId)
  │
  ▼ domain/media/port/in/UploadMediaUseCase  [INBOUND PORT interface]
  │
  ▼ application/service/MediaService.java    [implements UploadMediaUseCase]
    - Validates file not empty → throws InvalidMediaContentException
    - Validates contentType in allowlist (jpeg/png/webp/mp4) → throws UnsupportedMediaTypeException
    - Checks the caller is a member of the channel
    - Builds S3 key: "channel/{channelId}/{yyyy/MM/dd}/{uuid}{ext}"
    - Opens file InputStream
    - Constructs MediaStoragePort.UploadRequest(key, stream, size, contentType)
    - Calls storagePort.upload(request)
  │
  ▼ domain/media/port/out/MediaStoragePort  [OUTBOUND PORT interface]
  │
  ▼ adapters/out/storage/S3StorageAdapter.java  [implements MediaStoragePort]
    - Reads bucket name from AwsProps config
    - Builds AWS SDK PutObjectRequest (bucket, key, contentType)
    - Calls s3Client.putObject(request, RequestBody.fromInputStream(...))
    - Returns UploadResult(bucket, objectKey)
  │
  ▼ Back in MediaService → returns S3 object key string
  │
  ▼ Back in MediaController
    - Wraps result with ApiResponseFactory.success(...)
  │
  ▼ HTTP 200 { "data": { "key": "channel/123/2026/03/08/uuid.jpg" } }
```

## Contracts at each boundary

- **Controller → Service:** `UploadMediaCommand` record (a domain object; no HTTP types).
- **Service → Storage adapter:** `MediaStoragePort.UploadRequest` record, defined inside the port interface.
- **Errors:** domain exceptions (`InvalidMediaContentException`, `UnsupportedMediaTypeException`) are thrown from the service layer and mapped to HTTP by the global `@ControllerAdvice` (`ApiExceptionHandler` + `HttpErrorMapper`).
