// pages/login/login.js
const authApi = require('../../api/auth')
const {
  setAccessToken,
  setRefreshToken,
  setUser,
} = require('../../store/auth')

Page({
  data: {
    username: '',
    password: '',
    loading: false,
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  async handleLogin() {
    const { username, password } = this.data
    if (!username || !password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const data = await authApi.login(username, password)
      setAccessToken(data.accessToken)
      setRefreshToken(data.refreshToken)
      setUser(data.user)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (err) {
      wx.showToast({ title: err.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
})
