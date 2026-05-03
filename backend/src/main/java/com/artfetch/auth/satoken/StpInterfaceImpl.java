package com.artfetch.auth.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.artfetch.auth.service.PermissionLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final PermissionLoadService permissionLoadService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permissionLoadService.findPermissionCodesByUserId(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return permissionLoadService.findRoleCodesByUserId(Long.valueOf(loginId.toString()));
    }
}
