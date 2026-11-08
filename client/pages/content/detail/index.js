const { getGuide, getPolicy } = require('../../../api/content')

function formatDate(value) {
  return value ? String(value).slice(0, 10) : '-'
}

Page({
  data: {
    type: 'guide',
    id: 0,
    loading: true,
    error: '',
    detail: null,
    categoryText: '',
    metaText: '',
  },

  onLoad(options) {
    const type = options && options.type === 'policy' ? 'policy' : 'guide'
    const id = Number(options && options.id)
    if (!id) {
      this.setData({ loading: false, error: '内容参数不正确' })
      return
    }
    this.setData({ type, id })
    this.load()
  },

  load() {
    this.setData({ loading: true, error: '' })
    const request = this.data.type === 'policy' ? getPolicy : getGuide
    return request(this.data.id)
      .then((detail) => {
        const categoryText =
          this.data.type === 'policy'
            ? detail.docTypeLabel
            : detail.categoryLabel
        const metaText =
          this.data.type === 'policy'
            ? [detail.docNo, detail.source, detail.publishDate ? '发布 ' + formatDate(detail.publishDate) : '']
                .filter(Boolean)
                .join(' · ')
            : '更新于 ' + formatDate(detail.updatedAt)
        this.setData({ detail, categoryText, metaText, loading: false })
      })
      .catch((err) => {
        this.setData({ loading: false, error: (err && err.message) || '内容不存在或已下架' })
      })
  },

  goBack() {
    wx.navigateBack({ delta: 1 })
  },
})
