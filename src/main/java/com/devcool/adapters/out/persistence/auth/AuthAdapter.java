package com.devcool.adapters.out.persistence.auth;

import com.devcool.adapters.out.persistence.user.mapper.UserMapper;
import com.devcool.adapters.out.persistence.user.repository.UserRepository;
import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.user.model.User;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor
public class AuthAdapter implements LoadUserPort {
  private final UserRepository repo;
  private final UserMapper mapper;

  @Override
  public Optional<User> loadByUsername(String username) {
    return repo.findByUsername(username).map(mapper::toDomain);
  }

  @Override
  public Optional<User> loadById(Integer id) {
    return repo.findById(id).map(mapper::toDomain);
  }
}
