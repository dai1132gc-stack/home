const { post, get } = require('../utils/request')

/** 账号密码登录（W1 测试号阶段，不调用 code2session） */
function login(username, password) {
  return post('/auth/login', {
    username,
    password,
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

module.exports = { login, logout, me }
