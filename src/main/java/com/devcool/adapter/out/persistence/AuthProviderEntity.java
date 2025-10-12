package com.devcool.adapter.out.persistence;

import com.devcool.domain.model.enums.LoginType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "AUTH_PROVIDER")
@Getter
@Setter
public class AuthProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "ID", nullable = false, unique = true)
    private Integer id;

    @Column(name = "PROVIDER", nullable = false)
    private LoginType provider;

    @Column(name = "PROVIDER_ID", nullable = false)
    private String providerId;

    @Column(name = "CREATED_TIME", nullable = false)
    private Timestamp createdTime;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "USER_ID", referencedColumnName = "ID")
    private UserEntity user;
}
