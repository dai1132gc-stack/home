// index.js — 入口页：展示登录状态与导航
const { getUser, getAccessToken, getRefreshToken } = require('../../store/auth')
const { refreshAccessToken } = require('../../utils/request')

Page({
  data: {
    loggedIn: false,
    user: null,
    loading: false,
  },

  onLoad() {
    this.refreshView()
    // 内存 access token 丢失（页面冷启动）但有 refresh token → 尝试恢复
    if (!getAccessToken() && getRefreshToken()) {
      this.setData({ loading: true })
      refreshAccessToken()
        .then(() => this.refreshView())
        .catch(() => this.refreshView())
        .then(() => this.setData({ loading: false }))
    }
  },

  onShow() {
    this.refreshView()
  },

  refreshView() {
    const user = getUser()
    this.setData({
      loggedIn: !!getAccessToken(),
      user,
    })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/profile/profile' })
  },
})
