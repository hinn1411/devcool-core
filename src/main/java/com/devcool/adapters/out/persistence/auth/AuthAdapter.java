package com.devcool.adapters.out.persistence.auth;

import com.devcool.adapters.out.persistence.auth.entity.RefreshTokenEntity;
import com.devcool.adapters.out.persistence.auth.mapper.RefreshTokenMapper;
import com.devcool.adapters.out.persistence.auth.repository.RefreshTokenRepository;
import com.devcool.adapters.out.persistence.user.mapper.UserMapper;
import com.devcool.adapters.out.persistence.user.repository.UserRepository;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.port.out.AccessTokenPort;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.RefreshTokenStorePort;
import com.devcool.domain.user.model.User;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@AllArgsConstructor
public class AuthAdapter implements LoadUserPort, RefreshTokenStorePort, AccessTokenPort {
  private final UserRepository userRepo;
  private final UserMapper userMapper;
  private final RefreshTokenRepository refreshTokenRepo;
  private final RefreshTokenMapper refreshTokenMapper;

  @Override
  public Optional<User> loadByUsername(String username) {
    return userRepo.findByUsername(username).map(userMapper::toDomain);
  }

  @Override
  public Optional<User> loadById(Integer id) {
    return userRepo.findById(id).map(userMapper::toDomain);
  }

  @Override
  public List<User> loadByIds(List<Integer> ids) {
    if (Objects.isNull(ids) || ids.isEmpty()) {
      return List.of();
    }

    return userRepo.findByIdIn(ids).stream().map(userMapper::toDomain).toList();
  }

  @Override
  @Transactional
  public void store(RefreshToken refreshToken) {
    RefreshTokenEntity entity = refreshTokenMapper.toEntity(refreshToken);
    refreshTokenRepo.save(entity);
  }

  @Override
  @Transactional
  public boolean consumeIfValid(String jtiHash) {
    return refreshTokenRepo.consumeIfValid(jtiHash) > 0;
  }

  @Override
  @Transactional
  public void deleteOldRefreshTokens(Integer userId) {
    refreshTokenRepo.deleteAllByUserId(String.valueOf(userId));
  }

  @Override
  @Transactional
  public boolean revoke(String jtiHash) {
    return refreshTokenRepo.revoke(jtiHash) > 0;
  }

  @Override
  @Transactional
  public boolean updateVersion(Integer userId) {
    return userRepo.updateTokenVersion(userId) > 0;
  }

}
