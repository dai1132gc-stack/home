const { post, get } = require('../utils/request')

/** 账号密码登录（微信未配置或用户拒绝授权时的降级路径） */
function login(username, password) {
  return post('/auth/login', {
    username,
    password,
    clientType: 'MINI',
  })
}

/**
 * 微信一键登录：wx.login 的 code 一次性提交给后端换取双令牌。
 * 不在客户端保存/提交 openid；未配置凭据时后端返回 AUTH_50301，由页面提示降级账号密码。
 */
function wechatLogin(code) {
  return post('/auth/wechat-login', {
    code,
    clientType: 'MINI',
  })
}

function logout() {
  const { getRefreshToken } = require('../store/auth')
  return post('/auth/logout', { refreshToken: getRefreshToken() })
}

function me() {
  return get('/auth/me')
}

module.exports = { login, wechatLogin, logout, me }
