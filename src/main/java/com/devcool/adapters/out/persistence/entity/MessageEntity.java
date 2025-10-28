package com.devcool.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "MESSAGE")
@Getter
@Setter
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer id;

    @Column(name = "CONTENT",length = 1000, nullable = true)
    private String content;

    @Column(name = "CREATED_TIME", nullable = false)
    private Timestamp createdTime;

    @Column(name = "DELETED_TIME", nullable = true)
    private Timestamp deletedTime;

    @Column(name = "EDITED_TIME", nullable = false)
    private Timestamp editedTime;

    @OneToOne(mappedBy = "message")
    private MediaEntity media;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "CHANNEL_ID", referencedColumnName = "ID")
    private ChannelEntity channel;
}
