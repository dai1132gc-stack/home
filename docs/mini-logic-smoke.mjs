/**
 * W2+W3 小程序逻辑冒烟：用 Node 模拟 wx 环境，直接驱动页面逻辑。
 * 覆盖：房源列表/详情/收藏（W2）、预约看房/我的预约/取消、我的合同/确认签署/PDF 下载（W3）。
 * 不含微信开发者工具渲染（Skyline 渲染需 GUI 验证）。
 * 前置：后端 8080 已启动，MySQL/Redis 可用。
 * 用法：node docs/mini-logic-smoke.mjs
 */
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'

const CLIENT_ROOT = 'D:/graduate/home/client/'
const require = createRequire(pathToFileURL(CLIENT_ROOT + 'index.js').href)
const API = 'http://localhost:8080/api/v1'

const storage = new Map()
const calls = { navigate: [], toast: [], openDocument: [], downloaded: 0 }

globalThis.wx = {
  getStorageSync: (k) => (storage.has(k) ? storage.get(k) : ''),
  setStorageSync: (k, v) => storage.set(k, v),
  removeStorageSync: (k) => storage.delete(k),
  showToast: (opt) => calls.toast.push(opt.title),
  showModal: (opt) => opt.success && opt.success({ confirm: true }),
  navigateTo: (opt) => calls.navigate.push(opt.url),
  redirectTo: (opt) => calls.navigate.push(opt.url),
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
  downloadFile: (opt) => {
    fetch(opt.url, { headers: opt.header || {} })
      .then(async (res) => {
        const buffer = Buffer.from(await res.arrayBuffer())
        calls.downloaded = buffer.length
        if (res.status !== 200) {
          opt.success({ statusCode: res.status, tempFilePath: '' })
          return
        }
        const head = buffer.subarray(0, 4).toString('latin1')
        if (head !== '%PDF') throw new Error('downloaded file is not PDF')
        opt.success({ statusCode: 200, tempFilePath: '/tmp/contract.pdf' })
      })
      .catch((err) => opt.fail && opt.fail(err))
  },
  openDocument: (opt) => {
    calls.openDocument.push(opt.filePath)
    opt.success && opt.success()
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const auth = require('./store/auth.js')

async function apiLogin(username, password, clientType) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, clientType }),
  })
  const body = await res.json()
  assert(body.code === '0', `login ${username}: ` + body.message)
  return body.data
}

async function loginCustomer() {
  const data = await apiLogin('customer', 'Customer@123456', 'MINI')
  auth.setAccessToken(data.accessToken)
  auth.setRefreshToken(data.refreshToken)
  auth.setUser(data.user)
  return data.user
}

async function adminCall(path, method = 'GET', body) {
  const login = await apiLogin('admin', 'Admin@123456', 'WEB')
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + login.accessToken,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const data = await res.json()
  return { status: res.status, body: data }
}

async function preparePublishedHouse(stamp) {
  const owner = await adminCall('/owners', 'POST', {
    name: '小程序冒烟房东' + stamp,
    phone: '13800138002',
    type: 'LANDLORD',
  })
  assert(owner.body.code === '0', 'create owner: ' + owner.body.message)
  const ownerId = owner.body.data.id
  const house = await adminCall('/houses', 'POST', {
    title: '小程序冒烟房源' + stamp,
    houseType: 'RENT',
    community: '冒烟小区',
    region: '浦东新区',
    address: '上海市浦东新区冒烟路 1 弄 2 号 301 室',
    price: 4800,
    area: 88,
    rooms: 2,
    bathrooms: 1,
    floor: '3/6',
    ownerId,
  })
  assert(house.body.code === '0', 'create house: ' + house.body.message)
  const houseId = house.body.data.id
  const published = await adminCall(`/houses/${houseId}/publish`, 'POST')
  assert(published.body.code === '0', 'publish house: ' + published.body.message)
  return houseId
}

function tomorrow() {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

async function main() {
  auth.clearAuth()
  const stamp = Date.now().toString().slice(-6)
  const houseId = await preparePublishedHouse(stamp)
  console.log(`准备数据 OK：houseId=${houseId}`)

  // ============ W2 回归：游客浏览、收藏 ============
  const list = loadPage('./pages/house/list/index.js')
  list.setData({ loggedIn: false })
  await list.load(true)
  assert(!list.data.error, 'guest list error: ' + list.data.error)
  assert(list.data.items.length > 0, 'guest list should contain published houses')
  const first = list.data.items.find((item) => item.id === houseId) || list.data.items[0]
  assert(first.priceText.startsWith('¥'), 'price formatted')
  assert(first.cover === '' || first.cover.startsWith('http://localhost:8080/uploads/'), 'cover resolved')
  console.log(`游客列表 OK：total=${list.data.total} 首条=${first.title} ${first.priceText}`)

  const guestDetail = loadPage('./pages/house/detail/index.js')
  guestDetail.setData({ id: first.id })
  await guestDetail.load()
  assert(!guestDetail.data.error, 'guest detail error: ' + guestDetail.data.error)
  assert(guestDetail.data.favorited === false, 'guest not favorited')
  guestDetail.toggleFavorite()
  await sleep(800)
  assert(calls.navigate.some((u) => u.includes('/pages/login/login')), 'guest favorite redirects login')
  console.log('游客详情/收藏拦截 OK')

  const user = await loginCustomer()
  console.log('客户登录 OK：', user.username)

  const detail = loadPage('./pages/house/detail/index.js')
  detail.setData({ id: houseId })
  await detail.load()
  detail.toggleFavorite()
  await sleep(600)
  assert(detail.data.favorited === true, 'favorite should be true after toggle')
  detail.toggleFavorite()
  await sleep(600)
  assert(detail.data.favorited === false, 'favorite should be false after cancel')
  console.log('收藏/取消收藏 OK')

  // ============ W3：预约看房 ============
  const createPage = loadPage('./pages/appointment/create/index.js')
  createPage.setData({ houseId, house: detail.data.house, date: tomorrow(), time: '10:00', phone: '13912340000' })
  createPage.submit()
  await sleep(1200)
  assert(
    calls.navigate.some((u) => u.includes('/pages/appointment/list/index')),
    'appointment create should redirect to list',
  )
  console.log('提交预约 OK：跳转我的预约')

  const apptList = loadPage('./pages/appointment/list/index.js')
  apptList.setData({ loggedIn: true })
  await apptList.load(true)
  assert(apptList.data.items.length >= 1, 'appointment list should contain 1 item')
  const appointment = apptList.data.items.find((item) => item.houseId === houseId && item.status === 'PENDING')
  assert(appointment, 'new appointment should be PENDING')
  assert(appointment.contactPhone.includes('****'), 'customer phone should be masked: ' + appointment.contactPhone)
  console.log(`我的预约 OK：${appointment.houseTitle} ${appointment.timeText} ${appointment.statusText}`)

  apptList.cancel({ currentTarget: { dataset: { id: appointment.id } } })
  await sleep(900)
  await apptList.load(true)
  assert(
    apptList.data.items.find((item) => item.id === appointment.id).status === 'CANCELLED',
    'appointment should be CANCELLED',
  )
  console.log('取消预约 OK')

  // 再次预约并由 ADMIN 确认，为合同准备数据
  const createAgain = loadPage('./pages/appointment/create/index.js')
  createAgain.setData({ houseId, house: detail.data.house, date: tomorrow(), time: '14:00', phone: '13912340000' })
  createAgain.submit()
  await sleep(1200)
  await apptList.load(true)
  const confirmedTarget = apptList.data.items.find((item) => item.houseId === houseId && item.status === 'PENDING')
  assert(confirmedTarget, 'second appointment should exist')
  const confirmRes = await adminCall(`/appointments/${confirmedTarget.id}/confirm`, 'POST')
  assert(confirmRes.body.code === '0', 'admin confirm appointment: ' + confirmRes.body.message)
  await apptList.load(true)
  assert(apptList.data.items.find((item) => item.id === confirmedTarget.id).status === 'CONFIRMED', 'appointment CONFIRMED')
  console.log('ADMIN 确认预约 OK：房源置已预定')

  // ============ W3：合同 ============
  const customerId = user.id
  const contractCreate = await adminCall('/contracts', 'POST', {
    houseId,
    customerId,
    title: '小程序冒烟租赁合同' + stamp,
    amount: 4800,
    deposit: 9600,
    paymentCycle: 'MONTHLY',
    startDate: tomorrow(),
    endDate: new Date(new Date().setFullYear(new Date().getFullYear() + 1)).toISOString().slice(0, 10),
  })
  assert(contractCreate.body.code === '0', 'create contract: ' + contractCreate.body.message)
  const contractId = contractCreate.body.data.id
  const submitRes = await adminCall(`/contracts/${contractId}/submit`, 'POST')
  assert(submitRes.body.code === '0', 'submit contract: ' + submitRes.body.message)
  console.log(`合同起草/提交 OK：id=${contractId} 状态=${submitRes.body.data.status}`)

  const contractList = loadPage('./pages/contract/list/index.js')
  contractList.setData({ loggedIn: true })
  await contractList.load(true)
  const item = contractList.data.items.find((row) => row.id === contractId)
  assert(item, 'contract should appear in my contracts')
  assert(item.needSign === true, 'contract should need sign')
  console.log(`我的合同 OK：${item.contractNo} ${item.statusText} ${item.amountText}`)

  const contractDetail = loadPage('./pages/contract/detail/index.js')
  contractDetail.setData({ id: contractId })
  await contractDetail.load()
  assert(!contractDetail.data.error, 'contract detail error: ' + contractDetail.data.error)
  assert(contractDetail.data.contract.parties.length === 3, 'contract parties should be 3')
  contractDetail.confirm()
  await sleep(1200)
  assert(contractDetail.data.contract.status === 'ACTIVE', 'contract should be ACTIVE after confirm')
  const signNode = contractDetail.data.contract.transactions.find((t) => t.node === 'SIGN')
  assert(signNode.status === 'DONE', 'SIGN node should be auto DONE')
  console.log('确认签署 OK：合同生效，SIGN 节点已完成')

  contractDetail.download()
  await sleep(1500)
  assert(calls.openDocument.length === 1, 'openDocument should be called')
  assert(calls.downloaded > 20000, 'downloaded pdf too small: ' + calls.downloaded)
  console.log(`PDF 下载 OK：${calls.downloaded} bytes，已调用 openDocument`)

  // 首页入口
  const index = loadPage('./pages/index/index.js')
  index.goAppointments()
  index.goContracts()
  assert(calls.navigate.some((u) => u.includes('/pages/appointment/list/index')), 'index appointment entry')
  assert(calls.navigate.some((u) => u.includes('/pages/contract/list/index')), 'index contract entry')
  console.log('首页预约/合同入口 OK')

  console.log('== 小程序逻辑冒烟全部通过 ==')
}

main().then(() => process.exit(0)).catch((e) => {
  console.error('MINI SMOKE FAILED:', e.message)
  process.exit(1)
})
