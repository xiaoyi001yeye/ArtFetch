package com.artfetch.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.artfetch.auth.support.RoleCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataScopeService {

    public Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public boolean isAdmin() {
        return StpUtil.hasRole(RoleCodes.ADMIN);
    }

    public boolean hasPermission(String permissionCode) {
        return StpUtil.hasPermission(permissionCode);
    }

    public void requirePermission(String permissionCode) {
        StpUtil.checkPermission(permissionCode);
    }
}
