package com.devcool.adapters.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.support.AbstractWsIT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * UNSUBSCRIBE and remove/leave cut off live pushes (P3-T16), over a real socket. "No push" is
 * proven without sleeping: after the cut-off message, a sentinel is sent on a channel the listener
 * still follows, and the listener's next frame must be that sentinel.
 */
class WsUnsubscribeIT extends AbstractWsIT {

  @Test
  void unsubscribedConnectionStopsReceivingPushes_andKeepsItsOtherChannels() throws Exception {
    Account sender = signUp("us");
    Account listener = signUp("ul");
    Long left = createLounge(sender, listener);
    Long kept = createLounge(sender, listener);
    Client listening = connect(listener);
    subscribe(listening, left);
    subscribe(listening, kept);
    Client sending = connect(sender);

    unsubscribe(listening, left);
    sendAndAwaitAck(sending, left, "after-unsubscribe");
    sendAndAwaitAck(sending, kept, "sentinel");

    assertThat(awaitFrame(listening)).contains("sentinel").doesNotContain("after-unsubscribe");
    sendAndAwaitAck(listening, kept, "listener can still send");
  }

  @Test
  void removedMemberStopsReceivingPushesOnEveryTab() throws Exception {
    Account owner = signUp("ro");
    Account bob = signUp("rb");
    Long removedFrom = createLounge(owner, bob);
    Long kept = createLounge(owner, bob);
    List<Client> tabs = List.of(connect(bob), connect(bob));
    for (Client tab : tabs) {
      subscribe(tab, removedFrom);
      subscribe(tab, kept);
    }
    Client sending = connect(owner);

    mvc.perform(
            delete("/api/v1/channels/{c}/members/{u}", removedFrom, bob.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
        .andExpect(status().isNoContent());
    sendAndAwaitAck(sending, removedFrom, "after-remove");
    sendAndAwaitAck(sending, kept, "sentinel");

    for (Client tab : tabs) {
      assertThat(awaitFrame(tab)).contains("sentinel").doesNotContain("after-remove");
    }
  }

  @Test
  void memberWhoLeftStopsReceivingPushes() throws Exception {
    Account owner = signUp("lo");
    Account bob = signUp("lb");
    Long leftChannel = createLounge(owner, bob);
    Long kept = createLounge(owner, bob);
    Client tab = connect(bob);
    subscribe(tab, leftChannel);
    subscribe(tab, kept);
    Client sending = connect(owner);

    mvc.perform(
            delete("/api/v1/channels/{c}/members/me", leftChannel)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob.accessToken()))
        .andExpect(status().isNoContent());
    sendAndAwaitAck(sending, leftChannel, "after-leave");
    sendAndAwaitAck(sending, kept, "sentinel");

    assertThat(awaitFrame(tab)).contains("sentinel").doesNotContain("after-leave");
  }
}
