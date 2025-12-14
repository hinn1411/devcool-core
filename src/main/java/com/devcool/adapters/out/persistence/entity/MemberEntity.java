package com.devcool.adapters.out.persistence.entity;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.model.enums.MemberType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "MEMBER")
@Getter
@Setter
public class MemberEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "ID", nullable = false, unique = true)
  private Integer id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "USER_ID", referencedColumnName = "ID", nullable = false)
  private UserEntity user;

  @ManyToOne(optional = false)
  @JoinColumn(name = "CHANNEL_ID", referencedColumnName = "ID", nullable = false)
  private ChannelEntity channel;

  @Column(name = "ROLE", nullable = false)
  @Enumerated(EnumType.STRING)
  private MemberType role;

  @Column(name = "JOINED_TIME", nullable = false)
  private Instant joinedTime;
}
