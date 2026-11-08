const { getAccessToken } = require('../../../store/auth')
const { getHouse } = require('../../../api/house')
const { getContract } = require('../../../api/contract')
const { createRepair, uploadRepairImage } = require('../../../api/repair')

const LAST_PHONE_KEY = 'last_repair_phone'
const MAX_IMAGES = 6

const CATEGORIES = [
  { value: 'PLUMBING', label: '水暖管道' },
  { value: 'ELECTRICAL', label: '电路照明' },
  { value: 'APPLIANCE', label: '家电设备' },
  { value: 'STRUCTURE', label: '房屋结构' },
  { value: 'OTHER', label: '其他' },
]

function pad(value) {
  return value < 10 ? '0' + value : String(value)
}

function formatDate(date) {
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
}

Page({
  data: {
    mode: 'house',
    houseId: 0,
    contractId: 0,
    house: null,
    contract: null,
    loading: true,
    error: '',
    categories: CATEGORIES,
    categoryIndex: 0,
    urgency: 'NORMAL',
    title: '',
    description: '',
    phone: '',
    date: '',
    time: '10:00',
    minDate: '',
    maxDate: '',
    images: [],
    maxImages: MAX_IMAGES,
    submitting: false,
  },

  onLoad(options) {
    const houseId = Number(options && options.houseId) || 0
    const contractId = Number(options && options.contractId) || 0
    if (!houseId && !contractId) {
      this.setData({ loading: false, error: '缺少房源或合同参数' })
      return
    }
    if (!getAccessToken()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => wx.navigateTo({ url: '/pages/login/login' }), 500)
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
      mode: contractId ? 'contract' : 'house',
      houseId,
      contractId,
      phone,
      date: formatDate(today),
      minDate: formatDate(today),
      maxDate: formatDate(max),
    })
    this.loadSource()
  },

  loadSource() {
    this.setData({ loading: true, error: '' })
    if (this.data.contractId) {
      return getContract(this.data.contractId)
        .then((contract) => {
          if (contract.status !== 'ACTIVE') {
            this.setData({ loading: false, error: '仅生效中的合同可以申请报修' })
            return
          }
          this.setData({ contract, houseId: contract.houseId, loading: false })
        })
        .catch((err) => {
          this.setData({ loading: false, error: (err && err.message) || '合同不存在或无权限查看' })
        })
    }
    return getHouse(this.data.houseId)
      .then((house) => this.setData({ house, loading: false }))
      .catch((err) => {
        this.setData({ loading: false, error: (err && err.message) || '房源不存在或已下架' })
      })
  },

  onCategoryChange(e) {
    this.setData({ categoryIndex: Number(e.detail.value) })
  },

  onUrgencyChange(e) {
    this.setData({ urgency: e.detail.value })
  },

  onTitleInput(e) {
    this.setData({ title: e.detail.value })
  },

  onDescriptionInput(e) {
    this.setData({ description: e.detail.value })
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value })
  },

  onDateChange(e) {
    this.setData({ date: e.detail.value })
  },

  onTimeChange(e) {
    this.setData({ time: e.detail.value })
  },

  chooseImage() {
    const remain = MAX_IMAGES - this.data.images.length
    if (remain <= 0) {
      wx.showToast({ title: '最多上传 ' + MAX_IMAGES + ' 张图片', icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const paths = (res.tempFiles || []).map((file) => file.tempFilePath)
        this.setData({ images: this.data.images.concat(paths) })
      },
    })
  },

  removeImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    const images = this.data.images.slice()
    images.splice(index, 1)
    this.setData({ images })
  },

  previewImage(e) {
    const current = e.currentTarget.dataset.url
    if (!current) return
    wx.previewImage({ current, urls: this.data.images })
  },

  submit() {
    if (this.data.submitting) return
    const title = String(this.data.title || '').trim()
    const description = String(this.data.description || '').trim()
    const phone = String(this.data.phone || '').trim()
    if (title.length < 4) {
      wx.showToast({ title: '标题至少 4 个字', icon: 'none' })
      return
    }
    if (description.length < 10) {
      wx.showToast({ title: '请至少描述 10 个字', icon: 'none' })
      return
    }
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请填写正确的手机号', icon: 'none' })
      return
    }
    const target = new Date(this.data.date + 'T' + this.data.time + ':00')
    if (target.getTime() < Date.now() - 5 * 60 * 1000) {
      wx.showToast({ title: '期望上门时间不能早于当前时间', icon: 'none' })
      return
    }
    const category = CATEGORIES[this.data.categoryIndex]
    const payload = {
      houseId: this.data.houseId,
      contractId: this.data.contractId || undefined,
      category: category.value,
      urgency: this.data.urgency,
      title,
      description,
      contactPhone: phone,
      preferredVisitTime: target.toISOString(),
    }
    this.setData({ submitting: true })
    createRepair(payload)
      .then((ticket) => {
        try {
          wx.setStorageSync(LAST_PHONE_KEY, phone)
        } catch (e) {
          // 忽略本地存储失败
        }
        const paths = this.data.images.slice()
        let chain = Promise.resolve()
        let failed = 0
        paths.forEach((path) => {
          chain = chain.then(() =>
            uploadRepairImage(ticket.id, path).catch(() => {
              failed += 1
            }),
          )
        })
        return chain.then(() => {
          wx.showToast({
            title: failed > 0 ? '工单已提交，' + failed + ' 张图片上传失败' : '报修已提交',
            icon: 'none',
          })
          setTimeout(() => {
            wx.redirectTo({ url: '/pages/repair/detail/index?id=' + ticket.id })
          }, 800)
        })
      })
      .catch((err) => {
        this.setData({ submitting: false })
        wx.showToast({ title: (err && err.message) || '提交失败', icon: 'none' })
      })
  },

  goBack() {
    wx.navigateBack({ delta: 1 })
  },
})
