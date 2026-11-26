package com.devcool.domain.member.model;

/** A member's read marker before and after one advance; equal when nothing moved. */
public record ReadMarker(long previousSeq, long seq) {

  public boolean advanced() {
    return seq > previousSeq;
  }
}
