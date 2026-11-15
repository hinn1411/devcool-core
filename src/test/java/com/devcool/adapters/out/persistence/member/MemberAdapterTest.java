package com.devcool.adapters.out.persistence.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import com.devcool.adapters.out.persistence.member.mapper.MemberMapper;
import com.devcool.adapters.out.persistence.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** addMembers guards around the native catch-up that an IT can't provoke (P3-T10 loop 3). */
@ExtendWith(MockitoExtension.class)
class MemberAdapterTest {

  private static final long CHANNEL = 1L;

  @Mock private MemberMapper mapper;
  @Mock private MemberRepository repo;
  @Mock private EntityManager em;

  private MemberAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new MemberAdapter(mapper, repo, em);
  }

  @Test
  void addMembers_noIds_isANoOp_andRunsNoSql() {
    assertThat(adapter.addMembers(CHANNEL, Set.of())).isFalse();

    verifyNoInteractions(repo, em);
  }

  @Test
  void addMembers_catchUpMissingARow_failsSoTheTransactionRollsBack() {
    when(repo.saveAll(anyList()))
        .thenReturn(List.of(MemberEntity.builder().build(), MemberEntity.builder().build()));
    when(repo.catchUpMembers(anyLong(), anyCollection())).thenReturn(1);

    assertThatThrownBy(() -> adapter.addMembers(CHANNEL, Set.of(2L, 3L)))
        .isInstanceOf(IllegalStateException.class);
  }
}
