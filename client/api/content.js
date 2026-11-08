const { get } = require('../utils/request')

/** 教程 / 网签指引 / 公告 / 政策：公开只读已发布 */
function listGuides(params) {
  return get('/guides', params)
}

function getGuide(id) {
  return get('/guides/' + id)
}

function listPolicies(params) {
  return get('/policies', params)
}

function getPolicy(id) {
  return get('/policies/' + id)
}

module.exports = { listGuides, getGuide, listPolicies, getPolicy }
