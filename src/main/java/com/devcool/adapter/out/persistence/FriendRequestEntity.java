package com.devcool.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "FRIEND_REQUEST")
@Getter
@Setter
public class FriendRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "ID", nullable = false, unique = true)
    private Integer id;

    @Column(name = "REQUEST_ID", nullable = false, unique = true)
    private String requestId;

    @Column(name = "CREATED_TIME", nullable = false)
    private Timestamp createdTime;

    @Column(name = "PROCESSED_TIME", nullable = false)
    private Timestamp processedTime;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    // Create SENDER_ID column in FRIEND_REQUEST table
    // which reference to ID column of USER table
    @JoinColumn(name = "SENDER_ID", referencedColumnName = "ID")
    private UserEntity sender;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    // Create RECEIVER_ID column in FRIEND_REQUEST table
    // which reference to ID column of USER table
    @JoinColumn(name = "RECEIVER_ID", referencedColumnName = "ID")
    private UserEntity receiver;


}
