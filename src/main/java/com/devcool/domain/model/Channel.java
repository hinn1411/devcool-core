package com.devcool.domain.model;

import com.devcool.domain.model.enums.BoundaryType;
import com.devcool.domain.model.enums.ChannelType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "CHANNEL")
@Getter
@Setter
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "ID", nullable = false, unique = true)
    private Integer id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "BOUNDARY", nullable = false)
    @Enumerated
    private BoundaryType boundary;

    @Column(name = "TOTAL_OF_MEMBERS", nullable = false)
    private Integer totalOfMembers;

    @Column(name = "EXPIRED_TIME")
    private Timestamp expiredTime;

    @Column(name = "CHANNEL_TYPE", nullable = false)
    @Enumerated
    private ChannelType channelType;

    @ManyToOne
    @JoinColumn(name = "CREATOR_ID", referencedColumnName = "ID")
    private AppUser creator;

    @ManyToOne
    @JoinColumn(name = "LEADER_ID", referencedColumnName = "ID")
    private AppUser leader;
}
