package com.devcool.adapter.out.persistence;

import com.devcool.domain.model.enums.Role;
import com.devcool.domain.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity // Mark class as bean that can be persisted by Hibernate
@Table(name = "APP_USER") // Table mapping & constraints
@Getter
@Setter
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "ID", nullable = false, unique = true)
    private Integer id;

    @Column(name = "USER_NAME", length = 20, unique = true)
    private String username;

    @Column(name = "PASSWORD", length = 255)
    private String password;

    @Column(name = "EMAIL", length = 50, unique = true)
    private String email;

    @Column(name = "EMAIL_VERIFIED")
    private Boolean isEmailVerified;

    @Column(name = "NAME", length = 50)
    private String name;

    @Column(name = "AVATAR", length = 255)
    private String avatar;

    @Column(name = "ROLE", nullable = false)
    @Enumerated(value = EnumType.STRING)
    private Role role;

    @Column(name = "STATUS", nullable = false)
    @Enumerated(value = EnumType.STRING)
    private UserStatus status;

    @Column(name = "LAST_LOGIN_TIME")
    private Instant lastLoginTime;


//    Bi-directional mapping
//    @OneToMany(mappedBy = "senders", fetch = FetchType.LAZY)
//    private List<FriendRequest> sendingRequests;
//
//    @OneToMany(mappedBy = "receivers", fetch = FetchType.LAZY)
//    private List<FriendRequest> receivedRequests;

}
