package com.devcool.adapters.out.persistence.auth;

import com.devcool.adapters.out.persistence.auth.entity.RefreshTokenEntity;
import com.devcool.adapters.out.persistence.auth.mapper.RefreshTokenMapper;
import com.devcool.adapters.out.persistence.auth.repository.RefreshTokenRepository;
import com.devcool.adapters.out.persistence.user.repository.UserRepository;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.port.out.AccessTokenPort;
import com.devcool.domain.auth.port.out.RefreshTokenPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor
public class AuthAdapter implements RefreshTokenPort, AccessTokenPort {
  private final UserRepository userRepo;
  private final RefreshTokenRepository refreshTokenRepo;
  private final RefreshTokenMapper refreshTokenMapper;

  @Override
  public void store(RefreshToken refreshToken) {
    RefreshTokenEntity entity = refreshTokenMapper.toEntity(refreshToken);
    refreshTokenRepo.save(entity);
  }

  @Override
  public boolean consumeIfValid(String jtiHash) {
    return refreshTokenRepo.consumeIfValid(jtiHash) > 0;
  }

  @Override
  public void deleteOldRefreshTokens(Integer userId) {
    refreshTokenRepo.deleteAllByUserId(String.valueOf(userId));
  }

  @Override
  public boolean revoke(String jtiHash) {
    return refreshTokenRepo.revoke(jtiHash) > 0;
  }

  @Override
  public boolean updateVersion(Integer userId) {
    return userRepo.updateTokenVersion(userId) > 0;
  }
}
