package com.artfetch.repository;

import com.artfetch.entity.Artwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArtworkRepository extends JpaRepository<Artwork, Long>,
        JpaSpecificationExecutor<Artwork> {

    Optional<Artwork> findByTaskIdAndExternalId(Long taskId, String externalId);

    boolean existsByTaskIdAndExternalId(Long taskId, String externalId);

    Optional<Artwork> findByExternalId(String externalId);

    long countByTaskId(Long taskId);
}
