package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** {@code GET /channels/{id}/messages/search} (P3-T13). */
class ChannelMessageSearchIT extends AbstractMessageSearchIT {

  private long alice;
  private long outsider;
  private long channel;
  private String word;

  @BeforeEach
  void seed() {
    alice = user();
    outsider = user();
    channel = channel(alice);
    word = uniqueWord();
  }

  /** Done when 1. */
  @Test
  void matchesWholeWords_caseInsensitive_newestFirst_withSeq() throws Exception {
    long first = post(channel, alice, "Fix the " + word + " today");
    post(channel, alice, "nothing to see");
    post(channel, alice, word + "ing is a longer word, not a match");
    long third = postMarkdown(channel, alice, "**" + word.toUpperCase(Locale.ROOT) + "** again");
    long elsewhere = channel(alice);
    post(elsewhere, alice, word + " in another channel");

    ResultActions result =
        perform(alice, channelSearch(channel, word.toUpperCase(Locale.ROOT)))
            .andExpect(status().isOk());

    assertThat(ids(result)).containsExactly(third, first);
    assertThat(longs(result, "$.data.items[*].seq")).containsExactly(seqOf(third), seqOf(first));
    result.andExpect(jsonPath("$.data.hasMore").value(false));
  }

  /** Done when 1: no stemming. */
  @Test
  void simpleConfig_doesNotStem() throws Exception {
    long deployed = post(channel, alice, "deployed " + word);

    assertThat(ids(perform(alice, channelSearch(channel, "deploy " + word)))).isEmpty();
    assertThat(ids(perform(alice, channelSearch(channel, "deployed " + word))))
        .containsExactly(deployed);
  }

  /** Done when 2. */
  @Test
  void nonMember_is403() throws Exception {
    post(channel, alice, word);

    perform(outsider, channelSearch(channel, word)).andExpect(status().isForbidden());
  }

  @Test
  void missingChannel_is404() throws Exception {
    perform(alice, channelSearch(Long.MAX_VALUE, word)).andExpect(status().isNotFound());
  }

  @Test
  void withoutToken_is401() throws Exception {
    mvc.perform(channelSearch(channel, word)).andExpect(status().isUnauthorized());
  }

  /** Done when 3. */
  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void blankQuery_is400(String q) throws Exception {
    perform(alice, channelSearch(channel, q)).andExpect(status().isBadRequest());
  }

  @Test
  void queryOver200Characters_is400() throws Exception {
    perform(alice, channelSearch(channel, "a".repeat(201))).andExpect(status().isBadRequest());
  }

  /** Done when 4. */
  @Test
  void deletedAndMediaMessages_neverMatch() throws Exception {
    softDelete(post(channel, alice, "secret " + word));
    postMedia(channel, alice, word + ".png");

    ResultActions result = perform(alice, channelSearch(channel, word)).andExpect(status().isOk());

    assertThat(ids(result)).isEmpty();
  }

  /** Done when 5. */
  @ParameterizedTest
  @ValueSource(strings = {"\"((", "-", "&!", "a & | b", ":*", "'", "!!!", "<->", "or"})
  void junkQuery_is200_neverServerError(String q) throws Exception {
    post(channel, alice, word);

    perform(alice, channelSearch(channel, q)).andExpect(status().isOk());
  }

  /** Done when 5: operators. */
  @Test
  void websearchOperators_phraseOrAndExclude() throws Exception {
    String other = uniqueWord();
    long both = post(channel, alice, word + " " + other);
    long onlyWord = post(channel, alice, word + " alone");
    long onlyOther = post(channel, alice, other + " alone");

    assertThat(ids(perform(alice, channelSearch(channel, word + " -" + other))))
        .containsExactly(onlyWord);
    assertThat(ids(perform(alice, channelSearch(channel, word + " or " + other))))
        .containsExactly(onlyOther, onlyWord, both);
    assertThat(ids(perform(alice, channelSearch(channel, "\"" + other + " " + word + "\""))))
        .isEmpty();
  }

  /** Done when 7. */
  @Test
  void paging_beforeSeq_walksEveryHitOnce() throws Exception {
    long m1 = post(channel, alice, word + " 1");
    long m2 = post(channel, alice, word + " 2");
    post(channel, alice, "filler");
    long m3 = post(channel, alice, word + " 3");

    ResultActions page1 =
        perform(alice, channelSearch(channel, word).param("limit", "2"))
            .andExpect(jsonPath("$.data.hasMore").value(true));
    assertThat(ids(page1)).containsExactly(m3, m2);

    ResultActions page2 =
        perform(
                alice,
                channelSearch(channel, word)
                    .param("limit", "2")
                    .param("beforeSeq", String.valueOf(seqOf(m2))))
            .andExpect(jsonPath("$.data.hasMore").value(false));
    assertThat(ids(page2)).containsExactly(m1);
  }

  /** Review #4. */
  @Test
  void paging_byEchoingCursorId_walksEveryHitOnce() throws Exception {
    long m1 = post(channel, alice, word + " 1");
    post(channel(alice), alice, "bumps the global id between hits");
    long m2 = post(channel, alice, word + " 2");
    long m3 = post(channel, alice, word + " 3");

    ResultActions page1 = perform(alice, channelSearch(channel, word).param("limit", "2"));
    assertThat(ids(page1)).containsExactly(m3, m2);
    page1.andExpect(jsonPath("$.data.cursorId").value(seqOf(m2)));

    String cursor =
        JsonPath.read(page1.andReturn().getResponse().getContentAsString(), "$.data.cursorId")
            .toString();
    ResultActions page2 =
        perform(alice, channelSearch(channel, word).param("limit", "2").param("beforeSeq", cursor))
            .andExpect(jsonPath("$.data.hasMore").value(false));
    assertThat(ids(page2)).containsExactly(m1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101"})
  void limitOutOfRange_is422_likeHistory(String limit) throws Exception {
    perform(alice, channelSearch(channel, word).param("limit", limit))
        .andExpect(status().isUnprocessableEntity());
  }
}
