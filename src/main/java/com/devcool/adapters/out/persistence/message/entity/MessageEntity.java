package com.devcool.adapters.out.persistence.message.entity;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.media.entity.MediaEntity;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.chat.model.enums.ContentType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "MESSAGE")
@Getter
@Setter
public class MessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "ID", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "USER_ID",
      referencedColumnName = "ID",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_message_user"))
  private UserEntity user;

  @Column(name = "CONTENT", length = 1000, nullable = true)
  private String content;

  @Column(name = "CONTENT_TYPE", nullable = false)
  @Enumerated(EnumType.STRING)
  private ContentType contentType;

  @Column(name = "CREATED_TIME", nullable = false)
  private Instant createdTime;

  @Column(name = "DELETED_TIME", nullable = true)
  private Instant deletedTime;

  @Column(name = "EDITED_TIME", nullable = true)
  private Instant editedTime;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "CHANNEL_ID",
      referencedColumnName = "ID",
      foreignKey = @ForeignKey(name = "fk_message_channel"))
  private ChannelEntity channel;

  /** Position in the channel (ADR-0012), unique per channel. */
  @Column(name = "SEQ", nullable = false)
  private Long seq;

  /** The sender's idempotency key, unique per sender (uk_message_user_client_msg). */
  @Column(name = "CLIENT_MSG_ID")
  private UUID clientMsgId;

  @OneToOne(
      mappedBy = "message",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private MediaEntity media;
}
