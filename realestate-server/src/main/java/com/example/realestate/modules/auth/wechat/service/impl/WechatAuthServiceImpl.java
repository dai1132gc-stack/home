package com.example.realestate.modules.auth.wechat.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.common.enums.ClientType;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AppProperties;
import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.service.AuthService;
import com.example.realestate.modules.auth.wechat.client.WechatMiniClient;
import com.example.realestate.modules.auth.mapper.UserWechatBindingMapper;
import com.example.realestate.modules.auth.wechat.model.dto.WechatLoginRequest;
import com.example.realestate.modules.auth.wechat.model.entity.UserWechatBinding;
import com.example.realestate.modules.auth.wechat.service.WechatAuthService;
import com.example.realestate.modules.auth.wechat.service.WechatUserRegistrar;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 微信 code 登录实现。
 * 安全约束：
 * 1. 只信任 jscode2session 返回的 openid，客户端提交的 openid/unionid 一律忽略；
 * 2. 未配置凭据返回 503，code 无效 401，上游异常 502，不回退为“信任客户端身份”；
 * 3. 日志仅记录 userId、clientType、结果、耗时与错误码，不记录 code、openid 全量、session_key。
 */
@Service
public class WechatAuthServiceImpl implements WechatAuthService {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthServiceImpl.class);

    private final WechatMiniClient wechatMiniClient;
    private final WechatUserRegistrar registrar;
    private final UserWechatBindingMapper bindingMapper;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final AppProperties properties;

    public WechatAuthServiceImpl(WechatMiniClient wechatMiniClient,
                                 WechatUserRegistrar registrar,
                                 UserWechatBindingMapper bindingMapper,
                                 SysUserMapper sysUserMapper,
                                 AuthService authService,
                                 AppProperties properties) {
        this.wechatMiniClient = wechatMiniClient;
        this.registrar = registrar;
        this.bindingMapper = bindingMapper;
        this.sysUserMapper = sysUserMapper;
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public LoginResponse login(WechatLoginRequest request) {
        long start = System.currentTimeMillis();
        try {
            LoginResponse response = doLogin(request);
            log.info("微信登录成功 userId={} client=MINI elapsedMs={}",
                    response.user().id(), System.currentTimeMillis() - start);
            return response;
        } catch (BusinessException e) {
            log.warn("微信登录失败 code={} client={} elapsedMs={}",
                    e.errorCode().code(),
                    request.clientType(),
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    private LoginResponse doLogin(WechatLoginRequest request) {
        if (request.clientType() != ClientType.MINI) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "微信登录仅支持小程序客户端");
        }
        if (!properties.wechat().configured()) {
            // 配置缺失属于部署状态，不是用户错误：明确 503 并提示降级账号密码登录
            throw new BusinessException(ErrorCode.WECHAT_NOT_CONFIGURED);
        }

        WechatMiniClient.WechatSession session = wechatMiniClient.code2Session(request.code().trim());
        UserWechatBinding binding = findByOpenid(session.openid());
        SysUser user;
        if (binding == null) {
            try {
                user = registrar.createCustomer(session.openid(), session.unionid());
            } catch (DataIntegrityViolationException e) {
                // 并发首次登录：唯一键兜底，回滚后重查绑定；仍无绑定则提示重试，绝不重复建号
                binding = findByOpenid(session.openid());
                if (binding == null) {
                    log.warn("微信首次登录唯一键冲突且未查到绑定，按并发冲突处理");
                    throw new BusinessException(ErrorCode.CONFLICT, "微信登录冲突，请重试");
                }
                user = sysUserMapper.selectById(binding.getUserId());
            }
        } else {
            user = sysUserMapper.selectById(binding.getUserId());
        }

        if (user == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        // 账号状态、角色校验与令牌签发复用账号密码登录的同一套逻辑
        return authService.loginAfterExternalAuth(user.getId(), ClientType.MINI);
    }

    private UserWechatBinding findByOpenid(String openid) {
        return bindingMapper.selectOne(
                Wrappers.<UserWechatBinding>lambdaQuery().eq(UserWechatBinding::getOpenid, openid));
    }
}
