const { getAccessToken } = require('../../../store/auth')
const { cancelRepair, closeRepair, listMyRepairs, reopenRepair } = require('../../../api/repair')

const PAGE_SIZE = 10

const STATUS_TEXT = {
  SUBMITTED: '待受理',
  ACCEPTED: '已受理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
  CANCELLED: '已撤销',
  REOPENED: '已重开',
}

const CATEGORY_TEXT = {
  PLUMBING: '水暖管道',
  ELECTRICAL: '电路照明',
  APPLIANCE: '家电设备',
  STRUCTURE: '房屋结构',
  OTHER: '其他',
}

const TABS = [
  { value: '', label: '全部' },
  { value: 'SUBMITTED', label: '待受理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
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
    categoryText: CATEGORY_TEXT[item.category] || item.category,
    createdText: formatDateTime(item.createdAt),
    canCancel: item.status === 'SUBMITTED' || item.status === 'ACCEPTED',
    canClose: item.status === 'RESOLVED',
    canReopen: item.status === 'RESOLVED' || item.status === 'CLOSED',
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
      this.setData({ loggedIn: false, error: '请先登录后查看报修' })
      return
    }
    this.setData({ loggedIn: true })
    this.load(true)
  },

  onShow() {
    if (getAccessToken() && this.data.loggedIn) {
      this.load(true)
    } else if (getAccessToken() && !this.data.loggedIn) {
      this.setData({ loggedIn: true })
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
    return listMyRepairs(params)
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

  act(e) {
    const id = Number(e.currentTarget.dataset.id)
    const action = e.currentTarget.dataset.action
    if (!id || !action || this.data.acting) return
    const config = {
      cancel: { title: '撤销报修', content: '撤销后工单终止，确认撤销？', run: cancelRepair },
      close: { title: '确认完成', content: '确认问题已解决并关闭工单？', run: closeRepair },
      reopen: { title: '重新打开', content: '问题未解决时可将工单重新打开，确认继续？', run: reopenRepair },
    }[action]
    if (!config) return
    wx.showModal({
      title: config.title,
      content: config.content,
      success: (res) => {
        if (!res.confirm) return
        this.setData({ acting: true })
        config
          .run(id)
          .then(() => {
            wx.showToast({ title: '操作成功', icon: 'none' })
            this.setData({ acting: false })
            this.load(true)
          })
          .catch((err) => {
            this.setData({ acting: false })
            wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' })
          })
      },
    })
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/repair/detail/index?id=' + id })
  },

  goCreate() {
    wx.navigateTo({ url: '/pages/house/list/index' })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },
})
