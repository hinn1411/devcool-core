package com.devcool.adapters.out.persistence.message.entity;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.domain.chat.model.enums.ContentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "MESSAGE")
@Getter
@Setter
public class MessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  private Integer id;

  @Column(name = "SENDER_USER_ID", nullable = false)
  private Integer senderUserId;

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
  @JoinColumn(name = "CHANNEL_ID", referencedColumnName = "ID")
  private ChannelEntity channel;
}
