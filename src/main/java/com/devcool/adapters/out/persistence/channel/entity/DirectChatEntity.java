package com.devcool.adapters.out.persistence.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/** Mapped for ddl validation; writes use the native queries. */
@Entity
@Table(name = "DIRECT_CHAT")
@IdClass(DirectChatEntity.Key.class)
@Getter
@Setter
public class DirectChatEntity {

  @Id
  @Column(name = "USER_LOW_ID", nullable = false)
  private Long userLowId;

  @Id
  @Column(name = "USER_HIGH_ID", nullable = false)
  private Long userHighId;

  @Column(name = "CHANNEL_ID", nullable = false, unique = true)
  private Long channelId;

  public record Key(Long userLowId, Long userHighId) implements Serializable {}
}
