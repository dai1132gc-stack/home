const { post } = require('../utils/request')

/**
 * W6 智能服务公开接口：游客可用，不改变登录态。
 * 降级/拒答由响应字段表达（degraded/insufficient），不抛错；503/429 由 request 抛错携带 code。
 */
function houseSearch(query, page, size) {
  return post('/ai/house-search', { query: query, page: page || 1, size: size || 10 })
}

function policyAnswer(question) {
  return post('/ai/policy-answer', { question: question })
}

module.exports = { houseSearch, policyAnswer }
