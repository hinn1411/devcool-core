package com.devcool.adapters.in.web.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.in.web.dto.request.EditMessageRequest;
import com.devcool.adapters.in.web.dto.response.EditMessageResponse;
import com.devcool.adapters.in.web.dto.response.GetMessagesResponse;
import com.devcool.adapters.in.web.dto.response.MessageItemResponse;
import com.devcool.adapters.in.web.dto.response.ReactionSummaryResponse;
import com.devcool.adapters.in.web.dto.response.ReplyPreviewResponse;
import com.devcool.domain.chat.model.EditedMessage;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.ReactionSummary;
import com.devcool.domain.chat.model.ReplyPreview;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MessageDtoMapperTest {

  private static final Instant CREATED = Instant.parse("2026-03-08T10:15:30Z");
  private static final Instant EDITED = Instant.parse("2026-03-08T11:00:00Z");

  private final MessageDtoMapper mapper = new MessageDtoMapperImpl();

  private static MessageItem item(ContentType contentType) {
    return MessageItem.builder()
        .id(5L)
        .seq(7L)
        .content("hello")
        .contentType(contentType)
        .createdTime(CREATED)
        .version(2)
        .editedTime(EDITED)
        .userId(9L)
        .senderName("Alice")
        .senderAvatar("https://cdn.devcool.com/avatars/alice.png")
        .build();
  }

  @Test
  void toGetCommand_keepsEachArgumentInItsOwnField() {
    GetMessageCommand command = mapper.toGetCommand(11L, 22L, 33L, 55L, 66L, 44);

    assertThat(command).isEqualTo(new GetMessageCommand(11L, 22L, 33L, 55L, 66L, 44));
  }

  @Test
  void toGetCommand_firstPage_hasNullCursor() {
    GetMessageCommand command = mapper.toGetCommand(11L, 22L, null, null, null, 44);

    assertThat(command.cursorId()).isNull();
  }

  @Test
  void toGetResponse_mapsEveryItemFieldCursorAndHasMore() {
    MessageList messageList = new MessageList(List.of(item(ContentType.TEXT)), 5L, true);

    GetMessagesResponse response = mapper.toGetResponse(messageList);

    assertThat(response.getItems())
        .containsExactly(
            new MessageItemResponse(
                5L,
                7L,
                "hello",
                "TEXT",
                CREATED,
                2,
                EDITED,
                null,
                9L,
                "Alice",
                "https://cdn.devcool.com/avatars/alice.png",
                null,
                List.of()));
    assertThat(response.getCursorId()).isEqualTo(5);
    assertThat(response.isHasMore()).isTrue();
  }

  @Test
  void toGetResponse_tombstone_carriesDeletedTimeAndNoContent() {
    Instant deleted = EDITED.plusSeconds(60);
    MessageItem tombstone = item(ContentType.TEXT);
    tombstone.setContent(null);
    tombstone.setDeletedTime(deleted);

    MessageItemResponse response =
        mapper
            .toGetResponse(new MessageList(List.of(tombstone), null, false))
            .getItems()
            .getFirst();

    assertThat(response.deletedTime()).isEqualTo(deleted);
    assertThat(response.content()).isNull();
    assertThat(response.seq()).isEqualTo(7L);
  }

  @Test
  void toGetResponse_reply_mapsThePreview() {
    MessageItem reply = item(ContentType.TEXT);
    reply.setReplyTo(new ReplyPreview(4L, 6L, 8L, "Bob", ContentType.MARKDOWN, "# hi", false));

    MessageItemResponse response =
        mapper.toGetResponse(new MessageList(List.of(reply), null, false)).getItems().getFirst();

    assertThat(response.replyTo())
        .isEqualTo(new ReplyPreviewResponse(4L, 6L, 8L, "Bob", "MARKDOWN", "# hi", false));
  }

  @Test
  void toGetResponse_reactions_keepOrderCountAndReactedByMe() {
    MessageItem reacted = item(ContentType.TEXT);
    reacted.setReactions(
        List.of(new ReactionSummary("👍", 3, true), new ReactionSummary("🎉", 1, false)));

    MessageItemResponse response =
        mapper.toGetResponse(new MessageList(List.of(reacted), null, false)).getItems().getFirst();

    assertThat(response.reactions())
        .containsExactly(
            new ReactionSummaryResponse("👍", 3, true),
            new ReactionSummaryResponse("🎉", 1, false));
  }

  @ParameterizedTest
  @EnumSource(ContentType.class)
  void toGetResponse_everyContentType_mapsToItsName(ContentType contentType) {
    GetMessagesResponse response =
        mapper.toGetResponse(new MessageList(List.of(item(contentType)), 5L, false));

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

  // ---- edit (P3-T06) ---------------------------------------------------------------------------

  @Test
  void toEditCommand_takesCallerAndIdFromThePath_andTheRestFromTheBody() {
    EditMessageCommand command = mapper.toEditCommand(7L, 500L, new EditMessageRequest("new", 3));

    assertThat(command).isEqualTo(new EditMessageCommand(7L, 500L, "new", 3));
  }

  @Test
  void toEditResponse_mapsEveryField() {
    EditedMessage edited =
        new EditedMessage(500L, 42L, 9L, ContentType.MARKDOWN, "# new", 4, EDITED);

    assertThat(mapper.toEditResponse(edited))
        .isEqualTo(new EditMessageResponse(500L, 42L, 9L, "# new", "MARKDOWN", 4, EDITED));
  }
}
