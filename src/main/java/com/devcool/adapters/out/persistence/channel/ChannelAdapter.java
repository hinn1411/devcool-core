package com.devcool.adapters.out.persistence.channel;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.mapper.ChannelMapper;
import com.devcool.adapters.out.persistence.channel.projection.ChannelListRow;
import com.devcool.adapters.out.persistence.channel.repository.ChannelRepository;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.ChannelListItem;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.port.out.ChannelPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChannelAdapter implements ChannelPort {
  private static final Logger log = LoggerFactory.getLogger(ChannelAdapter.class);
  private final ChannelRepository repo;
  private final ChannelMapper mapper;

  @Override
  public Optional<Channel> findById(Integer id) {
    return repo.findById(id).map(mapper::toDomain);
  }

  @Override
  public ChannelListPage loadChannels(Integer memberId, Integer currentCursor, Integer limit) {
    // Use limit + 1 trick to check next data block
    Pageable pagination = PageRequest.of(0, limit + 1);
    List<ChannelListRow> channelRows = repo.findChannelPageByMemberId(memberId, currentCursor, pagination);
    boolean hasMore = channelRows.size() > limit;
    List<ChannelListRow> page = hasMore ? channelRows.subList(0, limit) : channelRows;
    Integer nextCursor = Optional.of(page)
        .filter(rows -> !rows.isEmpty())
        .map(rows -> rows.getLast().id())
        .orElse(null);


    return new ChannelListPage(
        page.stream().map(row -> new ChannelListItem(row.id(),
            row.name(),
            row.channelType(),
            row.boundaryType())).toList(),
        nextCursor,
        hasMore
    );
  }

  @Override
  public boolean existById(Integer id) {
    return repo.existsById(id);
  }

  @Override
  public Integer save(Channel channel) {
    ChannelEntity entity = mapper.toEntity(channel);
    entity.getMembers().forEach(m -> m.setChannel(entity));
    ChannelEntity saved = repo.save(entity);
    return saved.getId();
  }

  @Override
  public boolean update(Channel channel) {
    ChannelEntity entity = mapper.toEntity(channel);
    entity.getMembers().forEach(m -> m.setChannel(entity));
    repo.save(entity);
    return true;
  }

  @Override
  @Transactional
  public boolean increaseTotalMembers(Integer channelId, Integer newMembers) {
    return repo.increaseTotalMembers(channelId, newMembers) > 0;
  }
}
