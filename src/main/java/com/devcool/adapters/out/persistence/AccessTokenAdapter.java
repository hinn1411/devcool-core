package com.devcool.adapters.out.persistence;

import com.devcool.adapters.out.persistence.user.repository.UserRepository;
import com.devcool.domain.auth.out.AccessTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AccessTokenAdapter implements AccessTokenPort {
  private final UserRepository repo;

  @Override
  @Transactional
  public boolean updateVersion(Integer userId) {
    return repo.updateTokenVersion(userId) > 0;
  }
}
