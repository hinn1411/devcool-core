package com.devcool.adapter.out.adapter;

import com.devcool.adapter.out.persistence.UserEntity;
import com.devcool.adapter.out.repository.UserRepository;
import com.devcool.domain.port.out.UserRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class UserAdapter implements UserRepositoryPort {

    private final UserRepository repo;

    public UserAdapter(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<UserEntity> findById(Integer id) {
        return Optional.empty();
    }

    @Override
    public Optional<UserEntity> findByEmail(String email) {
        return Optional.empty();
    }

    @Override
    @Transactional
    public Integer save(UserEntity user) {
        return 0;
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
