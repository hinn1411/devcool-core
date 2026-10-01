package com.devcool.adapters.in.web.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.in.web.dto.request.AddMembersRequest;
import com.devcool.adapters.in.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.in.web.dto.request.UpdateChannelRequest;
import com.devcool.adapters.in.web.dto.response.AddMembersResponse;
import com.devcool.adapters.in.web.dto.response.ChannelListItemResponse;
import com.devcool.adapters.in.web.dto.response.CreateChannelResponse;
import com.devcool.adapters.in.web.dto.response.GetChannelResponse;
import com.devcool.adapters.in.web.dto.response.UpdateChannelResponse;
import com.devcool.domain.channel.model.ChannelListItem;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.GetChannelCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChannelDtoMapperTest {

  private static final Instant EXPIRED = Instant.parse("2030-01-01T00:00:00Z");

  private final ChannelDtoMapper mapper = new ChannelDtoMapperImpl();

  @Test
  void toCreateChannelCommand_carriesEveryFieldAndKeepsCreatorSeparateFromLeader() {
    CreateChannelRequest request =
        new CreateChannelRequest(
            "forum-name-1", BoundaryType.PRIVATE, EXPIRED, ChannelType.FORUM, 22, List.of(3, 4, 5));

    CreateChannelCommand command = mapper.toCreateChannelCommand(request, 11);

    assertThat(command.name()).isEqualTo("forum-name-1");
    assertThat(command.boundaryType()).isEqualTo(BoundaryType.PRIVATE);
    assertThat(command.expiredTime()).isEqualTo(EXPIRED);
    assertThat(command.channelType()).isEqualTo(ChannelType.FORUM);
    assertThat(command.creatorId()).isEqualTo(11);
    assertThat(command.leaderId()).isEqualTo(22);
    assertThat(command.memberIds()).containsExactly(3, 4, 5);
  }

  @Test
  void toCreateChannelCommand_optionalFieldsAbsent_stayNull() {
    CreateChannelRequest request =
        new CreateChannelRequest(
            "lounge-name-1", BoundaryType.PUBLIC, null, ChannelType.LOUNGE, null, null);

    CreateChannelCommand command = mapper.toCreateChannelCommand(request, 11);

    assertThat(command.expiredTime()).isNull();
    assertThat(command.leaderId()).isNull();
    assertThat(command.memberIds()).isNull();
    assertThat(command.creatorId()).isEqualTo(11);
  }

  @Test
  void toCreateChannelResponse_carriesChannelId() {
    CreateChannelResponse response = mapper.toCreateChannelResponse(42);

    assertThat(response.getChannelId()).isEqualTo(42);
  }

  @Test
  void toUpdateChannelCommand_carriesEveryField() {
    UpdateChannelRequest request =
        new UpdateChannelRequest("renamed-room", BoundaryType.PUBLIC, EXPIRED, ChannelType.LOUNGE);

    UpdateChannelCommand command = mapper.toUpdateChannelCommand(request, 9);

    assertThat(command.callerId()).isEqualTo(9);
    assertThat(command.name()).isEqualTo("renamed-room");
    assertThat(command.boundaryType()).isEqualTo(BoundaryType.PUBLIC);
    assertThat(command.expiredTime()).isEqualTo(EXPIRED);
    assertThat(command.channelType()).isEqualTo(ChannelType.LOUNGE);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void toUpdateChannelResponse_carriesFlag(boolean updated) {
    UpdateChannelResponse response = mapper.toUpdateChannelResponse(updated);

    assertThat(response.isChannelUpdated()).isEqualTo(updated);
  }

  @Test
  void toAddMembersCommand_carriesCallerAndUserIds() {
    AddMembersCommand command = mapper.toAddMembersCommand(new AddMembersRequest(List.of(7, 8)), 9);

    assertThat(command.callerId()).isEqualTo(9);
    assertThat(command.userIds()).containsExactly(7, 8);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void toAddMembersResponse_carriesFlag(boolean added) {
    AddMembersResponse response = mapper.toAddMembersResponse(added);

    assertThat(response.isMemberAdded()).isEqualTo(added);
  }

  @Test
  void toGetChannelCommand_keepsEachArgumentInItsOwnField() {
    GetChannelCommand command = mapper.toGetChannelCommand(11, 22, 33);

    assertThat(command.memberId()).isEqualTo(11);
    assertThat(command.cursorId()).isEqualTo(22);
    assertThat(command.limit()).isEqualTo(33);
  }

  @Test
  void toGetChannelCommand_firstPage_hasNullCursor() {
    GetChannelCommand command = mapper.toGetChannelCommand(11, null, 33);

    assertThat(command.cursorId()).isNull();
  }

  @Test
  void toGetChannelResponse_mapsItemsEnumsCursorAndHasMore() {
    ChannelListPage page =
        new ChannelListPage(
            List.of(
                new ChannelListItem(1, "general-room", ChannelType.LOUNGE, BoundaryType.PUBLIC),
                new ChannelListItem(
                    2, "secret-room", ChannelType.PRIVATE_CHAT, BoundaryType.PRIVATE)),
            2,
            true);

    GetChannelResponse response = mapper.toGetChannelResponse(page);

    assertThat(response.getChannels())
        .containsExactly(
            new ChannelListItemResponse(1, "general-room", "LOUNGE", "PUBLIC"),
            new ChannelListItemResponse(2, "secret-room", "PRIVATE_CHAT", "PRIVATE"));
    assertThat(response.getNextCursorId()).isEqualTo(2);
    assertThat(response.isHasMore()).isTrue();
  }

  @Test
  void toGetChannelResponse_emptyPage_hasNoItemsAndNoCursor() {
    GetChannelResponse response =
        mapper.toGetChannelResponse(new ChannelListPage(List.of(), null, false));

    assertThat(response.getChannels()).isEmpty();
    assertThat(response.getNextCursorId()).isNull();
    assertThat(response.isHasMore()).isFalse();
  }
}
