package com.devcool.adapters.out.persistence.channel.projection;

/** Native-query projection of the channel row read under {@code FOR NO KEY UPDATE}. */
public interface ChannelAccessLockedRow {
  String getChannelType();

  Integer getTotalOfMembers();

  Long getLeaderId();
}
