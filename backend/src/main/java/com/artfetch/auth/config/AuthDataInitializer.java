package com.artfetch.auth.config;

import com.artfetch.auth.entity.*;
import com.artfetch.auth.repository.AuthPermissionRepository;
import com.artfetch.auth.repository.AuthRoleRepository;
import com.artfetch.auth.repository.AuthUserRepository;
import com.artfetch.auth.service.PasswordService;
import com.artfetch.auth.support.RoleCodes;
import com.artfetch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthDataInitializer implements ApplicationRunner {

    private final AuthPermissionRepository permissionRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthUserRepository userRepository;
    private final PasswordService passwordService;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, AuthPermission> permissions = upsertPermissions();
        AuthRole admin = upsertRole(RoleCodes.ADMIN, "系统管理员", "拥有系统全部权限", true);
        AuthRole expert = upsertRole(RoleCodes.EXPERT, "专家", "参与分配给自己的评估任务", true);
        AuthRole auditor = upsertRole(RoleCodes.AUDITOR, "审核人", "审核评估项目和专家评估结果", true);

        admin.setPermissions(new LinkedHashSet<>(permissions.values()));
        expert.setPermissions(resolvePermissions(permissions, List.of(
                "artwork:image:view",
                "evaluation-review:assigned:view",
                "evaluation-review:own:view",
                "evaluation-review:own:save",
                "evaluation-review:own:submit",
                "evaluation-review:own:resubmit"
        )));
        auditor.setPermissions(resolvePermissions(permissions, List.of(
                "evaluation:view",
                "evaluation:result:view",
                "evaluation-audit:view",
                "evaluation-audit:approve",
                "evaluation-audit:reject-review",
                "evaluation-audit:history:view"
        )));
        roleRepository.saveAll(List.of(admin, expert, auditor));
        createDefaultAdminIfNeeded(admin);
    }

    private Map<String, AuthPermission> upsertPermissions() {
        List<PermissionSeed> seeds = List.of(
                seed("task:view", "查看任务", "TASK", "查看任务列表、任务详情"),
                seed("task:create", "创建任务", "TASK", "创建检索、图片、成交价等任务"),
                seed("task:start", "启动任务", "TASK", "启动任务"),
                seed("task:pause", "暂停任务", "TASK", "暂停任务"),
                seed("task:resume", "恢复任务", "TASK", "恢复任务"),
                seed("task:cancel", "取消任务", "TASK", "取消任务"),
                seed("task:delete", "删除任务", "TASK", "删除任务"),
                seed("task:failure:view", "查看失败记录", "TASK", "查看任务失败记录"),
                seed("task:failure:retry", "重试失败记录", "TASK", "重试失败记录"),
                seed("artwork:view", "查看艺术品", "ARTWORK", "查看艺术品列表、详情"),
                seed("artwork:image:view", "查看图片", "ARTWORK", "查看原图、高清图"),
                seed("artwork:image:redownload", "重新下载图片", "ARTWORK", "重新下载原图或高清图"),
                seed("artwork:transaction-price:supplement", "补充成交价", "ARTWORK", "单件补充成交价"),
                seed("artwork:export", "导出艺术品", "ARTWORK", "导出 Excel"),
                seed("evaluation-metric:view", "查看评估指标", "EVALUATION", "查看指标库"),
                seed("evaluation-metric:create", "创建评估指标", "EVALUATION", "新建指标定义"),
                seed("evaluation-metric:update", "编辑评估指标", "EVALUATION", "编辑指标定义"),
                seed("evaluation-metric:disable", "停用评估指标", "EVALUATION", "停用指标定义"),
                seed("evaluation-template:view", "查看指标模板", "EVALUATION", "查看模板"),
                seed("evaluation-template:create", "创建指标模板", "EVALUATION", "新建模板"),
                seed("evaluation-template:update", "编辑指标模板", "EVALUATION", "编辑模板"),
                seed("evaluation-template:disable", "停用指标模板", "EVALUATION", "停用模板"),
                seed("evaluation:view", "查看评估项目", "EVALUATION", "查看评估项目列表、详情"),
                seed("evaluation:create", "创建评估项目", "EVALUATION", "新建评估项目"),
                seed("evaluation:update", "编辑评估项目", "EVALUATION", "编辑项目基本信息、艺术品、指标、专家"),
                seed("evaluation:delete", "删除评估项目", "EVALUATION", "删除草稿或未开始项目"),
                seed("evaluation:publish", "发布评估项目", "EVALUATION", "发布评估项目并锁定配置，允许专家开始评估"),
                seed("evaluation:submit-review", "提交审核", "EVALUATION", "将评估项目提交审核"),
                seed("evaluation:result:view", "查看评估结果", "EVALUATION", "查看多专家评估结果"),
                seed("evaluation-review:assigned:view", "查看我的评估", "EVALUATION_REVIEW", "查看分配给自己的评估项目"),
                seed("evaluation-review:own:view", "查看自己的评估", "EVALUATION_REVIEW", "查看自己的专家评估记录"),
                seed("evaluation-review:own:save", "保存自己的评估", "EVALUATION_REVIEW", "保存草稿"),
                seed("evaluation-review:own:submit", "提交自己的评估", "EVALUATION_REVIEW", "提交专家评估"),
                seed("evaluation-review:own:resubmit", "重新提交被驳回评估", "EVALUATION_REVIEW", "修改并重新提交被驳回的单条评估"),
                seed("evaluation-audit:view", "查看待审核项目", "EVALUATION_AUDIT", "查看审核页"),
                seed("evaluation-audit:approve", "审核通过", "EVALUATION_AUDIT", "审核通过整个项目"),
                seed("evaluation-audit:reject-review", "驳回单条专家评估", "EVALUATION_AUDIT", "驳回某专家对某艺术品的评估"),
                seed("evaluation-audit:history:view", "查看审核历史", "EVALUATION_AUDIT", "查看审核记录"),
                seed("user:view", "查看用户", "AUTH", "查看用户列表"),
                seed("user:create", "创建用户", "AUTH", "新增用户"),
                seed("user:update", "编辑用户", "AUTH", "编辑用户、重置密码、分配角色"),
                seed("user:disable", "停用用户", "AUTH", "停用用户"),
                seed("role:view", "查看角色", "AUTH", "查看角色列表"),
                seed("role:create", "创建角色", "AUTH", "新建角色"),
                seed("role:update", "编辑角色", "AUTH", "编辑角色权限"),
                seed("role:disable", "停用角色", "AUTH", "停用角色"),
                seed("audit-log:view", "查看审计日志", "AUDIT", "查看系统审计日志列表")
        );

        for (PermissionSeed seed : seeds) {
            AuthPermission permission = permissionRepository.findByCode(seed.code()).orElseGet(AuthPermission::new);
            permission.setCode(seed.code());
            permission.setName(seed.name());
            permission.setModule(seed.module());
            permission.setResourceType(PermissionResourceType.API);
            permission.setDescription(seed.description());
            permission.setEnabled(true);
            permission.setBuiltIn(true);
            permissionRepository.save(permission);
        }
        return permissionRepository.findAll().stream()
                .collect(Collectors.toMap(AuthPermission::getCode, permission -> permission));
    }

    private AuthRole upsertRole(String code, String name, String description, boolean builtIn) {
        AuthRole role = roleRepository.findWithPermissionsByCode(code).orElseGet(AuthRole::new);
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setEnabled(true);
        role.setBuiltIn(builtIn);
        return role;
    }

    private LinkedHashSet<AuthPermission> resolvePermissions(Map<String, AuthPermission> permissions, List<String> codes) {
        return codes.stream()
                .map(permissions::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void createDefaultAdminIfNeeded(AuthRole adminRole) {
        if (userRepository.count() > 0) {
            return;
        }
        String username = appProperties.getAuth().getAdminUsername();
        String password = appProperties.getAuth().getAdminPassword();
        passwordService.validatePasswordStrength(password, username);
        AuthUser admin = new AuthUser();
        admin.setUsername(username);
        admin.setDisplayName("系统管理员");
        admin.setPasswordHash(passwordService.hashPassword(password));
        admin.setStatus(UserStatus.ENABLED);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);
    }

    private PermissionSeed seed(String code, String name, String module, String description) {
        return new PermissionSeed(code, name, module, description);
    }

    private record PermissionSeed(String code, String name, String module, String description) {
    }
}
