package com.artfetch.repository;

import com.artfetch.entity.ObjectStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ObjectStorageConfigRepository extends JpaRepository<ObjectStorageConfig, Long> {
    Optional<ObjectStorageConfig> findByEnabledTrue();
}
