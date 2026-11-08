/**
 * W2 小程序逻辑冒烟：用 Node 模拟 wx 环境，直接驱动房源列表/详情页逻辑，
 * 验证模块解析、接口调用、游客浏览与登录后收藏（不含微信开发者工具渲染）。
 * 前置：后端 8080 已启动，数据库有至少 1 条 PUBLISHED 房源。
 * 用法：node docs/mini-logic-smoke.mjs
 */
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'

const CLIENT_ROOT = 'D:/graduate/home/client/'
const require = createRequire(pathToFileURL(CLIENT_ROOT + 'index.js').href)
const API = 'http://localhost:8080/api/v1'

const storage = new Map()
const calls = { navigate: [], toast: [] }

globalThis.wx = {
  getStorageSync: (k) => (storage.has(k) ? storage.get(k) : ''),
  setStorageSync: (k, v) => storage.set(k, v),
  removeStorageSync: (k) => storage.delete(k),
  showToast: (opt) => calls.toast.push(opt.title),
  navigateTo: (opt) => calls.navigate.push(opt.url),
  navigateBack: () => calls.navigate.push('back'),
  reLaunch: (opt) => calls.navigate.push(opt.url),
  previewImage: () => {},
  stopPullDownRefresh: () => {},
  request: (opt) => {
    const url = opt.url.startsWith('http') ? opt.url : API + opt.url
    const query = opt.method === 'GET' && opt.data
      ? '?' + new URLSearchParams(opt.data).toString()
      : ''
    fetch(url + query, {
      method: opt.method || 'GET',
      headers: opt.header || {},
      body: opt.method && opt.method !== 'GET' ? JSON.stringify(opt.data || {}) : undefined,
    })
      .then(async (res) => {
        const data = await res.json()
        opt.success({ statusCode: res.status, data })
      })
      .catch((err) => opt.fail && opt.fail(err))
  },
}

let captured = null
globalThis.Page = (config) => {
  captured = config
}
globalThis.getCurrentPages = () => [{ route: 'pages/house/list/index' }]

function instantiate(config) {
  const instance = { data: JSON.parse(JSON.stringify(config.data || {})) }
  instance.setData = (patch, cb) => {
    Object.assign(instance.data, patch)
    if (cb) cb()
  }
  for (const [key, value] of Object.entries(config)) {
    if (typeof value === 'function') instance[key] = value
  }
  return instance
}

function loadPage(path) {
  captured = null
  const resolved = require.resolve(path)
  delete require.cache[resolved]
  require(path)
  if (!captured) throw new Error('Page not registered: ' + path)
  return instantiate(captured)
}

function assert(condition, message) {
  if (!condition) throw new Error('ASSERT FAILED: ' + message)
}

const auth = require('./store/auth.js')

async function loginCustomer() {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'customer', password: 'Customer@123456', clientType: 'MINI' }),
  })
  const body = await res.json()
  assert(body.code === '0', 'customer login: ' + body.message)
  auth.setAccessToken(body.data.accessToken)
  auth.setRefreshToken(body.data.refreshToken)
  auth.setUser(body.data.user)
  return body.data.user
}

async function main() {
  auth.clearAuth()

  // 1. 游客浏览
  const list = loadPage('./pages/house/list/index.js')
  list.setData({ loggedIn: false })
  await list.load(true)
  assert(!list.data.error, 'guest list error: ' + list.data.error)
  assert(list.data.items.length > 0, 'guest list should contain published houses')
  const first = list.data.items[0]
  assert(first.priceText.startsWith('¥'), 'price formatted')
  assert(first.cover.startsWith('http://localhost:8080/uploads/'), 'cover resolved to absolute url')
  console.log(`游客列表 OK：total=${list.data.total} 首条=${first.title} ${first.priceText}`)

  // 2. 游客详情
  const guestDetail = loadPage('./pages/house/detail/index.js')
  guestDetail.setData({ id: first.id })
  await guestDetail.load()
  assert(!guestDetail.data.error, 'guest detail error: ' + guestDetail.data.error)
  assert(guestDetail.data.images.length > 0, 'detail images resolved')
  assert(guestDetail.data.favorited === false, 'guest not favorited')
  console.log(`游客详情 OK：${guestDetail.data.house.title} 图片=${guestDetail.data.images.length}`)

  // 3. 游客点收藏 → 跳登录
  guestDetail.toggleFavorite()
  await new Promise((r) => setTimeout(r, 800))
  assert(calls.navigate.some((u) => u.includes('/pages/login/login')), 'guest favorite should redirect to login')
  console.log('游客收藏拦截 OK：跳转登录页')

  // 4. 登录客户 → 收藏
  const user = await loginCustomer()
  console.log('客户登录 OK：', user.username)
  const detail = loadPage('./pages/house/detail/index.js')
  detail.setData({ id: first.id })
  await detail.load()
  assert(detail.data.favorited === false, 'should not be favorited initially')
  detail.toggleFavorite()
  await new Promise((r) => setTimeout(r, 800))
  assert(detail.data.favorited === true, 'favorite should be true after toggle')
  console.log('收藏 OK：', calls.toast[calls.toast.length - 1])

  // 5. 我的收藏列表
  const favList = loadPage('./pages/house/list/index.js')
  favList.setData({ loggedIn: true, tab: 'fav' })
  await favList.load(true)
  assert(favList.data.items.length === 1, 'favorites should contain 1 item, got ' + favList.data.items.length)
  console.log(`我的收藏 OK：total=${favList.data.total}`)

  // 6. 取消收藏
  detail.toggleFavorite()
  await new Promise((r) => setTimeout(r, 800))
  assert(detail.data.favorited === false, 'favorite should be false after cancel')
  await favList.load(true)
  assert(favList.data.items.length === 0, 'favorites should be empty after cancel')
  console.log('取消收藏 OK：', calls.toast[calls.toast.length - 1])

  // 7. 首页入口
  const index = loadPage('./pages/index/index.js')
  index.goHouses({ currentTarget: { dataset: { tab: 'all' } } })
  assert(calls.navigate.some((u) => u.includes('/pages/house/list/index?tab=all')), 'index entry to houses')
  console.log('首页房源入口 OK')

  console.log('== 小程序逻辑冒烟全部通过 ==')
}

main().then(() => process.exit(0)).catch((e) => {
  console.error('MINI SMOKE FAILED:', e.message)
  process.exit(1)
})
