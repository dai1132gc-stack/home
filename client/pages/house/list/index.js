const { getUser, getAccessToken } = require('../../../store/auth')
const { listFavorites, listHouses } = require('../../../api/house')
const { resolveMediaUrl } = require('../../../utils/media')

const PAGE_SIZE = 10

function formatMoney(value) {
  const num = Number(value || 0)
  return String(num).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function decorate(item) {
  const isRent = item.houseType === 'RENT'
  return Object.assign({}, item, {
    cover: resolveMediaUrl(item.coverUrl),
    priceText: (isRent ? '¥' : '¥') + formatMoney(item.price) + (isRent ? '/月' : ''),
    typeText: isRent ? '出租' : '出售',
    areaText: Number(item.area || 0) + '㎡',
    layoutText: item.rooms + '室' + (item.bathrooms || 0) + '卫',
  })
}

Page({
  data: {
    tab: 'all',
    loggedIn: false,
    houseType: '',
    sort: 'created_desc',
    items: [],
    page: 1,
    total: 0,
    loading: false,
    finished: false,
    error: '',
    user: null,
  },

  onLoad(options) {
    const tab = options && options.tab === 'fav' ? 'fav' : 'all'
    const loggedIn = !!getAccessToken()
    this.setData({ tab: tab === 'fav' && !loggedIn ? 'all' : tab, loggedIn, user: getUser() })
    this.load(true)
  },

  onShow() {
    const loggedIn = !!getAccessToken()
    if (loggedIn !== this.data.loggedIn) {
      this.setData({ loggedIn, user: getUser() })
      if (this.data.tab === 'fav') this.load(true)
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

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.tab) return
    if (tab === 'fav' && !this.data.loggedIn) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ tab })
    this.load(true)
  },

  onTypeChange(e) {
    const houseType = e.currentTarget.dataset.type || ''
    if (houseType === this.data.houseType) return
    this.setData({ houseType })
    this.load(true)
  },

  onSortChange() {
    const next = this.data.sort === 'created_desc' ? 'price_asc' : 'created_desc'
    this.setData({ sort: next })
    this.load(true)
  },

  load(reset) {
    if (this.data.loading) return Promise.resolve()
    const page = reset ? 1 : this.data.page + 1
    const favMode = this.data.tab === 'fav'
    if (favMode && !getAccessToken()) return Promise.resolve()

    this.setData({ loading: true, error: '' })
    const params = favMode
      ? { page, size: PAGE_SIZE }
      : { page, size: PAGE_SIZE, sort: this.data.sort }
    if (!favMode && this.data.houseType) params.houseType = this.data.houseType

    const task = favMode ? listFavorites(params) : listHouses(params)
    return task
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
        this.setData({
          loading: false,
          error: (err && err.message) || '加载失败，请稍后重试',
        })
      })
  },

  retry() {
    this.load(true)
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/house/detail/index?id=' + id })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },
})
