package com.devcool.adapters.out.persistence.user;

import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.adapters.out.persistence.user.mapper.UserMapper;
import com.devcool.adapters.out.persistence.user.repository.UserRepository;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@AllArgsConstructor
public class UserAdapter implements UserPort {

  private final UserRepository repo;
  private final UserMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findById(Integer id) {
    return repo.findById(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<User> findByIds(Collection<Integer> ids) {
    return repo.findByIdIn(ids).stream().map(mapper::toDomain).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByEmail(String email) {
    return repo.existsByEmail(email);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return repo.findByEmail(email).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByUsername(String username) {
    return repo.existsByUsername(username);
  }

  @Override
  @Transactional
  public Integer save(User user) {
    UserEntity saved = repo.save(mapper.toEntity(user));
    return saved.getId();
  }

  @Override
  @Transactional
  public boolean updatePassword(Integer id, String newHash) {
    return false;
  }

  @Override
  @Transactional
  public boolean remove(Integer id) {
    return false;
  }

  @Override
  public Set<Integer> findExistingUserIds(Set<Integer> userIds) {
    return repo.findExistingIds(userIds);
  }

}
