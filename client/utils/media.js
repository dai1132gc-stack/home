const { env } = require('../store/auth')

/** 后端返回的媒体为相对路径（/uploads/...），小程序需要绝对地址 */
function origin() {
  return String(env.apiBaseUrl || '').replace(/\/api\/v1\/?$/, '')
}

function resolveMediaUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return origin() + (url.charAt(0) === '/' ? url : '/' + url)
}

module.exports = { resolveMediaUrl, origin }
