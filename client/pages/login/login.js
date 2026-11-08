// pages/login/login.js
const authApi = require('../../api/auth')
const { setAccessToken, setRefreshToken, setUser } = require('../../store/auth')

const WECHAT_LOGIN_TIMEOUT_MS = 10000

/** wx.login 包装：10s 超时，失败给出可读提示 */
function getWechatCode() {
  return new Promise((resolve, reject) => {
    let settled = false
    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      reject(new Error('微信登录超时，请重试或使用账号密码'))
    }, WECHAT_LOGIN_TIMEOUT_MS)
    wx.login({
      success(res) {
        if (settled) return
        settled = true
        clearTimeout(timer)
        if (res && res.code) {
          resolve(res.code)
        } else {
          reject(new Error('获取微信登录凭证失败，请重试或使用账号密码'))
        }
      },
      fail() {
        if (settled) return
        settled = true
        clearTimeout(timer)
        reject(new Error('微信登录失败，请重试或使用账号密码'))
      },
    })
  })
}

/** 微信登录错误码 → 用户可读提示（不展示后端原始细节） */
function wechatErrorMessage(err) {
  const code = err && err.code
  if (code === 'AUTH_50301') return '微信登录未配置，请使用账号密码'
  if (code === 'AUTH_40105') return '微信登录凭证已失效，请重试'
  if (code === 'AUTH_50201') return '微信服务暂时不可用，请稍后重试'
  return (err && err.message) || '微信登录失败，请重试'
}

Page({
  data: {
    username: '',
    password: '',
    loading: false,
    wechatLoading: false,
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  /** 登录成功后回原页面（保留 return），无返回栈则回首页 */
  applyLogin(data, title) {
    setAccessToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    setUser(data.user)
    wx.showToast({ title, icon: 'success' })
    setTimeout(() => {
      const pages = getCurrentPages ? getCurrentPages() : []
      if (pages.length > 1) {
        wx.navigateBack({ delta: 1 })
      } else {
        wx.reLaunch({ url: '/pages/index/index' })
      }
    }, 600)
  },

  /** 微信一键登录：wx.login 取 code 后立即提交，失败不静默 */
  handleWechatLogin() {
    if (this.data.loading || this.data.wechatLoading) return
    this.setData({ wechatLoading: true })
    getWechatCode()
      .then((code) => authApi.wechatLogin(code))
      .then((data) => {
        // 成功后保持 loading 直到页面返回，避免 600ms 窗口内重复提交
        this.applyLogin(data, '登录成功')
      })
      .catch((err) => {
        this.setData({ wechatLoading: false })
        wx.showToast({ title: wechatErrorMessage(err), icon: 'none' })
      })
  },

  async handleLogin() {
    if (this.data.loading || this.data.wechatLoading) return
    const { username, password } = this.data
    if (!username || !password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const data = await authApi.login(username, password)
      // 成功后保持 loading 直到页面返回，避免重复点击造成多次登录
      this.applyLogin(data, '登录成功')
    } catch (err) {
      this.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '登录失败', icon: 'none' })
    }
  },
})
