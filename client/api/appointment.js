const { get, post } = require('../utils/request')

/** 客户预约带看：提交、我的列表、取消、详情 */
function createAppointment(data) {
  return post('/appointments', data)
}

function listMyAppointments(params) {
  return get('/appointments', params)
}

function getAppointment(id) {
  return get('/appointments/' + id)
}

function cancelAppointment(id) {
  return post('/appointments/' + id + '/cancel', {})
}

module.exports = { createAppointment, listMyAppointments, getAppointment, cancelAppointment }
