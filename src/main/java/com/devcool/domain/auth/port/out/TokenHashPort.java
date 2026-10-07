package com.devcool.domain.auth.port.out;

/** One-way hash of a token id, so the refresh-token table never stores a usable jti. */
public interface TokenHashPort {
  String hash(String tokenId);
}
