package com.devcool.adapters.in.web.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.in.web.dto.response.GetMessagesResponse;
import com.devcool.adapters.in.web.dto.response.MessageItemResponse;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MessageDtoMapperTest {

  private static final Instant CREATED = Instant.parse("2026-03-08T10:15:30Z");

  private final MessageDtoMapper mapper = new MessageDtoMapperImpl();

  private static MessageItem item(ContentType contentType) {
    return MessageItem.builder()
        .id(5)
        .content("hello")
        .contentType(contentType)
        .createdTime(CREATED)
        .userId(9)
        .senderName("Alice")
        .senderAvatar("https://cdn.devcool.com/avatars/alice.png")
        .build();
  }

  @Test
  void toGetCommand_keepsEachArgumentInItsOwnField() {
    GetMessageCommand command = mapper.toGetCommand(11, 22, 33, 44);

    assertThat(command.userId()).isEqualTo(11);
    assertThat(command.channelId()).isEqualTo(22);
    assertThat(command.cursorId()).isEqualTo(33);
    assertThat(command.limit()).isEqualTo(44);
  }

  @Test
  void toGetCommand_firstPage_hasNullCursor() {
    GetMessageCommand command = mapper.toGetCommand(11, 22, null, 44);

    assertThat(command.cursorId()).isNull();
  }

  @Test
  void toGetResponse_mapsEveryItemFieldCursorAndHasMore() {
    MessageList messageList = new MessageList(List.of(item(ContentType.TEXT)), 5, true);

    GetMessagesResponse response = mapper.toGetResponse(messageList);

    assertThat(response.getItems())
        .containsExactly(
            new MessageItemResponse(
                5,
                "hello",
                "TEXT",
                CREATED,
                9,
                "Alice",
                "https://cdn.devcool.com/avatars/alice.png"));
    assertThat(response.getCursorId()).isEqualTo(5);
    assertThat(response.isHasMore()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(ContentType.class)
  void toGetResponse_everyContentType_mapsToItsName(ContentType contentType) {
    GetMessagesResponse response =
        mapper.toGetResponse(new MessageList(List.of(item(contentType)), 5, false));

    assertThat(response.getItems())
        .singleElement()
        .extracting("contentType")
        .isEqualTo(contentType.name());
  }

  @Test
  void toGetResponse_emptyList_hasNoItemsAndNoCursor() {
    GetMessagesResponse response = mapper.toGetResponse(new MessageList(List.of(), null, false));

    assertThat(response.getItems()).isEmpty();
    assertThat(response.getCursorId()).isNull();
    assertThat(response.isHasMore()).isFalse();
  }
}
