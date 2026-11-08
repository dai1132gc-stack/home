// pages/profile/profile.js
const authApi = require('../../api/auth')
const { getUser, getAccessToken, clearAuth } = require('../../store/auth')

Page({
  data: {
    user: null,
    loading: false,
  },

  onLoad() {
    this.refreshView()
    if (getAccessToken()) {
      this.fetchMe()
    }
  },

  refreshView() {
    this.setData({ user: getUser() })
  },

  async fetchMe() {
    this.setData({ loading: true })
    try {
      const user = await authApi.me()
      const { setUser } = require('../../store/auth')
      setUser(user)
      this.refreshView()
    } catch (err) {
      wx.showToast({ title: err.message || '获取信息失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async handleLogout() {
    try {
      await authApi.logout()
    } catch (e) {
      // 退出失败也清本地，避免残留
    } finally {
      clearAuth()
      wx.showToast({ title: '已退出', icon: 'success' })
      setTimeout(() => wx.reLaunch({ url: '/pages/index/index' }), 600)
    }
  },
})
