const { get, post } = require('../utils/request')

/** 房贷与税费测算（公开接口，后端按 IP 限流） */
function getCalculatorConfig() {
  return get('/calculators/config')
}

function calculateMortgage(payload) {
  return post('/calculators/mortgage', payload)
}

function calculateTax(payload) {
  return post('/calculators/tax', payload)
}

module.exports = { getCalculatorConfig, calculateMortgage, calculateTax }
