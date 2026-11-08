const { getAccessToken } = require('../../../store/auth')
const { addFavorite, getHouse, removeFavorite } = require('../../../api/house')
const { resolveMediaUrl } = require('../../../utils/media')

function formatMoney(value) {
  const num = Number(value || 0)
  return String(num).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

Page({
  data: {
    id: null,
    loading: true,
    error: '',
    house: null,
    images: [],
    priceText: '',
    typeText: '',
    favorited: false,
    favoriting: false,
  },

  onLoad(options) {
    const id = Number(options && options.id)
    if (!id) {
      this.setData({ loading: false, error: '房源参数不正确' })
      return
    }
    this.setData({ id })
    this.load()
  },

  load() {
    this.setData({ loading: true, error: '' })
    return getHouse(this.data.id)
      .then((house) => {
        const isRent = house.houseType === 'RENT'
        const images = (house.media || [])
          .filter((m) => m.mediaType === 'IMAGE')
          .map((m) => resolveMediaUrl(m.url))
        this.setData({
          house,
          images,
          priceText: '¥' + formatMoney(house.price) + (isRent ? '/月' : ''),
          typeText: isRent ? '出租' : '出售',
          favorited: !!house.favorited,
          loading: false,
        })
      })
      .catch((err) => {
        this.setData({ loading: false, error: (err && err.message) || '房源不存在或已下架' })
      })
  },

  previewImage(e) {
    const current = e.currentTarget.dataset.url
    if (!current) return
    wx.previewImage({ current, urls: this.data.images })
  },

  toggleFavorite() {
    if (this.data.favoriting) return
    if (!getAccessToken()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => {
        wx.navigateTo({ url: '/pages/login/login' })
      }, 600)
      return
    }
    const id = this.data.id
    const wasFavorited = this.data.favorited
    this.setData({ favoriting: true })
    const task = wasFavorited ? removeFavorite(id) : addFavorite(id)
    task
      .then(() => {
        this.setData({ favorited: !wasFavorited, favoriting: false })
        wx.showToast({ title: wasFavorited ? '已取消收藏' : '已收藏', icon: 'none' })
      })
      .catch((err) => {
        this.setData({ favoriting: false })
        wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' })
      })
  },

  goBack() {
    wx.navigateBack({ delta: 1 })
  },
})
