package com.devcool.adapters.out.persistence.media.entity;

import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "MEDIA",
    uniqueConstraints = @UniqueConstraint(name = "uk_media_message", columnNames = "MESSAGE_ID"))
@Getter
@Setter
public class MediaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "ID", nullable = false)
  private Integer id;

  @Column(name = "path", length = 500, nullable = false)
  private String path;

  @Column(name = "CREATED_TIME", nullable = false)
  private Instant createdTime;

  @OneToOne
  @JoinColumn(
      name = "MESSAGE_ID",
      referencedColumnName = "ID",
      foreignKey = @ForeignKey(name = "fk_media_message"))
  private MessageEntity message;
}
