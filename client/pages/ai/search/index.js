const { houseSearch } = require('../../../api/ai')
const { resolveMediaUrl } = require('../../../utils/media')

const PAGE_SIZE = 10
const MAX_QUERY = 200

const DEGRADE_TEXT = {
  AI_DISABLED: '智能解析未启用，已按最新发布展示',
  AI_TIMEOUT: '智能解析超时，已按最新发布展示',
  AI_UPSTREAM: '智能服务暂时不可用，已按最新发布展示',
  AI_INVALID_JSON: '智能解析失败，已按最新发布展示',
  AI_INVALID_INTENT: '未识别出有效条件，已按最新发布展示',
}

const CONDITION_LABELS = {
  houseType: function (v) { return v === 'RENT' ? '出租' : '出售' },
  region: function (v) { return '区域：' + v },
  keyword: function (v) { return '关键词：' + v },
  minPrice: function (v) { return '最低价 ' + v },
  maxPrice: function (v) { return '最高价 ' + v },
  rooms: function (v) { return v + ' 室' },
  sort: function (v) {
    return { created_desc: '最新发布', price_asc: '价格从低到高', price_desc: '价格从高到低', area_asc: '面积从小到大', area_desc: '面积从大到小' }[v] || v
  },
}

function formatMoney(value) {
  return String(Number(value || 0)).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function decorate(item) {
  const isRent = item.houseType === 'RENT'
  return Object.assign({}, item, {
    cover: resolveMediaUrl(item.coverUrl),
    priceText: '¥' + formatMoney(item.price) + (isRent ? '/月' : ''),
    typeText: isRent ? '出租' : '出售',
    areaText: Number(item.area || 0) + '㎡',
    layoutText: item.rooms + '室' + (item.bathrooms || 0) + '卫',
  })
}

Page({
  data: {
    query: '',
    items: [],
    conditions: [],
    degraded: false,
    degradeText: '',
    page: 1,
    total: 0,
    loading: false,
    finished: false,
    error: '',
    searched: false,
  },

  onInput(e) {
    this.setData({ query: (e.detail.value || '').slice(0, MAX_QUERY) })
  },

  submit() {
    if (this.data.loading) return
    const query = (this.data.query || '').trim()
    if (!query) {
      this.setData({ error: '请输入找房需求，如：朝阳三室一厅 5000 以内' })
      return
    }
    this.setData({ error: '' })
    this.load(true)
  },

  load(reset) {
    if (this.data.loading) return
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true, error: '' })
    houseSearch((this.data.query || '').trim(), page, PAGE_SIZE)
      .then((resp) => {
        const list = (resp.items || []).map(decorate)
        const items = reset ? list : this.data.items.concat(list)
        const conditions = []
        const parsed = resp.parsedConditions || {}
        Object.keys(CONDITION_LABELS).forEach(function (key) {
          if (parsed[key] !== undefined && parsed[key] !== null && parsed[key] !== '') {
            conditions.push({ key: key, text: CONDITION_LABELS[key](parsed[key]) })
          }
        })
        this.setData({
          items: items,
          conditions: conditions,
          degraded: !!resp.degraded,
          degradeText: resp.degraded ? (DEGRADE_TEXT[resp.degradedReason] || '已降级为普通列表') : '',
          page: page,
          total: resp.total || 0,
          finished: items.length >= (resp.total || 0) || list.length < PAGE_SIZE,
          searched: true,
        })
      })
      .catch((err) => {
        this.setData({ error: (err && err.message) || '搜索失败，请稍后重试' })
      })
      .then(() => this.setData({ loading: false }))
  },

  onReachBottom() {
    if (this.data.searched && !this.data.finished && !this.data.loading) {
      this.load(false)
    }
  },

  retry() {
    this.load(true)
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    if (id) wx.navigateTo({ url: '/pages/house/detail/index?id=' + id })
  },
})
