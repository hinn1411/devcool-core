package com.devcool.adapters.out.persistence.member.projection;

/** The read marker before and after one advance. */
public interface ReadMarkerRow {
  Long getPreviousSeq();

  Long getSeq();
}
