package com.devcool.adapters.out.persistence.media.entity;

import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "MEDIA")
@Getter
@Setter
public class MediaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "ID", nullable = false, unique = true)
  private Integer id;

  @Column(name = "path", length = 500, nullable = false)
  private String path;

  @Column(name = "CREATED_TIME", nullable = false)
  private Instant createdTime;

  @OneToOne(cascade = CascadeType.MERGE, orphanRemoval = true)
  @JoinColumn(name = "MESSAGE_ID", referencedColumnName = "ID")
  private MessageEntity message;
}
