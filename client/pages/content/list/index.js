const { listGuides, listPolicies } = require('../../../api/content')

const PAGE_SIZE = 10

const GUIDE_TABS = [
  { value: '', label: '全部' },
  { value: 'GUIDE', label: '租房教程' },
  { value: 'NETWORK_GUIDE', label: '网签指引' },
  { value: 'NOTICE', label: '公告' },
]

const POLICY_TABS = [
  { value: '', label: '全部' },
  { value: 'POLICY', label: '政策' },
  { value: 'NOTICE', label: '通知' },
]

const GUIDE_CATEGORY = { GUIDE: '租房教程', NETWORK_GUIDE: '网签指引', NOTICE: '公告' }
const POLICY_TYPE = { POLICY: '政策', NOTICE: '通知' }

function formatDate(value) {
  return value ? String(value).slice(0, 10) : '-'
}

function decorateGuide(item) {
  return Object.assign({}, item, {
    categoryText: GUIDE_CATEGORY[item.category] || item.category,
    dateText: formatDate(item.updatedAt),
  })
}

function decoratePolicy(item) {
  return Object.assign({}, item, {
    categoryText: POLICY_TYPE[item.docType] || item.docType,
    dateText: formatDate(item.publishDate),
  })
}

Page({
  data: {
    type: 'guide',
    title: '租房指南',
    tabs: GUIDE_TABS,
    category: '',
    items: [],
    page: 1,
    total: 0,
    loading: false,
    finished: false,
    error: '',
  },

  onLoad(options) {
    const type = options && options.type === 'policy' ? 'policy' : 'guide'
    const category = (options && options.category) || ''
    this.setData({
      type,
      title: type === 'policy' ? '政策文件' : category === 'NOTICE' ? '公告' : '租房指南',
      tabs: type === 'policy' ? POLICY_TABS : GUIDE_TABS,
      category,
    })
    this.load(true)
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

  switchCategory(e) {
    const category = e.currentTarget.dataset.value || ''
    if (category === this.data.category) return
    this.setData({ category })
    this.load(true)
  },

  load(reset) {
    if (this.data.loading) return Promise.resolve()
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true, error: '' })
    const params = { page, size: PAGE_SIZE }
    if (this.data.category) params.category = this.data.category
    const request = this.data.type === 'policy' ? listPolicies : listGuides
    const decorate = this.data.type === 'policy' ? decoratePolicy : decorateGuide
    return request(params)
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
    if (!id) return
    wx.navigateTo({ url: '/pages/content/detail/index?type=' + this.data.type + '&id=' + id })
  },
})
