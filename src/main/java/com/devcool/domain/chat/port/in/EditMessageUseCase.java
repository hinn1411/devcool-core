package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.EditedMessage;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;

public interface EditMessageUseCase {
  EditedMessage edit(EditMessageCommand command);
}
