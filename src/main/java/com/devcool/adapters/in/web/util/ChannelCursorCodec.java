package com.devcool.adapters.in.web.util;

import com.devcool.domain.channel.model.ChannelCursor;

/** The opaque {@code cursor} query param and response field of the channel list (P3-T10). */
public final class ChannelCursorCodec {

  private ChannelCursorCodec() {}

  public static String encode(ChannelCursor cursor) {
    throw new UnsupportedOperationException();
  }

  /** Throws {@code InvalidChannelCursorException} for anything {@link #encode} didn't produce. */
  public static ChannelCursor decode(String token) {
    throw new UnsupportedOperationException();
  }
}
