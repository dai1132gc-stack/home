const { getAccessToken } = require('../../../store/auth')
const { cancelAppointment, listMyAppointments } = require('../../../api/appointment')

const PAGE_SIZE = 10

const STATUS_TEXT = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  NO_SHOW: '已爽约',
}

const TABS = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待确认' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'COMPLETED', label: '已完成' },
]

function pad(value) {
  return value < 10 ? '0' + value : String(value)
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (isNaN(date.getTime())) return String(value)
  return (
    date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) +
    ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes())
  )
}

function decorate(item) {
  return Object.assign({}, item, {
    statusText: STATUS_TEXT[item.status] || item.status,
    timeText: formatDateTime(item.preferredTime),
    createdText: formatDateTime(item.createdAt),
    canCancel: item.status === 'PENDING' || item.status === 'CONFIRMED',
  })
}

Page({
  data: {
    status: '',
    tabs: TABS,
    items: [],
    page: 1,
    total: 0,
    loading: false,
    finished: false,
    error: '',
    loggedIn: false,
    acting: false,
  },

  onLoad() {
    if (!getAccessToken()) {
      this.setData({ loggedIn: false, error: '请先登录后查看预约' })
      return
    }
    this.setData({ loggedIn: true })
    this.load(true)
  },

  onShow() {
    if (getAccessToken() && this.data.loggedIn) {
      this.load(true)
    }
  },

  onPullDownRefresh() {
    this.load(true).then(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (!this.data.loading && !this.data.finished) this.load(false)
  },

  onScrollToLower() {
    this.onReachBottom()
  },

  switchStatus(e) {
    const status = e.currentTarget.dataset.status || ''
    if (status === this.data.status) return
    this.setData({ status })
    this.load(true)
  },

  load(reset) {
    if (!this.data.loggedIn || this.data.loading) return Promise.resolve()
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true, error: '' })
    const params = { page, size: PAGE_SIZE }
    if (this.data.status) params.status = this.data.status
    return listMyAppointments(params)
      .then((result) => {
        const list = (result.items || []).map(decorate)
        const items = reset ? list : this.data.items.concat(list)
        this.setData({
          items,
          page,
          total: result.total || 0,
          finished: items.length >= (result.total || 0) || list.length < PAGE_SIZE,
          loading: false,
        })
      })
      .catch((err) => {
        this.setData({ loading: false, error: (err && err.message) || '加载失败，请稍后重试' })
      })
  },

  cancel(e) {
    const id = e.currentTarget.dataset.id
    if (!id || this.data.acting) return
    wx.showModal({
      title: '取消预约',
      content: '取消后房源将释放给其他客户，确认取消？',
      success: (res) => {
        if (!res.confirm) return
        this.setData({ acting: true })
        cancelAppointment(id)
          .then(() => {
            wx.showToast({ title: '已取消', icon: 'none' })
            this.setData({ acting: false })
            this.load(true)
          })
          .catch((err) => {
            this.setData({ acting: false })
            wx.showToast({ title: (err && err.message) || '取消失败', icon: 'none' })
          })
      },
    })
  },

  goHouses() {
    wx.navigateTo({ url: '/pages/house/list/index' })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },
})
