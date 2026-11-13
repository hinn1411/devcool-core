package com.devcool.adapters.out.persistence.channel;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.mapper.ChannelMapper;
import com.devcool.adapters.out.persistence.channel.projection.ChannelListRow;
import com.devcool.adapters.out.persistence.channel.repository.ChannelRepository;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.ChannelListItem;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.ReplyPreview;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChannelAdapter implements ChannelPort {
  private static final Logger log = LoggerFactory.getLogger(ChannelAdapter.class);
  private final ChannelRepository repo;
  private final ChannelMapper mapper;

  @Override
  public Optional<Channel> findById(Long id) {
    return repo.findById(id).map(mapper::toDomain);
  }

  @Override
  public ChannelListPage loadChannels(Long memberId, Long currentCursor, Integer limit) {
    // Use limit + 1 trick to check next data block
    Pageable pagination = PageRequest.of(0, limit + 1);
    List<ChannelListRow> channelRows =
        repo.findChannelPageByMemberId(memberId, currentCursor, pagination);
    boolean hasMore = channelRows.size() > limit;
    List<ChannelListRow> page = hasMore ? channelRows.subList(0, limit) : channelRows;
    Long nextCursor =
        Optional.of(page)
            .filter(rows -> !rows.isEmpty())
            .map(rows -> rows.getLast().id())
            .orElse(null);

    return new ChannelListPage(
        page.stream().map(ChannelAdapter::toListItem).toList(), nextCursor, hasMore);
  }

  /** A row without a joined message (empty channel) has every {@code last*} column null. */
  private static ChannelListItem toListItem(ChannelListRow row) {
    ReplyPreview lastMessage = row.lastMessageId() == null ? null : toLastMessage(row);

    return new ChannelListItem(
        row.id(),
        row.name(),
        row.channelType(),
        row.boundaryType(),
        row.unreadCount(),
        lastMessage);
  }

  @Override
  public boolean existById(Long id) {
    return repo.existsById(id);
  }

  @Override
  public Optional<ChannelAccessInfo> findAccessInfo(Long id) {
    return repo.findAccessRowById(id)
        .map(row -> new ChannelAccessInfo(row.channelType(), row.totalOfMembers()));
  }

  @Override
  public Long save(Channel channel) {
    ChannelEntity entity = mapper.toEntity(channel);
    entity.getMembers().forEach(m -> m.setChannel(entity));
    ChannelEntity saved = repo.save(entity);
    return saved.getId();
  }

  @Override
  public boolean update(Channel channel) {
    return repo.updateChannelInfo(
            channel.getId(),
            channel.getName(),
            channel.getChannelType(),
            channel.getBoundaryType(),
            channel.getExpiredTime())
        > 0;
  }

  @Override
  public boolean increaseTotalMembers(Long channelId, Integer newMembers) {
    return repo.increaseTotalMembers(channelId, newMembers) > 0;
  }

  @Override
  public Optional<Long> getNextSequence(Long channelId) {
    return repo.bumpLastSeq(channelId);
  }

  private static ReplyPreview toLastMessage(ChannelListRow row) {
    return ReplyPreview.of(
        row.lastMessageId(),
        row.lastMessageSeq(),
        row.lastSenderId(),
        row.lastSenderName(),
        row.lastContentType(),
        row.lastContent(),
        Objects.nonNull(row.lastDeletedTime()));
  }
}
