package com.devcool.adapters.out.persistence.auth;

import com.devcool.adapters.out.persistence.auth.entity.RefreshTokenEntity;
import com.devcool.adapters.out.persistence.auth.mapper.RefreshTokenMapper;
import com.devcool.adapters.out.persistence.auth.repository.RefreshTokenRepository;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.out.RefreshTokenStorePort;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@AllArgsConstructor
public class RefreshTokenAdapter implements RefreshTokenStorePort {
  private static final Logger log = LoggerFactory.getLogger(RefreshTokenAdapter.class);
  private final RefreshTokenRepository repo;
  private final RefreshTokenMapper mapper;

  @Override
  @Transactional
  public void store(RefreshToken refreshToken) {
    RefreshTokenEntity entity = mapper.toEntity(refreshToken);
    repo.save(entity);
  }

  @Override
  @Transactional
  public boolean consumeIfValid(String jtiHash) {
    return repo.consumeIfValid(jtiHash) > 0;
  }

  @Override
  @Transactional
  public void deleteOldRefreshTokens(Integer userId) {
    repo.deleteAllByUserId(String.valueOf(userId));
  }

  @Override
  @Transactional
  public boolean revoke(String jtiHash) {
    return repo.revoke(jtiHash) > 0;
  }
}
