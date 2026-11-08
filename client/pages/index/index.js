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

  /** 房源列表：游客可浏览；tab=fav 需要登录，未登录由列表页跳登录 */
  goHouses(e) {
    const tab = (e && e.currentTarget && e.currentTarget.dataset.tab) || 'all'
    wx.navigateTo({ url: '/pages/house/list/index?tab=' + tab })
  },

  goAppointments() {
    if (!getAccessToken()) {
      this.goLogin()
      return
    }
    wx.navigateTo({ url: '/pages/appointment/list/index' })
  },

  goContracts() {
    if (!getAccessToken()) {
      this.goLogin()
      return
    }
    wx.navigateTo({ url: '/pages/contract/list/index' })
  },

  goRepairs() {
    if (!getAccessToken()) {
      this.goLogin()
      return
    }
    wx.navigateTo({ url: '/pages/repair/list/index' })
  },

  goGuides(e) {
    const category = (e && e.currentTarget && e.currentTarget.dataset.category) || ''
    const url = '/pages/content/list/index?type=guide' + (category ? '&category=' + category : '')
    wx.navigateTo({ url })
  },

  goPolicies() {
    wx.navigateTo({ url: '/pages/content/list/index?type=policy' })
  },

  goCalculator() {
    wx.navigateTo({ url: '/pages/calculator/index' })
  },
})
