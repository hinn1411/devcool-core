package com.devcool.domain.media.port.in;

import com.devcool.domain.media.port.in.command.UploadMediaCommand;

public interface UploadMediaUseCase {
  boolean upload(UploadMediaCommand command);
}
