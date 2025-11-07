package com.devcool.adapters.out.persistence.entity;

import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TOPIC")
@Getter
@Setter
public class TopicEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "ID", nullable = false, unique = true)
  private Integer id;

  @Column(name = "NAME", nullable = false)
  private String name;

  @ManyToMany
  @JoinTable(
      name = "TOPICS_OF_CHANNELS",
      joinColumns = @JoinColumn(name = "TOPIC_ID", referencedColumnName = "ID"),
      inverseJoinColumns = @JoinColumn(name = "CHANNEL_ID", referencedColumnName = "ID"))
  private List<ChannelEntity> channels;
}
