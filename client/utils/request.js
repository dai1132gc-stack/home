const { env, getAccessToken, setAccessToken, getRefreshToken, clearAuth } = require('../store/auth')

/**
 * wx.request 封装：
 * - 自动附加 Authorization 与 X-Request-Id
 * - 401 单飞刷新 Refresh Token，请求队列等待后最多重试一次
 * - 刷新失败清除本地令牌并跳转登录页
 * - 不依赖浏览器 Cookie，小程序 Refresh Token 走 Storage
 */

let refreshing = false
const pendingQueue = [] // 刷新期间挂起的请求回调

function genTraceId() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

function redirectToLogin() {
  clearAuth()
  // 避免重复跳转
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  if (!current || current.route !== 'pages/login/login') {
    wx.reLaunch({ url: '/pages/login/login' })
  }
}

function flushQueue(error, newToken) {
  while (pendingQueue.length) {
    const cb = pendingQueue.shift()
    if (error) cb.reject(error)
    else cb.resolve(newToken)
  }
}

/** 单飞刷新：并发请求只触发一次 /auth/refresh */
function refreshAccessToken() {
  if (refreshing) {
    return new Promise((resolve, reject) => pendingQueue.push({ resolve, reject }))
  }
  refreshing = true
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    refreshing = false
    redirectToLogin()
    return Promise.reject(new Error('无刷新令牌'))
  }
  return new Promise((resolve, reject) => {
    wx.request({
      url: env.apiBaseUrl + '/auth/refresh',
      method: 'POST',
      data: { refreshToken },
      header: { 'Content-Type': 'application/json', 'X-Request-Id': genTraceId() },
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 200 && body.code === '0' && body.data && body.data.accessToken) {
          setAccessToken(body.data.accessToken)
          // 服务端轮换后返回新 refreshToken（小程序客户端）
          if (body.data.refreshToken) {
            const { setRefreshToken } = require('../store/auth')
            setRefreshToken(body.data.refreshToken)
          }
          if (body.data.user) {
            const { setUser } = require('../store/auth')
            setUser(body.data.user)
          }
          refreshing = false
          flushQueue(null, body.data.accessToken)
          resolve(body.data.accessToken)
        } else {
          refreshing = false
          flushQueue(new Error('刷新失败'))
          redirectToLogin()
          reject(new Error(body.message || '刷新失败'))
        }
      },
      fail(err) {
        refreshing = false
        flushQueue(err)
        reject(err)
      },
    })
  })
}

/**
 * 通用请求
 * @param {Object} options { url, method, data, header, _retried }
 * @returns {Promise<any>} 成功 resolve body.data；失败 reject Error
 */
function request(options) {
  const token = getAccessToken()
  const header = Object.assign(
    { 'Content-Type': 'application/json', 'X-Request-Id': genTraceId() },
    options.header || {},
  )
  if (token) header.Authorization = 'Bearer ' + token

  return new Promise((resolve, reject) => {
    wx.request({
      url: env.apiBaseUrl + options.url,
      method: options.method || 'GET',
      data: options.data,
      header,
      success(res) {
        const body = res.data || {}
        // 业务码失败
        if (res.statusCode === 200 && body.code !== '0') {
          const err = new Error(body.message || '请求失败')
          err.code = body.code
          err.traceId = body.traceId
          return reject(err)
        }
        // 401：尝试刷新后重试一次（登录/刷新接口自身除外）
        if (
          res.statusCode === 401 &&
          !options._retried &&
          !options.url.includes('/auth/login') &&
          !options.url.includes('/auth/refresh')
        ) {
          options._retried = true
          refreshAccessToken()
            .then(() => request(options).then(resolve, reject))
            .catch(reject)
          return
        }
        if (res.statusCode >= 400) {
          const err = new Error(body.message || '请求失败')
          err.code = body.code
          err.httpStatus = res.statusCode
          return reject(err)
        }
        resolve(body.data)
      },
      fail(err) {
        reject(err)
      },
    })
  })
}

function get(url, data) {
  return request({ url, method: 'GET', data })
}

function post(url, data) {
  return request({ url, method: 'POST', data })
}

module.exports = {
  request,
  get,
  post,
  refreshAccessToken,
}
