package com.devcool.adapter.out.persistence;

import com.devcool.domain.model.enums.MemberType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(name = "MEMBER")
@Getter
@Setter
public class MemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "ID", nullable = false, unique = true)
    private Integer id;

    @Column(name = "USER_ID", nullable = false)
    private String userId;

    @Column(name = "ROLE", nullable = false)
    @Enumerated
    private MemberType role;

    @Column(name = "JOINED_TIME", nullable = false)
    private Timestamp joinedTime;

    @ManyToMany
    @JoinTable(
            name = "MEMBERS_OF_CHANNELS",
            joinColumns = @JoinColumn(name = "MEMBER_ID", referencedColumnName = "ID"),
            inverseJoinColumns = @JoinColumn(name = "CHANNEL_ID", referencedColumnName = "ID")
    )
    private List<ChannelEntity> channels;
}
