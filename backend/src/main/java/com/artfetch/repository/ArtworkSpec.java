package com.artfetch.repository;

import com.artfetch.entity.Artwork;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ArtworkSpec {

    public static Specification<Artwork> search(Long taskId, String keyword, String artist, String year) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (taskId != null) {
                predicates.add(cb.equal(root.get("task").get("id"), taskId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("artist")), pattern)
                ));
            }
            if (artist != null && !artist.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("artist")),
                        "%" + artist.toLowerCase() + "%"));
            }
            if (year != null && !year.isBlank()) {
                predicates.add(cb.equal(root.get("year"), year));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
