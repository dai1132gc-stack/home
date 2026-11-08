const { getAccessToken } = require('../../../store/auth')
const { getHouse } = require('../../../api/house')
const { createAppointment } = require('../../../api/appointment')

const LAST_PHONE_KEY = 'last_appointment_phone'

function pad(value) {
  return value < 10 ? '0' + value : String(value)
}

function formatDate(date) {
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
}

Page({
  data: {
    houseId: 0,
    house: null,
    loading: true,
    error: '',
    date: '',
    time: '10:00',
    minDate: '',
    maxDate: '',
    phone: '',
    remark: '',
    submitting: false,
  },

  onLoad(options) {
    const houseId = Number(options && options.id)
    if (!houseId) {
      this.setData({ loading: false, error: '房源参数不正确' })
      return
    }
    if (!getAccessToken()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => {
        wx.navigateTo({ url: '/pages/login/login' })
      }, 500)
      return
    }
    const today = new Date()
    const max = new Date()
    max.setDate(max.getDate() + 30)
    let phone = ''
    try {
      phone = wx.getStorageSync(LAST_PHONE_KEY) || ''
    } catch (e) {
      phone = ''
    }
    this.setData({
      houseId,
      date: formatDate(today),
      minDate: formatDate(today),
      maxDate: formatDate(max),
      phone,
    })
    this.loadHouse()
  },

  loadHouse() {
    this.setData({ loading: true, error: '' })
    return getHouse(this.data.houseId)
      .then((house) => {
        this.setData({ house, loading: false })
      })
      .catch((err) => {
        this.setData({ loading: false, error: (err && err.message) || '房源不存在或已下架' })
      })
  },

  onDateChange(e) {
    this.setData({ date: e.detail.value })
  },

  onTimeChange(e) {
    this.setData({ time: e.detail.value })
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value })
  },

  onRemarkInput(e) {
    this.setData({ remark: e.detail.value })
  },

  submit() {
    if (this.data.submitting) return
    const phone = String(this.data.phone || '').trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请填写正确的手机号', icon: 'none' })
      return
    }
    const preferredTime = this.data.date + 'T' + this.data.time
    const target = new Date(preferredTime.replace(/-/g, '/'))
    if (target.getTime() <= Date.now()) {
      wx.showToast({ title: '看房时间需晚于当前时间', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    createAppointment({
      houseId: this.data.houseId,
      preferredTime,
      contactPhone: phone,
      remark: String(this.data.remark || '').trim() || undefined,
    })
      .then(() => {
        try {
          wx.setStorageSync(LAST_PHONE_KEY, phone)
        } catch (e) {
          // 忽略本地存储失败
        }
        wx.showToast({ title: '预约已提交', icon: 'success' })
        setTimeout(() => {
          wx.redirectTo({ url: '/pages/appointment/list/index' })
        }, 800)
      })
      .catch((err) => {
        this.setData({ submitting: false })
        wx.showToast({ title: (err && err.message) || '预约失败', icon: 'none' })
      })
  },

  goMyAppointments() {
    wx.navigateTo({ url: '/pages/appointment/list/index' })
  },

  goBack() {
    wx.navigateBack({ delta: 1 })
  },
})
