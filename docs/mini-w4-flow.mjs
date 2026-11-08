/**
 * W4 小程序逻辑冒烟：Node 模拟 wx，驱动报修、内容政策与测算页面逻辑。
 * 覆盖：报修提交（含 wx.chooseMedia/wx.uploadFile 私有图片上传）、我的报修、
 *      详情鉴权图片下载、关闭/重开/撤销、教程/政策列表与详情、房贷/税费测算、首页入口。
 * 不含微信开发者工具渲染（Skyline 需 GUI 验证）。
 * 前置：后端 8080、MySQL、Redis 已启动。
 * 用法：node docs/mini-w4-flow.mjs
 */
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'
import fs from 'node:fs'

const CLIENT_ROOT = 'D:/graduate/home/client/'
const require = createRequire(pathToFileURL(CLIENT_ROOT + 'index.js').href)
const API = 'http://localhost:8080/api/v1'

function readEnv(key) {
  const text = fs.readFileSync('D:/graduate/home/realestate-server/.env', 'utf8')
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith(key + '=')) return line.slice(key.length + 1).trim()
  }
  throw new Error('missing env ' + key)
}

const ADMIN_PASSWORD = readEnv('BOOTSTRAP_ADMIN_PASSWORD')
const CUSTOMER_PASSWORD = readEnv('BOOTSTRAP_CUSTOMER_PASSWORD')

const JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==',
  'base64',
)
fs.writeFileSync('/tmp/w4-mini-1.jpg', JPEG)
fs.writeFileSync('/tmp/w4-mini-2.jpg', JPEG)

const storage = new Map()
const calls = { navigate: [], toast: [], previews: [], downloads: 0, uploads: 0 }

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
  previewImage: (opt) => calls.previews.push(opt.current),
  stopPullDownRefresh: () => {},
  chooseMedia: (opt) => {
    opt.success({ tempFiles: [{ tempFilePath: '/tmp/w4-mini-1.jpg' }, { tempFilePath: '/tmp/w4-mini-2.jpg' }] })
  },
  request: (opt) => {
    const url = opt.url.startsWith('http') ? opt.url : API + opt.url
    const query =
      opt.method === 'GET' && opt.data ? '?' + new URLSearchParams(opt.data).toString() : ''
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
  uploadFile: (opt) => {
    const buffer = fs.readFileSync(opt.filePath)
    const form = new FormData()
    form.append(opt.name || 'file', new Blob([buffer], { type: 'image/jpeg' }), 'repair.jpg')
    fetch(opt.url, { method: 'POST', headers: opt.header || {}, body: form })
      .then(async (res) => {
        calls.uploads += 1
        const text = await res.text()
        opt.success({ statusCode: res.status, data: text })
      })
      .catch((err) => opt.fail && opt.fail(err))
  },
  downloadFile: (opt) => {
    fetch(opt.url, { headers: opt.header || {} })
      .then(async (res) => {
        const buffer = Buffer.from(await res.arrayBuffer())
        if (res.status !== 200) {
          opt.success({ statusCode: res.status, tempFilePath: '' })
          return
        }
        const type = res.headers.get('content-type') || ''
        if (!type.startsWith('image/')) throw new Error('downloaded file is not image: ' + type)
        calls.downloads += 1
        const path = '/tmp/w4-media-' + calls.downloads + '.jpg'
        fs.writeFileSync(path, buffer)
        opt.success({ statusCode: 200, tempFilePath: path })
      })
      .catch((err) => opt.fail && opt.fail(err))
  },
}

let captured = null
globalThis.Page = (config) => {
  captured = config
}
globalThis.getCurrentPages = () => [{ route: 'pages/index/index' }]

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

async function adminCall(path, method = 'GET', body) {
  const login = await apiLogin('admin', ADMIN_PASSWORD, 'WEB')
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

async function customerCall(path, method = 'GET', body) {
  const login = await apiLogin('customer', CUSTOMER_PASSWORD, 'MINI')
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

async function prepareHouse(stamp) {
  const owner = await adminCall('/owners', 'POST', {
    name: 'W4小程序房东' + stamp,
    phone: '13800138005',
    type: 'LANDLORD',
  })
  assert(owner.body.code === '0', 'create owner: ' + owner.body.message)
  const house = await adminCall('/houses', 'POST', {
    title: 'W4小程序报修房源' + stamp,
    houseType: 'RENT',
    community: '报修花园',
    region: '浦东新区',
    address: '上海市浦东新区报修路 18 号 502 室',
    price: 5200,
    area: 90,
    rooms: 2,
    bathrooms: 1,
    ownerId: owner.body.data.id,
  })
  assert(house.body.code === '0', 'create house: ' + house.body.message)
  const published = await adminCall(`/houses/${house.body.data.id}/publish`, 'POST')
  assert(published.body.code === '0', 'publish house: ' + published.body.message)
  return house.body.data.id
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
  const houseId = await prepareHouse(stamp)
  const login = await apiLogin('customer', CUSTOMER_PASSWORD, 'MINI')
  auth.setAccessToken(login.accessToken)
  auth.setRefreshToken(login.refreshToken)
  auth.setUser(login.user)
  console.log(`准备数据 OK：houseId=${houseId} customer=${login.user.username}`)

  // ============ 报修提交（含图片上传） ============
  const createPage = loadPage('./pages/repair/create/index.js')
  createPage.onLoad({ houseId: String(houseId) })
  await sleep(800)
  assert(!createPage.data.error, 'repair create load error: ' + createPage.data.error)
  assert(createPage.data.house && createPage.data.house.id === houseId, 'house loaded')
  createPage.chooseImage()
  assert(createPage.data.images.length === 2, 'chooseMedia should append 2 images')
  createPage.setData({
    title: '厨房水管渗水',
    description: '水槽下方水管持续渗水，地面已经积水，希望尽快安排师傅上门处理',
    phone: '13912340001',
    date: tomorrow(),
    time: '10:00',
    categoryIndex: 0,
    urgency: 'URGENT',
  })
  createPage.submit()
  await sleep(2600)
  assert(calls.uploads === 2, 'two images should be uploaded, actual=' + calls.uploads)
  assert(
    calls.navigate.some((u) => u.includes('/pages/repair/detail/index?id=')),
    'repair submit should redirect to detail',
  )
  const detailUrl = calls.navigate.filter((u) => u.includes('/pages/repair/detail/index?id=')).pop()
  const ticketId = Number(detailUrl.split('id=')[1])
  console.log(`报修提交 OK：ticketId=${ticketId}，上传图片 ${calls.uploads} 张`)

  const detailPage = loadPage('./pages/repair/detail/index.js')
  detailPage.onLoad({ id: String(ticketId) })
  await sleep(1200)
  assert(!detailPage.data.error, 'repair detail error: ' + detailPage.data.error)
  assert(detailPage.data.detail.status === 'SUBMITTED', 'ticket should be SUBMITTED')
  assert(detailPage.data.detail.media.length === 2, 'ticket should have 2 media')
  assert(Object.keys(detailPage.data.mediaPaths).length === 2, 'private images should be downloaded')
  assert(detailPage.data.imageUrls.length === 2, 'preview urls ready')
  assert(detailPage.data.detail.contactPhone.includes('****'), 'phone should be masked')
  console.log(`报修详情 OK：状态=${detailPage.data.detail.statusLabel}，图片=${detailPage.data.imageUrls.length} 张（私有下载）`)

  // ============ 报修流转：受理→处理→解决→客户关闭→重开 ============
  assert((await adminCall(`/repairs/${ticketId}/accept`, 'POST', {})).body.code === '0', 'accept')
  assert((await adminCall(`/repairs/${ticketId}/start`, 'POST', {})).body.code === '0', 'start')
  assert((await adminCall(`/repairs/${ticketId}/resolve`, 'POST', {})).body.code === '0', 'resolve')
  await detailPage.load()
  assert(detailPage.data.detail.status === 'RESOLVED', 'ticket should be RESOLVED')
  detailPage.act({ currentTarget: { dataset: { action: 'close' } } })
  await sleep(1000)
  assert(detailPage.data.detail.status === 'CLOSED', 'ticket should be CLOSED after customer confirm')
  detailPage.act({ currentTarget: { dataset: { action: 'reopen' } } })
  await sleep(1000)
  assert(detailPage.data.detail.status === 'REOPENED', 'ticket should be REOPENED')
  console.log('报修流转 OK：SUBMITTED→ACCEPTED→PROCESSING→RESOLVED→CLOSED→REOPENED')

  // 再次处理并关闭，清理为 CLOSED
  await adminCall(`/repairs/${ticketId}/start`, 'POST', {})
  await adminCall(`/repairs/${ticketId}/resolve`, 'POST', {})
  await customerCall(`/repairs/${ticketId}/close`, 'POST', {})

  // ============ 我的报修列表 + 撤销 ============
  const second = await customerCall('/repairs', 'POST', {
    houseId,
    category: 'OTHER',
    urgency: 'NORMAL',
    title: '门锁不太好用',
    description: '入户门锁转动费力，需要上门检查或更换锁芯',
    contactPhone: '13912340001',
  })
  assert(second.body.code === '0', 'create second repair: ' + second.body.message)

  const listPage = loadPage('./pages/repair/list/index.js')
  listPage.onLoad()
  await sleep(900)
  assert(listPage.data.items.length >= 2, 'repair list should contain 2 tickets')
  const secondRow = listPage.data.items.find((item) => item.id === second.body.data.id)
  assert(secondRow && secondRow.canCancel, 'second ticket should be cancellable')
  listPage.act({ currentTarget: { dataset: { id: secondRow.id, action: 'cancel' } } })
  await sleep(900)
  await listPage.load(true)
  assert(
    listPage.data.items.find((item) => item.id === second.body.data.id).status === 'CANCELLED',
    'second ticket should be CANCELLED',
  )
  console.log(`我的报修 OK：${listPage.data.items.length} 条，撤销成功`)

  // ============ 内容 / 政策 ============
  const guideList = loadPage('./pages/content/list/index.js')
  guideList.onLoad({ type: 'guide' })
  await sleep(900)
  assert(guideList.data.items.length > 0, 'guide list should not be empty')
  const guide = guideList.data.items[0]
  const guideDetail = loadPage('./pages/content/detail/index.js')
  guideDetail.onLoad({ type: 'guide', id: String(guide.id) })
  await sleep(800)
  assert(!guideDetail.data.error, 'guide detail error: ' + guideDetail.data.error)
  assert(guideDetail.data.detail.content.length > 0, 'guide content should be plain text')
  console.log(`教程列表/详情 OK：${guide.title}（${guide.categoryText}）`)

  const policyList = loadPage('./pages/content/list/index.js')
  policyList.onLoad({ type: 'policy' })
  await sleep(900)
  assert(policyList.data.items.length > 0, 'policy list should not be empty')
  const policyDetail = loadPage('./pages/content/detail/index.js')
  policyDetail.onLoad({ type: 'policy', id: String(policyList.data.items[0].id) })
  await sleep(800)
  assert(!policyDetail.data.error, 'policy detail error: ' + policyDetail.data.error)
  assert(policyDetail.data.detail.source.includes('示例'), 'policy source should be marked as example')
  console.log(`政策列表/详情 OK：${policyList.data.items[0].title}`)

  // ============ 测算 ============
  const calc = loadPage('./pages/calculator/index.js')
  calc.onLoad()
  await sleep(900)
  assert(calc.data.cities.length > 0, 'calculator cities should load')
  calc.setData({ totalPrice: 1000000, downPayment: 300000, loanTypeIndex: 0, methodIndex: 0, yearsIndex: 5 })
  calc.submitMortgage()
  await sleep(900)
  assert(calc.data.mortgage, 'mortgage result should exist')
  assert(calc.data.mortgage.firstMonthText.startsWith('¥'), 'mortgage first month formatted')
  assert(calc.data.mortgage.yearly.length === 30, 'mortgage yearly summary should be 30 rows')
  console.log(`房贷测算 OK：首月 ${calc.data.mortgage.firstMonthText}，总利息 ${calc.data.mortgage.totalInterestText}`)

  calc.switchTab({ currentTarget: { dataset: { tab: 'tax' } } })
  calc.setData({ cityIndex: 0, taxPrice: 1000000 })
  calc.submitTax()
  await sleep(900)
  assert(calc.data.tax, 'tax result should exist')
  assert(calc.data.tax.items.length === 3, 'tax should contain 3 items')
  console.log(`税费测算 OK：合计 ${calc.data.tax.totalTaxText}`)

  // ============ 页面入口 ============
  const index = loadPage('./pages/index/index.js')
  index.goGuides({})
  index.goRepairs()
  index.goCalculator()
  assert(calls.navigate.some((u) => u.includes('/pages/content/list/index')), 'index guide entry')
  assert(calls.navigate.some((u) => u.includes('/pages/repair/list/index')), 'index repair entry')
  assert(calls.navigate.some((u) => u.includes('/pages/calculator/index')), 'index calculator entry')
  console.log('首页教程/报修/测算入口 OK')

  const houseDetail = loadPage('./pages/house/detail/index.js')
  houseDetail.setData({ id: houseId })
  houseDetail.goRepair()
  assert(calls.navigate.some((u) => u.includes('/pages/repair/create/index?houseId=' + houseId)), 'house detail repair entry')

  const contractDetail = loadPage('./pages/contract/detail/index.js')
  contractDetail.setData({ contract: { id: 12345 } })
  contractDetail.goRepair()
  assert(calls.navigate.some((u) => u.includes('/pages/repair/create/index?contractId=12345')), 'contract detail repair entry')
  console.log('房源/合同详情报修入口 OK')

  // ============ 游客访问拦截 ============
  auth.clearAuth()
  const guestList = loadPage('./pages/repair/list/index.js')
  guestList.onLoad()
  assert(guestList.data.loggedIn === false, 'guest should not be logged in')
  console.log('游客报修列表拦截 OK')

  console.log('== W4 小程序逻辑冒烟全部通过 ==')
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('MINI W4 SMOKE FAILED:', e.message)
    process.exit(1)
  })
