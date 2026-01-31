package com.devcool.adapters.out.persistence.message.repository;

import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, Integer> {
}
