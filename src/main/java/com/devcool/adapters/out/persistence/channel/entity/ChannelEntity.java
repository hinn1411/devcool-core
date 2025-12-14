package com.devcool.adapters.out.persistence.channel.entity;

import com.devcool.adapters.out.persistence.entity.MemberEntity;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "CHANNEL")
@Getter
@Setter
public class ChannelEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "ID", nullable = false, unique = true)
  private Integer id;

  @Column(name = "NAME", nullable = false)
  private String name;

  @Column(name = "BOUNDARY", nullable = false)
  @Enumerated(EnumType.STRING)
  private BoundaryType boundaryType;

  @Column(name = "TOTAL_OF_MEMBERS", nullable = false)
  private Integer totalOfMembers;

  @Column(name = "EXPIRED_TIME")
  private Instant expiredTime;

  @Column(name = "CHANNEL_TYPE", nullable = false)
  @Enumerated(EnumType.STRING)
  private ChannelType channelType;

  @ManyToOne
  @JoinColumn(name = "CREATOR_ID", referencedColumnName = "ID")
  private UserEntity creator;

  @ManyToOne
  @JoinColumn(name = "LEADER_ID", referencedColumnName = "ID")
  private UserEntity leader;

  @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<MemberEntity> members = new ArrayList<>();
}
