package com.devcool.adapters.in.web.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MediaDtoMapperTest {

  private final MediaDtoMapper mapper = new MediaDtoMapperImpl();

  @Test
  void toUploadMediaCommand_copiesTheFileMetadataAndExposesItsBytes() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3, 4, 5});

    UploadMediaCommand command = mapper.toUploadMediaCommand(file, 11, 22);

    assertThat(command.filename()).isEqualTo("photo.png");
    assertThat(command.content().open()).hasBinaryContent(new byte[] {1, 2, 3, 4, 5});
    assertThat(command.size()).isEqualTo(5L);
    assertThat(command.contentType()).isEqualTo("image/png");
    assertThat(command.userId()).isEqualTo(11);
    assertThat(command.channelId()).isEqualTo(22);
  }
}
