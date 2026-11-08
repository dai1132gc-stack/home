const { getAccessToken } = require('../../../store/auth')
const {
  cancelRepair,
  closeRepair,
  downloadRepairImage,
  getRepair,
  reopenRepair,
} = require('../../../api/repair')

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

Page({
  data: {
    id: 0,
    loading: true,
    error: '',
    detail: null,
    logs: [],
    createdText: '-',
    visitText: '-',
    mediaPaths: {},
    imageUrls: [],
    acting: false,
    canCancel: false,
    canClose: false,
    canReopen: false,
  },

  onLoad(options) {
    const id = Number(options && options.id)
    if (!id) {
      this.setData({ loading: false, error: '报修参数不正确' })
      return
    }
    if (!getAccessToken()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => wx.navigateTo({ url: '/pages/login/login' }), 500)
      return
    }
    this.setData({ id })
    this.load()
  },

  load() {
    this.setData({ loading: true, error: '' })
    return getRepair(this.data.id)
      .then((detail) => {
        const logs = (detail.statusLogs || []).map((log) =>
          Object.assign({}, log, { timeText: formatDateTime(log.createdAt) }),
        )
        this.setData({
          detail,
          logs,
          createdText: formatDateTime(detail.createdAt),
          visitText: formatDateTime(detail.preferredVisitTime),
          canCancel: detail.status === 'SUBMITTED' || detail.status === 'ACCEPTED',
          canClose: detail.status === 'RESOLVED',
          canReopen: detail.status === 'RESOLVED' || detail.status === 'CLOSED',
          loading: false,
        })
        this.loadImages(detail)
      })
      .catch((err) => {
        this.setData({ loading: false, error: (err && err.message) || '报修不存在或无权限查看' })
      })
  },

  retry() {
    this.load()
  },

  /** 私有图片：逐张带 Token 下载到临时文件，下载完成后再渲染 */
  loadImages(detail) {
    const mediaPaths = {}
    const tasks = (detail.media || []).map((media) =>
      downloadRepairImage(detail.id, media.id)
        .then((path) => {
          mediaPaths[media.id] = path
        })
        .catch(() => {}),
    )
    Promise.all(tasks).then(() => {
      const imageUrls = Object.keys(mediaPaths).map((key) => mediaPaths[key])
      this.setData({ mediaPaths, imageUrls })
    })
  },

  previewImage(e) {
    const current = e.currentTarget.dataset.url
    if (!current) return
    wx.previewImage({ current, urls: this.data.imageUrls })
  },

  act(e) {
    const action = e.currentTarget.dataset.action
    if (!action || this.data.acting) return
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
          .run(this.data.id)
          .then(() => {
            wx.showToast({ title: '操作成功', icon: 'none' })
            this.setData({ acting: false })
            this.load()
          })
          .catch((err) => {
            this.setData({ acting: false })
            wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' })
          })
      },
    })
  },

  goList() {
    wx.redirectTo({ url: '/pages/repair/list/index' })
  },

  goBack() {
    wx.navigateBack({ delta: 1 })
  },
})
