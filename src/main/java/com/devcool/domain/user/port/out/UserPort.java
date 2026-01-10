package com.devcool.domain.user.port.out;

import com.devcool.domain.user.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserPort {
  Optional<User> findById(Integer id);

  List<User> findByIds(Collection<Integer> ids);

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  boolean existsByUsername(String username);

  Integer save(User user); // create or update, return id

  boolean updatePassword(Integer id, String newHash);

  boolean remove(Integer id);

  Set<Integer> findExistingUserIds(Set<Integer> userIds);
}
