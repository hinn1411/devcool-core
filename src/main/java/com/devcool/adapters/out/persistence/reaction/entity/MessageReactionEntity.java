package com.devcool.adapters.out.persistence.reaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Mapped for {@code ddl-auto=validate}; writes go through the repository's native queries. */
@Entity
@Table(name = "MESSAGE_REACTION")
@IdClass(MessageReactionEntity.Key.class)
@Getter
@Setter
public class MessageReactionEntity {

  @Id
  @Column(name = "MESSAGE_ID", nullable = false)
  private Long messageId;

  @Id
  @Column(name = "USER_ID", nullable = false)
  private Long userId;

  @Id
  @Column(name = "EMOJI", length = 16, nullable = false)
  private String emoji;

  @Column(name = "CREATED_TIME", nullable = false)
  private Instant createdTime;

  public record Key(Long messageId, Long userId, String emoji) implements Serializable {}
}
