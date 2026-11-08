package com.example.realestate.security.filter;

import com.example.realestate.common.enums.ClientType;
import com.example.realestate.security.jwt.JwtTokenProvider;
import com.example.realestate.security.model.LoginUser;
import com.example.realestate.security.service.UserStatusService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Authorization: Bearer <accessToken>。
 * 解析失败不中断链路，由 Security 的 401 入口点统一响应。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserStatusService userStatusService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   UserStatusService userStatusService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userStatusService = userStatusService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                JwtTokenProvider.AccessClaims claims = jwtTokenProvider.parseAccessToken(token);
                // V6.0.1 F3：禁用账号/已删除账号的 Access Token 最迟 30 秒内失效
                if (userStatusService.isActive(claims.userId())) {
                    LoginUser loginUser = new LoginUser(
                            claims.userId(), claims.username(), claims.role(), claims.client());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.info("拒绝非活跃账号的 Access Token userId={}", claims.userId());
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Access Token 校验失败: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
