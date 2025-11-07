package com.devcool.adapters.out.persistence.auth;

import com.devcool.adapters.out.persistence.auth.entity.RefreshTokenEntity;
import com.devcool.adapters.out.persistence.auth.mapper.RefreshTokenMapper;
import com.devcool.adapters.out.persistence.auth.repository.RefreshTokenRepository;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.out.RefreshTokenStorePort;
import java.time.Instant;
import java.util.Objects;
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
  @Transactional(readOnly = true)
  public boolean consume(String jtiHash) {
    RefreshTokenEntity refreshTokenEntity =
        repo.findById(jtiHash)
            .orElseGet(
                () -> {
                  log.info("Given jti not found");
                  return null;
                });
    if (Objects.isNull(refreshTokenEntity)
        || refreshTokenEntity.getExpiredTime().isBefore(Instant.now())
        || Objects.nonNull(refreshTokenEntity.getConsumedTime())) {
      return false;
    }
    refreshTokenEntity.setConsumedTime(Instant.now());
    return true;
  }
}
