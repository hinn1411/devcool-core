package com.devcool.adapters.out.persistence.entity;

import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import jakarta.persistence.*;
import java.sql.Timestamp;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "FRIEND_REQUEST",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_friend_request_request_id", columnNames = "REQUEST_ID"))
@Getter
@Setter
public class FriendRequestEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "ID", nullable = false)
  private Integer id;

  @Column(name = "REQUEST_ID", nullable = false)
  private String requestId;

  @Column(name = "CREATED_TIME", nullable = false)
  private Timestamp createdTime;

  @Column(name = "PROCESSED_TIME", nullable = false)
  private Timestamp processedTime;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  // Create SENDER_ID column in FRIEND_REQUEST table
  // which reference to ID column of USER table
  @JoinColumn(
      name = "SENDER_ID",
      referencedColumnName = "ID",
      foreignKey = @ForeignKey(name = "fk_friend_request_sender"))
  private UserEntity sender;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  // Create RECEIVER_ID column in FRIEND_REQUEST table
  // which reference to ID column of USER table
  @JoinColumn(
      name = "RECEIVER_ID",
      referencedColumnName = "ID",
      foreignKey = @ForeignKey(name = "fk_friend_request_receiver"))
  private UserEntity receiver;
}
