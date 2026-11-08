package com.example.realestate.modules.auth.wechat.client;

/**
 * 微信小程序登录凭证校验客户端（code2session）。
 * 实现只返回 openid/unionid，session_key 不落库、不返回、不写日志。
 */
public interface WechatMiniClient {

    /** 微信会话结果（不含 session_key） */
    record WechatSession(String openid, String unionid) {
    }

    /**
     * 用 wx.login 的 code 换取微信身份。
     * code 一次性使用；失败按 AUTH_40105（无效/过期）、AUTH_50201（上游异常）、
     * AUTH_50301（凭据未配置/无效）抛出业务异常，调用方不得回退为信任客户端 openid。
     */
    WechatSession code2Session(String code);
}
