const {
  env,
  getAccessToken,
  setAccessToken,
  getRefreshToken,
  setRefreshToken,
  setUser,
  clearAuth,
} = require('../store/auth')

/**
 * wx.request 封装（W5 加固）：
 * - 自动附加 Authorization 与 X-Request-Id；15s 超时，弱网给出明确提示
 * - 401 单飞刷新 Refresh Token，并发请求队列等待，原请求最多重试一次
 * - GET 网络失败可重试一次；POST/PUT/DELETE 不盲目重试，避免重复提交
 * - 刷新失败清除本地令牌并跳转登录页（保留一次返回栈，登录后可返回原页面）
 * - 不依赖浏览器 Cookie，小程序 Refresh Token 走 Storage
 */

const TIMEOUT_MS = 15000
const LOGIN_URL = '/pages/login/login'

let refreshing = false
const pendingQueue = [] // 刷新期间挂起的请求回调
let lastRedirectAt = 0

function genTraceId() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

/** 网络层错误统一为可展示中文，保留 errMsg 便于排查 */
function networkError(err) {
  const message = String((err && (err.errMsg || err.message)) || '')
  const timeout = message.indexOf('timeout') >= 0 || message.indexOf('超时') >= 0
  const wrapped = new Error(timeout ? '网络超时，请稍后重试' : '网络异常，请检查网络后重试')
  wrapped.networkError = true
  wrapped.rawMessage = message
  return wrapped
}

/** 401 刷新失败：清空本地令牌并回登录页；保留返回栈，登录成功后返回原页面 */
function redirectToLogin() {
  clearAuth()
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  if (current && current.route === 'pages/login/login') return
  const now = Date.now()
  if (now - lastRedirectAt < 1500) return
  lastRedirectAt = now
  wx.navigateTo({
    url: LOGIN_URL,
    fail() {
      wx.reLaunch({ url: LOGIN_URL })
    },
  })
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
    return Promise.reject(new Error('登录已过期，请重新登录'))
  }
  return new Promise((resolve, reject) => {
    wx.request({
      url: env.apiBaseUrl + '/auth/refresh',
      method: 'POST',
      data: { refreshToken },
      timeout: TIMEOUT_MS,
      header: { 'Content-Type': 'application/json', 'X-Request-Id': genTraceId() },
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 200 && body.code === '0' && body.data && body.data.accessToken) {
          setAccessToken(body.data.accessToken)
          // 服务端轮换后返回新 refreshToken（小程序客户端）
          if (body.data.refreshToken) setRefreshToken(body.data.refreshToken)
          if (body.data.user) setUser(body.data.user)
          refreshing = false
          flushQueue(null, body.data.accessToken)
          resolve(body.data.accessToken)
        } else {
          refreshing = false
          const err = new Error(body.message || '登录已过期，请重新登录')
          err.code = body.code
          flushQueue(err)
          redirectToLogin()
          reject(err)
        }
      },
      fail(err) {
        // 弱网导致刷新失败时不清空令牌：仅本次请求失败，用户仍可重试
        const wrapped = networkError(err)
        refreshing = false
        flushQueue(wrapped)
        reject(wrapped)
      },
    })
  })
}

/**
 * 通用请求
 * @param {Object} options { url, method, data, header, _retried, _netRetried }
 * @returns {Promise<any>} 成功 resolve body.data；失败 reject Error（含 code/httpStatus）
 */
function request(options) {
  const token = getAccessToken()
  const method = options.method || 'GET'
  const header = Object.assign(
    { 'Content-Type': 'application/json', 'X-Request-Id': genTraceId() },
    options.header || {},
  )
  if (token) header.Authorization = 'Bearer ' + token

  return new Promise((resolve, reject) => {
    wx.request({
      url: env.apiBaseUrl + options.url,
      method,
      data: options.data,
      header,
      timeout: TIMEOUT_MS,
      success(res) {
        const body = res.data || {}
        // 业务码失败（HTTP 200 但业务码非 0）
        if (res.statusCode === 200 && body.code !== '0') {
          const err = new Error(body.message || '请求失败')
          err.code = body.code
          err.traceId = body.traceId
          return reject(err)
        }
        // 401：单飞刷新后重试一次；登录/刷新/微信登录接口自身不触发刷新
        const isAuthApi =
          options.url.indexOf('/auth/login') >= 0 ||
          options.url.indexOf('/auth/refresh') >= 0 ||
          options.url.indexOf('/auth/wechat-login') >= 0
        if (res.statusCode === 401 && !options._retried && !isAuthApi) {
          options._retried = true
          refreshAccessToken()
            .then(() => request(options).then(resolve, reject))
            .catch(reject)
          return
        }
        if (res.statusCode === 401) {
          const err = new Error(body.message || '登录已过期，请重新登录')
          err.code = body.code
          err.httpStatus = 401
          return reject(err)
        }
        if (res.statusCode >= 400) {
          const err = new Error(body.message || '请求失败')
          err.code = body.code
          err.httpStatus = res.statusCode
          err.traceId = body.traceId
          return reject(err)
        }
        resolve(body.data)
      },
      fail(err) {
        // GET 可安全重试一次（弱网/瞬时失败）；POST 等写操作不重试，避免重复提交
        if (method === 'GET' && !options._netRetried) {
          options._netRetried = true
          return request(options).then(resolve, reject)
        }
        reject(networkError(err))
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
  networkError,
}
