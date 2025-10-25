package com.devcool.adapters.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
            inverseJoinColumns = @JoinColumn(name = "CHANNEL_ID", referencedColumnName = "ID")
    )
    private List<ChannelEntity> channels;
}
