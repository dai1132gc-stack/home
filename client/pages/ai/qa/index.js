const { policyAnswer } = require('../../../api/ai')

const MAX_QUESTION = 200

Page({
  data: {
    question: '',
    loading: false,
    error: '',
    answer: '',
    citations: [],
    insufficient: false,
    disclaimer: '',
    asked: false,
  },

  onInput(e) {
    this.setData({ question: (e.detail.value || '').slice(0, MAX_QUESTION) })
  },

  submit() {
    if (this.data.loading) return
    const question = (this.data.question || '').trim()
    if (!question) {
      this.setData({ error: '请输入政策相关问题' })
      return
    }
    this.setData({ loading: true, error: '', answer: '', citations: [], insufficient: false })
    policyAnswer(question)
      .then((resp) => {
        this.setData({
          asked: true,
          answer: resp.answer || '',
          citations: resp.citations || [],
          insufficient: !!resp.insufficient,
          disclaimer: resp.disclaimer || '',
        })
      })
      .catch((err) => {
        const code = err && err.code
        if (code === 'AI_50301') {
          this.setData({ error: '政策问答暂未开放，请稍后再试' })
        } else if (code === 'COMMON_42901') {
          this.setData({ error: '提问过于频繁或今日额度已满，请稍后再试' })
        } else if (code === 'AI_50201' || code === 'AI_50302') {
          this.setData({ error: '智能服务暂时不可用，请稍后重试' })
        } else {
          this.setData({ error: (err && err.message) || '网络异常，请稍后重试' })
        }
      })
      .then(() => this.setData({ loading: false }))
  },

  goPolicy(e) {
    const id = e.currentTarget.dataset.id
    if (id) wx.navigateTo({ url: '/pages/content/detail/index?type=policy&id=' + id })
  },
})
