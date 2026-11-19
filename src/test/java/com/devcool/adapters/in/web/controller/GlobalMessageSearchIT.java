package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** {@code GET /messages/search} (P3-T13). */
class GlobalMessageSearchIT extends AbstractMessageSearchIT {

  private long alice;
  private long bob;
  private String word;

  @BeforeEach
  void seed() {
    alice = user();
    bob = user();
    word = uniqueWord();
  }

  /** Done when 6. */
  @Test
  void onlyCurrentMemberships_leftAndForeignChannelsExcluded() throws Exception {
    long mine = channel(alice);
    long shared = channel(bob, alice);
    long foreign = channel(bob);
    long left = channel(bob, alice);

    long inMine = post(mine, alice, word + " mine");
    post(foreign, bob, word + " foreign");
    post(left, alice, word + " before leaving");
    long inShared = post(shared, bob, word + " shared");
    leave(left, alice);

    ResultActions result = perform(alice, globalSearch(word)).andExpect(status().isOk());

    assertThat(ids(result)).containsExactly(inShared, inMine);
    assertThat(longs(result, "$.data.items[*].channelId")).containsExactly(shared, mine);
  }

  @Test
  void callerWithNoChannels_getsAnEmptyPage() throws Exception {
    post(channel(bob), bob, word);

    assertThat(ids(perform(user(), globalSearch(word)).andExpect(status().isOk()))).isEmpty();
  }

  @Test
  void withoutToken_is401() throws Exception {
    mvc.perform(globalSearch(word)).andExpect(status().isUnauthorized());
  }

  /** Done when 3. */
  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void blankQuery_is400(String q) throws Exception {
    perform(alice, globalSearch(q)).andExpect(status().isBadRequest());
  }

  @Test
  void queryOver200Characters_is400() throws Exception {
    perform(alice, globalSearch("a".repeat(201))).andExpect(status().isBadRequest());
  }

  /** Done when 4. */
  @Test
  void deletedAndMediaMessages_neverMatch() throws Exception {
    long mine = channel(alice);
    softDelete(post(mine, alice, "secret " + word));
    postMedia(mine, alice, word + ".mp4");

    assertThat(ids(perform(alice, globalSearch(word)).andExpect(status().isOk()))).isEmpty();
  }

  /** Done when 5. */
  @ParameterizedTest
  @ValueSource(strings = {"\"((", "-", "&!", ":*", "'"})
  void junkQuery_is200_neverServerError(String q) throws Exception {
    post(channel(alice), alice, word);

    perform(alice, globalSearch(q)).andExpect(status().isOk());
  }

  /** Done when 7. */
  @Test
  void paging_beforeId_walksEveryHitOnce() throws Exception {
    long a = channel(alice);
    long b = channel(bob, alice);
    long m1 = post(a, alice, word);
    long m2 = post(b, bob, word);
    long m3 = post(a, alice, word);

    ResultActions page1 =
        perform(alice, globalSearch(word).param("limit", "2"))
            .andExpect(jsonPath("$.data.hasMore").value(true));
    assertThat(ids(page1)).containsExactly(m3, m2);

    ResultActions page2 =
        perform(alice, globalSearch(word).param("limit", "2").param("beforeId", String.valueOf(m2)))
            .andExpect(jsonPath("$.data.hasMore").value(false));
    assertThat(ids(page2)).containsExactly(m1);
  }

  /** Review #4. */
  @Test
  void cursorId_isTheLastId_forBeforeId() throws Exception {
    long a = channel(alice);
    post(a, alice, word);
    long m2 = post(a, alice, word);
    post(a, alice, word);

    perform(alice, globalSearch(word).param("limit", "2"))
        .andExpect(jsonPath("$.data.cursorId").value(m2));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101"})
  void limitOutOfRange_is422_likeHistory(String limit) throws Exception {
    perform(alice, globalSearch(word).param("limit", limit))
        .andExpect(status().isUnprocessableEntity());
  }
}
