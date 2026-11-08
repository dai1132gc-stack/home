const { get, post, request } = require('../utils/request')

/**
 * 房源公开接口：游客即可访问，返回仅 PUBLISHED 房源。
 * 收藏接口需要 CUSTOMER 登录态，401 时由 request 统一刷新或跳登录。
 */
function listHouses(params) {
  return get('/houses', params)
}

function getHouse(id) {
  return get('/houses/' + id)
}

function addFavorite(id) {
  return post('/houses/' + id + '/favorite', {})
}

function removeFavorite(id) {
  return request({ url: '/houses/' + id + '/favorite', method: 'DELETE' })
}

function listFavorites(params) {
  return get('/me/favorites', params)
}

module.exports = { listHouses, getHouse, addFavorite, removeFavorite, listFavorites }
