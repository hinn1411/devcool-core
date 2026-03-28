package com.devcool.adapters.out.persistence.media.repository;

import com.devcool.adapters.out.persistence.media.entity.MediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<MediaEntity, Integer> {

}
