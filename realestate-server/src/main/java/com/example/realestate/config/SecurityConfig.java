package com.example.realestate.config;

import com.example.realestate.security.SecurityJsonHandlers;
import com.example.realestate.security.filter.JwtAuthenticationFilter;
import com.example.realestate.security.jwt.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 无状态 JWT 安全配置：登录/刷新/健康检查放行，其余接口需认证；
 * 方法级 @PreAuthorize 实施角色校验。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final SecurityJsonHandlers securityJsonHandlers;
    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(SecurityJsonHandlers securityJsonHandlers, JwtTokenProvider jwtTokenProvider) {
        this.securityJsonHandlers = securityJsonHandlers;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                }) // 复用 CorsConfig 注册的 CorsFilter bean（仅明确 Origin）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // W2：房源列表与详情对游客开放（Service 层强制只返回 PUBLISHED）；图片资源公开
                        .requestMatchers(HttpMethod.GET, "/api/v1/houses", "/api/v1/houses/*").permitAll()
                        // W4：教程/公告/政策公开只读（Service 强制 PUBLISHED）；测算公开但限流
                        .requestMatchers(HttpMethod.GET, "/api/v1/guides", "/api/v1/guides/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies", "/api/v1/policies/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/calculators/mortgage", "/api/v1/calculators/tax").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/calculators/config").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityJsonHandlers)
                        .accessDeniedHandler(securityJsonHandlers))
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
