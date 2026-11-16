package com.devcool.adapters.out.persistence.channel.projection;

public interface ChannelAccessLockedRow {
  String getChannelType();

  Integer getTotalOfMembers();

  Long getLeaderId();
}
