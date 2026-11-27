package com.devcool.domain.member.model;

/** A read marker before and after one advance. */
public record ReadMarker(long previousSeq, long seq) {

  public boolean advanced() {
    return seq > previousSeq;
  }
}
