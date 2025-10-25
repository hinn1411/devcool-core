package com.devcool.adapters.persistence.adapter;

import com.devcool.adapters.persistence.mapper.UserMapper;
import com.devcool.adapters.persistence.entity.UserEntity;
import com.devcool.adapters.persistence.repository.UserRepository;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@AllArgsConstructor
public class UserAdapter implements UserPort {

    private final UserRepository repo;
    private final UserMapper mapper;

    @Override
    public Optional<User> findById(Integer id) {
        return repo.findById(id).map(mapper::toDomain);
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
}
