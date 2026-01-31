package com.devcool.adapters.out.persistence.entity;

import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import jakarta.persistence.*;
import java.sql.Timestamp;
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
  private String id;

  @Column(name = "URL", length = 500, nullable = false)
  private String url;

  @Column(name = "FILE_NAME", length = 50, nullable = false)
  private String fileName;

  @Column(name = "MIME_TYPE", nullable = false)
  private String mimeType;

  @Column(name = "CREATED_TIME", nullable = false)
  private Timestamp createdTime;

  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "MESSAGE_ID", referencedColumnName = "ID")
  private MessageEntity message;
}
