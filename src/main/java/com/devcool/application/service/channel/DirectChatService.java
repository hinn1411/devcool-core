package com.devcool.application.service.channel;

import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.DirectChat;
import com.devcool.domain.channel.model.DirectChatPair;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.in.OpenDirectChatUseCase;
import com.devcool.domain.channel.port.in.command.OpenDirectChatCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.channel.port.out.DirectChatPort;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class DirectChatService implements OpenDirectChatUseCase {
  /** NOT NULL column; clients show the other participant's name instead. */
  private static final String DIRECT_CHAT_NAME = "direct-message";

  private final UserPort userPort;
  private final ChannelPort channelPort;
  private final DirectChatPort directChatPort;
  private final TransactionOperations transactions;

  public DirectChatService(
      UserPort userPort,
      ChannelPort channelPort,
      DirectChatPort directChatPort,
      TransactionOperations transactions) {
    this.userPort = userPort;
    this.channelPort = channelPort;
    this.directChatPort = directChatPort;
    this.transactions = transactions;
  }

  /**
   * One transaction via {@code TransactionOperations}, so a lost pair race can mark it
   * rollback-only (dropping its own channel) and still return the winner's id.
   */
  @Override
  public DirectChat openDirectChat(OpenDirectChatCommand command) {
    DirectChatPair pair = DirectChatPair.of(command.callerId(), command.targetUserId());
    return Objects.requireNonNull(
        transactions.execute(status -> findOrCreate(pair, command, status)));
  }

  private DirectChat findOrCreate(
      DirectChatPair pair, OpenDirectChatCommand command, TransactionStatus status) {
    Optional<Long> channelId = directChatPort.findChannelId(pair);
    if (channelId.isPresent()) {
      return new DirectChat(channelId.get(), false);
    }

    User target = requireActive(command.targetUserId());
    User caller = requireActive(command.callerId());
    Long newChannelId = createChannel(caller, target);
    boolean isDirectChatCreated = directChatPort.insert(pair, newChannelId);
    if (!isDirectChatCreated) {
      // Lost the pair race: roll back our channel, return the winner's
      status.setRollbackOnly();
      return directChatPort
          .findChannelId(pair)
          .map(id -> new DirectChat(id, false))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Lost the direct-chat race but no winner found for " + pair));
    }
    return new DirectChat(newChannelId, true);
  }

  /** DELETED and BLOCKED users look missing: account state isn't disclosed. */
  private User requireActive(Long userId) {
    return userPort
        .findById(userId)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .orElseThrow(() -> new UserNotFoundException(userId));
  }

  private Long createChannel(User caller, User target) {
    Instant joinedTime = Instant.now();
    List<Member> members =
        List.of(
            Member.builder().user(caller).role(MemberType.CREATOR).joinedTime(joinedTime).build(),
            Member.builder().user(target).role(MemberType.MEMBER).joinedTime(joinedTime).build());
    return channelPort.save(
        Channel.builder()
            .name(DIRECT_CHAT_NAME)
            .channelType(ChannelType.PRIVATE_CHAT)
            .boundaryType(BoundaryType.PRIVATE)
            .creator(caller)
            .totalOfMembers(members.size())
            .members(members)
            .build());
  }
}
