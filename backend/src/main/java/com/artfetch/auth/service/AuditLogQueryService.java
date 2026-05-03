package com.artfetch.auth.service;

import com.artfetch.auth.dto.AuditLogDto;
import com.artfetch.auth.entity.AuditLog;
import com.artfetch.auth.repository.AuditLogRepository;
import com.artfetch.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PageResult<AuditLogDto> listLogs(String username, String action, Boolean success, int page, int size) {
        Specification<AuditLog> spec = Specification.where(null);
        if (username != null && !username.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("username")), "%" + username.trim().toLowerCase() + "%"));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("action")), "%" + action.trim().toLowerCase() + "%"));
        }
        if (success != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("success"), success));
        }
        return PageResult.of(
                auditLogRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                AuditLogDto::from
        );
    }
}
