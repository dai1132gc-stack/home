const { getAccessToken } = require('../../../store/auth')
const { confirmContract, downloadContractPdf, getContract } = require('../../../api/contract')

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

function formatDate(value) {
  return value ? String(value).slice(0, 10) : '-'
}

function formatMoney(value) {
  return '¥' + Number(value || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

Page({
  data: {
    id: 0,
    loading: true,
    error: '',
    contract: null,
    amountText: '',
    depositText: '',
    periodText: '',
    signedText: '-',
    logs: [],
    acting: false,
  },

  onLoad(options) {
    const id = Number(options && options.id)
    if (!id) {
      this.setData({ loading: false, error: '合同参数不正确' })
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
    return getContract(this.data.id)
      .then((contract) => {
        const logs = (contract.statusLogs || []).map((log) =>
          Object.assign({}, log, { timeText: formatDateTime(log.createdAt) })
        )
        this.setData({
          contract,
          logs,
          amountText: formatMoney(contract.amount),
          depositText: formatMoney(contract.deposit),
          periodText: formatDate(contract.startDate) + ' 至 ' + formatDate(contract.endDate),
          signedText: formatDateTime(contract.signedAt),
          loading: false,
        })
      })
      .catch((err) => {
        this.setData({ loading: false, error: (err && err.message) || '合同不存在或无权限查看' })
      })
  },

  retry() {
    this.load()
  },

  confirm() {
    if (this.data.acting) return
    const contract = this.data.contract
    if (!contract) return
    wx.showModal({
      title: '确认签署',
      content: '确认后合同立即生效，房源将标记为已签约，请核对双方信息与金额。',
      success: (res) => {
        if (!res.confirm) return
        this.setData({ acting: true })
        confirmContract(contract.id)
          .then(() => {
            wx.showToast({ title: '合同已生效', icon: 'success' })
            this.setData({ acting: false })
            this.load()
          })
          .catch((err) => {
            this.setData({ acting: false })
            wx.showToast({ title: (err && err.message) || '确认失败', icon: 'none' })
          })
      },
    })
  },

  download() {
    if (this.data.acting) return
    this.setData({ acting: true })
    downloadContractPdf(this.data.id)
      .then(() => this.setData({ acting: false }))
      .catch((err) => {
        this.setData({ acting: false })
        wx.showToast({ title: (err && err.message) || '下载失败', icon: 'none' })
      })
  },

  goRepair() {
    const contract = this.data.contract
    if (!contract) return
    wx.navigateTo({ url: '/pages/repair/create/index?contractId=' + contract.id })
  },

  goBack() {
    wx.navigateBack({ delta: 1 })
  },
})
