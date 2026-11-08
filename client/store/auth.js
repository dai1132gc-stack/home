const env = require('./env')

const REFRESH_TOKEN_KEY = 'rt'
const USER_KEY = 'user'

/** Access Token 仅保存在内存（模块变量），不写 Storage，降低泄露风险 */
let accessToken = ''

function getAccessToken() {
  return accessToken
}

function setAccessToken(token) {
  accessToken = token || ''
}

/** Refresh Token 存 Storage（安全折中：小程序无 HttpOnly Cookie，依赖 Storage + 服务端会话撤销） */
function getRefreshToken() {
  try {
    return wx.getStorageSync(REFRESH_TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

function setRefreshToken(token) {
  if (token) {
    wx.setStorageSync(REFRESH_TOKEN_KEY, token)
  } else {
    wx.removeStorageSync(REFRESH_TOKEN_KEY)
  }
}

function getUser() {
  try {
    return wx.getStorageSync(USER_KEY) || null
  } catch (e) {
    return null
  }
}

function setUser(user) {
  if (user) {
    wx.setStorageSync(USER_KEY, user)
  } else {
    wx.removeStorageSync(USER_KEY)
  }
}

function clearAuth() {
  setAccessToken('')
  setRefreshToken('')
  setUser(null)
}

module.exports = {
  env,
  REFRESH_TOKEN_KEY,
  USER_KEY,
  getAccessToken,
  setAccessToken,
  getRefreshToken,
  setRefreshToken,
  getUser,
  setUser,
  clearAuth,
}
