package com.example.realestate.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 启动引导：sys_user 为空时创建初始 ADMIN/CUSTOMER 账号。
 * 密码来自 BOOTSTRAP_ADMIN_PASSWORD / BOOTSTRAP_CUSTOMER_PASSWORD 环境变量；
 * prod 未提供则启动失败；dev/test 未提供时使用开发默认密码（仅限本地）。
 * 不在 Flyway 迁移中写死任何账号或弱密码。
 */
@Component
public class BootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapInitializer.class);
    private static final String DEV_DEFAULT_ADMIN_PASSWORD = "Admin@123456";
    private static final String DEV_DEFAULT_CUSTOMER_PASSWORD = "Customer@123456";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final String profile;

    public BootstrapInitializer(SysUserMapper sysUserMapper,
                                PasswordEncoder passwordEncoder,
                                AppProperties properties,
                                @Value("${spring.profiles.active:dev}") String profile) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.profile = profile;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = sysUserMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("已有 {} 个用户，跳过启动引导", count);
            return;
        }
        boolean prod = "prod".equals(profile);
        String adminPassword = resolvePassword(properties.bootstrap().adminPassword(),
                DEV_DEFAULT_ADMIN_PASSWORD, "BOOTSTRAP_ADMIN_PASSWORD", prod);
        String customerPassword = resolvePassword(properties.bootstrap().customerPassword(),
                DEV_DEFAULT_CUSTOMER_PASSWORD, "BOOTSTRAP_CUSTOMER_PASSWORD", prod);

        insertUser("admin", adminPassword, SysUser.ROLE_ADMIN, "系统管理员");
        insertUser("customer", customerPassword, SysUser.ROLE_CUSTOMER, "演示客户");
        log.warn("启动引导完成：已创建初始账号 admin(ADMIN) / customer(CUSTOMER)，请尽快修改默认密码");
    }

    private String resolvePassword(String provided, String devDefault, String envName, boolean prod) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        if (prod) {
            throw new IllegalStateException("生产环境必须通过 " + envName + " 提供初始账号密码");
        }
        log.warn("未设置 {}，使用开发默认密码（仅限本地 dev/test）", envName);
        return devDefault;
    }

    private void insertUser(String username, String rawPassword, String role, String nickname) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setNickname(nickname);
        user.setStatus(SysUser.STATUS_ACTIVE);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        sysUserMapper.insert(user);
    }
}
