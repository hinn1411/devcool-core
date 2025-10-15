package com.devcool.adapter.out.adapter;

import com.devcool.adapter.out.mapper.UserMapper;
import com.devcool.adapter.out.persistence.UserEntity;
import com.devcool.adapter.out.repository.UserRepository;
import com.devcool.domain.model.User;
import com.devcool.domain.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserAdapter implements UserPort {

    private final UserRepository repo;
    private final UserMapper mapper;

    @Override
    public Optional<User> findById(Integer id) {
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.empty();
    }

    @Override
    @Transactional
    public Integer save(User user) {
        var userEntity = mapper.toEntity(user);
        UserEntity savedUser = repo.save(userEntity);
        return savedUser.getId();

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
