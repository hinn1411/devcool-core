package com.devcool.domain.channel.model;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.user.model.User;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class Channel {
    private Integer id;
    private String name;
    private BoundaryType boundaryType;
    private Integer totalOfMembers;
    private Instant expiredTime;
    private ChannelType channelType;
    private User creator;
    private User leader;
}
