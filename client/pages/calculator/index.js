const { calculateMortgage, calculateTax, getCalculatorConfig } = require('../../api/calculator')

const LOAN_TYPES = [
  { value: 'COMMERCIAL', label: '商业贷款' },
  { value: 'FUND', label: '公积金贷款' },
]

const METHODS = [
  { value: 'EQUAL_INSTALLMENT', label: '等额本息' },
  { value: 'EQUAL_PRINCIPAL', label: '等额本金' },
]

const YEARS = [5, 10, 15, 20, 25, 30]

function money(value) {
  return '¥' + Number(value || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function percent(value) {
  return Number(value || 0).toFixed(2) + '%'
}

Page({
  data: {
    tab: 'mortgage',
    loadingConfig: false,
    loanTypes: LOAN_TYPES,
    methods: METHODS,
    years: YEARS,
    loanTypeIndex: 0,
    methodIndex: 0,
    yearsIndex: 5,
    totalPrice: 1000000,
    downPayment: 300000,
    annualRate: '',
    rateHint: '',
    cities: [],
    cityIndex: 0,
    taxPrice: 1000000,
    taxArea: '',
    firstHome: true,
    computing: false,
    mortgage: null,
    tax: null,
    disclaimer: '',
  },

  onLoad() {
    this.loadConfig()
  },

  loadConfig() {
    this.setData({ loadingConfig: true })
    return getCalculatorConfig()
      .then((config) => {
        const cities = []
        ;(config.taxRules || []).forEach((rule) => {
          if (cities.indexOf(rule.city) < 0) cities.push(rule.city)
        })
        this.setData({
          cities,
          disclaimer: config.disclaimer || '',
          loadingConfig: false,
        })
        this.updateRateHint(config)
      })
      .catch(() => {
        this.setData({ loadingConfig: false })
        wx.showToast({ title: '测算配置加载失败', icon: 'none' })
      })
  },

  updateRateHint(config) {
    const loanType = LOAN_TYPES[this.data.loanTypeIndex].value
    const term = YEARS[this.data.yearsIndex]
    const source = config || this.__config
    if (config) this.__config = config
    if (!source) return
    const found = (source.rates || []).find((item) => item.loanType === loanType && item.termYears === term)
    this.setData({
      rateHint: found
        ? '配置费率 ' + percent(found.ratePercent) + '（' + found.source + '，生效 ' + found.effectiveDate + '）'
        : '该类型与年限暂无配置费率，请手工输入年利率',
    })
  },

  switchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab })
  },

  onLoanTypeChange(e) {
    this.setData({ loanTypeIndex: Number(e.detail.value) })
    this.updateRateHint()
  },

  onMethodChange(e) {
    this.setData({ methodIndex: Number(e.detail.value) })
  },

  onYearsChange(e) {
    this.setData({ yearsIndex: Number(e.detail.value) })
    this.updateRateHint()
  },

  onTotalPriceInput(e) {
    this.setData({ totalPrice: Number(e.detail.value) || 0 })
  },

  onDownPaymentInput(e) {
    this.setData({ downPayment: Number(e.detail.value) || 0 })
  },

  onRateInput(e) {
    this.setData({ annualRate: e.detail.value })
  },

  onCityChange(e) {
    this.setData({ cityIndex: Number(e.detail.value) })
  },

  onTaxPriceInput(e) {
    this.setData({ taxPrice: Number(e.detail.value) || 0 })
  },

  onTaxAreaInput(e) {
    this.setData({ taxArea: e.detail.value })
  },

  onFirstHomeChange(e) {
    this.setData({ firstHome: !!e.detail.value })
  },

  submitMortgage() {
    if (this.data.computing) return
    if (this.data.downPayment > this.data.totalPrice) {
      wx.showToast({ title: '首付不能大于房屋总价', icon: 'none' })
      return
    }
    const rateValue = String(this.data.annualRate || '').trim()
    const payload = {
      loanType: LOAN_TYPES[this.data.loanTypeIndex].value,
      repaymentMethod: METHODS[this.data.methodIndex].value,
      totalPrice: this.data.totalPrice,
      downPayment: this.data.downPayment,
      termYears: YEARS[this.data.yearsIndex],
    }
    if (rateValue) payload.annualRate = Number(rateValue) / 100
    this.setData({ computing: true })
    calculateMortgage(payload)
      .then((result) => {
        const yearly = (result.yearlySummary || []).map((row) =>
          Object.assign({}, row, {
            principalText: money(row.principal),
            interestText: money(row.interest),
            balanceText: money(row.endingBalance),
          }),
        )
        this.setData({
          computing: false,
          mortgage: Object.assign({}, result, {
            loanAmountText: money(result.loanAmount),
            firstMonthText: money(result.firstMonthPayment),
            lastMonthText: money(result.lastMonthPayment),
            totalInterestText: money(result.totalInterest),
            totalRepaymentText: money(result.totalRepayment),
            downPaymentText: money(result.downPayment),
            rateText: percent(result.annualRatePercent),
            rateSourceText: result.rateSource + (result.rateEffectiveDate ? '（生效 ' + result.rateEffectiveDate + '）' : ''),
            yearly,
          }),
        })
      })
      .catch((err) => {
        this.setData({ computing: false })
        wx.showToast({ title: (err && err.message) || '测算失败', icon: 'none' })
      })
  },

  submitTax() {
    if (this.data.computing) return
    const city = this.data.cities[this.data.cityIndex]
    if (!city) {
      wx.showToast({ title: '暂无可用城市规则', icon: 'none' })
      return
    }
    const payload = { city, price: this.data.taxPrice, firstHome: this.data.firstHome }
    const area = String(this.data.taxArea || '').trim()
    if (area) payload.area = Number(area)
    this.setData({ computing: true })
    calculateTax(payload)
      .then((result) => {
        const items = (result.items || []).map((item) =>
          Object.assign({}, item, {
            baseText: money(item.base),
            amountText: money(item.amount),
            rateText: percent(item.ratePercent),
            deductionText: money(item.quickDeduction),
          }),
        )
        this.setData({
          computing: false,
          tax: Object.assign({}, result, {
            priceText: money(result.price),
            totalTaxText: money(result.totalTax),
            items,
          }),
        })
      })
      .catch((err) => {
        this.setData({ computing: false })
        wx.showToast({ title: (err && err.message) || '测算失败', icon: 'none' })
      })
  },
})
