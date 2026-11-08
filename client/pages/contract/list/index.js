const { getAccessToken } = require('../../../store/auth')
const { listMyContracts } = require('../../../api/contract')

const PAGE_SIZE = 10

const STATUS_TEXT = {
  DRAFT: '草稿',
  PENDING_SIGN: '待签署',
  ACTIVE: '生效中',
  EXPIRED: '已到期',
  TERMINATED: '已终止',
  CANCELLED: '已作废',
}

const TABS = [
  { value: '', label: '全部' },
  { value: 'PENDING_SIGN', label: '待签署' },
  { value: 'ACTIVE', label: '生效中' },
  { value: 'TERMINATED', label: '已终止' },
]

function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

function formatMoney(value) {
  const num = Number(value || 0)
  return '¥' + num.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function decorate(item) {
  return Object.assign({}, item, {
    statusText: STATUS_TEXT[item.status] || item.status,
    amountText: formatMoney(item.amount),
    depositText: formatMoney(item.deposit),
    periodText: formatDate(item.startDate) + ' ~ ' + formatDate(item.endDate),
    needSign: item.status === 'PENDING_SIGN',
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
  },

  onLoad() {
    if (!getAccessToken()) {
      this.setData({ loggedIn: false, error: '请先登录后查看合同' })
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
    return listMyContracts(params)
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

  retry() {
    this.load(true)
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/contract/detail/index?id=' + id })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },
})
