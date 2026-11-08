package com.example.realestate.security;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.user.model.entity.SysUser;
import com.example.realestate.security.model.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 当前登录用户访问器：业务 Service 只依赖 SecurityContext，不依赖 HttpServletRequest。
 * 游客（匿名）返回 empty，由业务决定是否允许匿名访问。
 */
@Component
public class CurrentUserProvider {

    public Optional<LoginUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return Optional.of(loginUser);
        }
        return Optional.empty();
    }

    public boolean isAdmin() {
        return current().map(u -> SysUser.ROLE_ADMIN.equals(u.role())).orElse(false);
    }

    public boolean isCustomer() {
        return current().map(u -> SysUser.ROLE_CUSTOMER.equals(u.role())).orElse(false);
    }

    public Long currentUserIdOrNull() {
        return current().map(LoginUser::userId).orElse(null);
    }

    /** 收藏等必须登录的场景：未登录统一 401 */
    public Long requireUserId() {
        return current().map(LoginUser::userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
