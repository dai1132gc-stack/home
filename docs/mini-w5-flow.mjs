/**
 * W5 小程序联调逻辑冒烟：Node 模拟 wx，覆盖微信登录（含未配置降级与模拟成功）、
 * 账号密码降级、401 单飞刷新、弱网重试策略、上传/下载鉴权、全流程页面与个人中心。
 *
 * 说明：
 * - 真实 jscode2session 需要正式 AppID/AppSecret，本机未配置，脚本对“微信登录成功”
 *   使用后端账号密码登录的真实令牌模拟后端响应，仅验证小程序页面逻辑；
 *   真机微信授权与 code2session 仍需用户扫码验证（见 docs/w5-device-checklist.md）。
 * - 不含微信开发者工具渲染（Skyline 需 GUI 验证）。
 * 前置：后端 8080、MySQL、Redis 已启动。
 * 用法：node docs/mini-w5-flow.mjs
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
fs.writeFileSync('/tmp/w5-mini-1.jpg', JPEG)
fs.writeFileSync('/tmp/w5-mini-2.jpg', JPEG)

const storage = new Map()
const pageStack = ['pages/index/index']
const calls = {
  navigate: [],
  toast: [],
  refresh: 0,
  wechatLogin: 0,
  uploads: 0,
  downloads: 0,
  attempts: {},
  documents: [],
  previews: [],
}
let wechatMock = null
let loginShouldFail = false
const failOnce = new Set()

function pathOf(url) {
  return url.replace(API, '').split('?')[0]
}

globalThis.wx = {
  getStorageSync: (k) => (storage.has(k) ? storage.get(k) : ''),
  setStorageSync: (k, v) => storage.set(k, v),
  removeStorageSync: (k) => storage.delete(k),
  showToast: (opt) => calls.toast.push(opt.title),
  showModal: (opt) => opt.success && opt.success({ confirm: true }),
  navigateTo: (opt) => {
    calls.navigate.push(opt.url)
    pageStack.push(opt.url.split('?')[0].replace(/^\//, ''))
  },
  redirectTo: (opt) => {
    calls.navigate.push(opt.url)
    pageStack[pageStack.length - 1] = opt.url.split('?')[0].replace(/^\//, '')
  },
  reLaunch: (opt) => {
    calls.navigate.push(opt.url)
    pageStack.length = 0
    pageStack.push(opt.url.split('?')[0].replace(/^\//, ''))
  },
  navigateBack: () => {
    calls.navigate.push('back')
    if (pageStack.length > 1) pageStack.pop()
  },
  previewImage: (opt) => calls.previews.push(opt.current),
  stopPullDownRefresh: () => {},
  login: (opt) => {
    setTimeout(() => {
      if (loginShouldFail) {
        opt.fail && opt.fail({ errMsg: 'login:fail mock' })
      } else {
        opt.success && opt.success({ code: 'w5-mock-code' })
      }
    }, 10)
  },
  chooseMedia: (opt) => {
    opt.success({ tempFiles: [{ tempFilePath: '/tmp/w5-mini-1.jpg' }] })
  },
  openDocument: (opt) => {
    try {
      calls.documents.push(fs.statSync(opt.filePath).size)
    } catch (e) {
      calls.documents.push(0)
    }
    opt.success && opt.success()
  },
  request: (opt) => {
    const path = pathOf(opt.url)
    calls.attempts[path] = (calls.attempts[path] || 0) + 1
    if (path === '/auth/refresh') calls.refresh += 1
    if (path === '/auth/wechat-login') {
      calls.wechatLogin += 1
      if (wechatMock) {
        setTimeout(() => {
          opt.success({ statusCode: 200, data: { code: '0', message: 'success', data: wechatMock } })
        }, 20)
        return
      }
    }
    if (failOnce.has(path)) {
      failOnce.delete(path)
      setTimeout(() => opt.fail && opt.fail({ errMsg: 'request:fail timeout' }), 20)
      return
    }
    const query = opt.method === 'GET' && opt.data ? '?' + new URLSearchParams(opt.data).toString() : ''
    fetch(opt.url.startsWith('http') ? opt.url + query : API + path + query, {
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
        if (res.status !== 200) {
          opt.success({ statusCode: res.status, tempFilePath: '' })
          return
        }
        const type = res.headers.get('content-type') || ''
        if (!type.startsWith('image/') && !type.startsWith('application/pdf')) {
          throw new Error('unexpected content-type: ' + type)
        }
        const buffer = Buffer.from(await res.arrayBuffer())
        calls.downloads += 1
        const ext = type.startsWith('application/pdf') ? '.pdf' : '.jpg'
        const path = '/tmp/w5-media-' + calls.downloads + ext
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
globalThis.getCurrentPages = () => pageStack.map((route) => ({ route }))

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

function resetStack(route = 'pages/index/index') {
  pageStack.length = 0
  pageStack.push(route)
}

const auth = require('./store/auth.js')
const requestApi = require('./utils/request.js')
const repairApi = require('./api/repair.js')

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
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + login.accessToken },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, body: await res.json() }
}

function applySession(session) {
  auth.setAccessToken(session.accessToken)
  auth.setRefreshToken(session.refreshToken)
  auth.setUser(session.user)
}

async function prepareHouse(stamp) {
  const owner = await adminCall('/owners', 'POST', {
    name: 'W5小程序房东' + stamp,
    phone: '13800138006',
    type: 'LANDLORD',
  })
  assert(owner.body.code === '0', 'create owner: ' + owner.body.message)
  const house = await adminCall('/houses', 'POST', {
    title: 'W5小程序房源' + stamp,
    houseType: 'RENT',
    community: '联调花园',
    region: '浦东新区',
    address: '上海市浦东新区联调路 20 号 601 室',
    price: 5600,
    area: 92,
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

/** 列表分页查找：先刷新第一页，未命中则继续翻页（避免历史数据把新记录挤到第二页） */
async function findAcrossPages(page, predicate, maxPages = 8) {
  await page.load(true)
  let found = page.data.items.find(predicate)
  let guard = 0
  while (!found && !page.data.finished && guard < maxPages) {
    await page.load(false)
    found = page.data.items.find(predicate)
    guard += 1
  }
  return found
}

async function main() {
  auth.clearAuth()
  resetStack('pages/index/index')
  const stamp = Date.now().toString().slice(-6)
  const houseId = await prepareHouse(stamp)
  console.log(`准备数据 OK：houseId=${houseId}`)

  // ============ 1. 登录页：微信未配置 → 明确降级提示，不静默失败 ============
  resetStack('pages/index/index')
  const loginPage = loadPage('./pages/login/login.js')
  loginPage.handleWechatLogin()
  await sleep(800)
  assert(calls.wechatLogin === 1, 'wechat-login should be requested once')
  assert(
    calls.toast.some((t) => t.includes('微信登录未配置，请使用账号密码')),
    'not-configured toast missing: ' + JSON.stringify(calls.toast),
  )
  assert(!auth.getAccessToken(), 'failed wechat login must not set access token')
  assert(loginPage.data.wechatLoading === false, 'wechat loading should reset')
  console.log('微信未配置降级 OK：提示使用账号密码，未写入登录态')

  // ============ 2. 登录页：微信登录成功（后端响应模拟，真实 code2session 未验证） ============
  const customerSession = await apiLogin('customer', CUSTOMER_PASSWORD, 'MINI')
  wechatMock = customerSession
  resetStack('pages/index/index')
  pageStack.push('pages/login/login')
  const loginPage2 = loadPage('./pages/login/login.js')
  loginPage2.handleWechatLogin()
  await sleep(1000)
  assert(calls.wechatLogin === 2, 'wechat-login should be requested twice')
  assert(auth.getAccessToken() === customerSession.accessToken, 'wechat login should store access token')
  assert(auth.getRefreshToken() === customerSession.refreshToken, 'wechat login should store refresh token')
  assert(calls.navigate.includes('back'), 'login should navigate back to previous page')
  console.log('微信登录成功链路 OK（模拟后端响应，真实微信授权待真机验证）')
  wechatMock = null

  // ============ 3. 账号密码登录（降级路径完全可用） ============
  auth.clearAuth()
  resetStack('pages/index/index')
  pageStack.push('pages/login/login')
  const loginPage3 = loadPage('./pages/login/login.js')
  loginPage3.setData({ username: 'customer', password: CUSTOMER_PASSWORD })
  await loginPage3.handleLogin()
  await sleep(800)
  assert(auth.getAccessToken(), 'account login should set access token')
  assert(auth.getRefreshToken(), 'account login should set refresh token')
  console.log('账号密码降级登录 OK')

  // ============ 4. 401 单飞刷新：并发请求只刷新一次，原请求各重试一次 ============
  const beforeRefresh = calls.refresh
  auth.setAccessToken('invalid-access-token')
  const [me, repairs] = await Promise.all([
    requestApi.get('/auth/me'),
    requestApi.get('/repairs', { page: 1, size: 1 }),
  ])
  assert(me && me.role === 'CUSTOMER', 'concurrent request /auth/me should succeed after refresh')
  assert(repairs && Array.isArray(repairs.items), 'concurrent request /repairs should succeed after refresh')
  assert(calls.refresh - beforeRefresh === 1, 'single-flight refresh expected 1, actual=' + (calls.refresh - beforeRefresh))
  assert(auth.getAccessToken() !== 'invalid-access-token', 'access token should be rotated')
  console.log('401 单飞刷新 OK：2 个并发请求仅 1 次刷新，均重试成功')

  // ============ 5. 弱网策略：GET 重试一次，POST 不盲目重试 ============
  failOnce.add('/guides')
  const guides = await requestApi.get('/guides', { page: 1, size: 1 })
  assert(Array.isArray(guides.items), 'GET should retry once after network failure')
  assert(calls.attempts['/guides'] >= 2, 'GET attempts should be >= 2')
  console.log(`弱网 GET 重试 OK：尝试 ${calls.attempts['/guides']} 次后成功`)

  failOnce.add('/calculators/mortgage')
  let postError = null
  try {
    await requestApi.post('/calculators/mortgage', {
      loanType: 'COMMERCIAL',
      repaymentMethod: 'EQUAL_INSTALLMENT',
      totalPrice: 1000000,
      downPayment: 300000,
      termYears: 5,
    })
  } catch (err) {
    postError = err
  }
  assert(postError, 'POST network failure should reject')
  assert(postError.message.includes('网络'), 'POST error should be readable: ' + postError.message)
  assert(calls.attempts['/calculators/mortgage'] === 1, 'POST must not auto retry, actual=' + calls.attempts['/calculators/mortgage'])
  console.log('弱网 POST 不重试 OK：仅 1 次尝试并给出网络提示')

  // ============ 6. 上传/下载鉴权 + 401 刷新重试 ============
  const createRepair = loadPage('./pages/repair/create/index.js')
  createRepair.onLoad({ houseId: String(houseId) })
  await sleep(800)
  assert(!createRepair.data.error, 'repair create load error: ' + createRepair.data.error)
  createRepair.chooseImage()
  createRepair.setData({
    title: '卫生间水龙头漏水',
    description: '水龙头关闭后仍持续滴水，地面有积水，希望尽快上门维修',
    phone: '13912340002',
    date: tomorrow(),
    time: '10:00',
    categoryIndex: 0,
    urgency: 'NORMAL',
  })
  createRepair.submit()
  await sleep(2600)
  assert(calls.uploads === 1, 'repair image should be uploaded once, actual=' + calls.uploads)
  const detailUrl = calls.navigate.filter((u) => u.includes('/pages/repair/detail/index?id=')).pop()
  assert(detailUrl, 'repair submit should redirect to detail')
  const ticketId = Number(detailUrl.split('id=')[1])
  console.log(`报修提交 + 图片上传 OK：ticketId=${ticketId}`)

  const repairDetail = loadPage('./pages/repair/detail/index.js')
  repairDetail.onLoad({ id: String(ticketId) })
  await sleep(1400)
  assert(!repairDetail.data.error, 'repair detail error: ' + repairDetail.data.error)
  assert(repairDetail.data.detail.media.length === 1, 'ticket should have 1 media')
  assert(repairDetail.data.imageUrls.length === 1, 'private image should be downloaded for preview')
  console.log('私有图片鉴权下载 OK：详情页预览 1 张')

  // 上传接口 401 刷新重试
  const beforeUploadRefresh = calls.refresh
  auth.setAccessToken('invalid-access-token')
  const media = await repairApi.uploadRepairImage(ticketId, '/tmp/w5-mini-2.jpg')
  assert(media && media.id, 'upload after 401 refresh should succeed')
  assert(calls.refresh > beforeUploadRefresh, 'upload 401 should trigger refresh')
  console.log('上传 401 刷新重试 OK')

  // 下载接口 401 刷新重试（直接调用 API，模拟令牌恰好过期）
  const beforeDownloadRefresh = calls.refresh
  auth.setAccessToken('invalid-access-token')
  const tempPath = await repairApi.downloadRepairImage(ticketId, media.id)
  assert(tempPath && fs.existsSync(tempPath), 'download after 401 refresh should produce temp file')
  assert(calls.refresh > beforeDownloadRefresh, 'download 401 should trigger refresh')
  console.log('下载 401 刷新重试 OK：私有图片落临时文件后预览')

  // ============ 7. 房源列表分页 / 详情 / 收藏 ============
  const listPage = loadPage('./pages/house/list/index.js')
  listPage.onLoad({ tab: 'all' })
  await sleep(900)
  assert(!listPage.data.error, 'house list error: ' + listPage.data.error)
  assert(listPage.data.items.length > 0, 'house list should not be empty')
  const firstPageIds = listPage.data.items.map((item) => item.id)
  listPage.onReachBottom()
  await sleep(900)
  const allIds = listPage.data.items.map((item) => item.id)
  assert(new Set(allIds).size === allIds.length, 'pagination must not duplicate items')
  console.log(`房源列表 OK：${allIds.length} 条（首页 ${firstPageIds.length}），无重复`)

  const houseDetail = loadPage('./pages/house/detail/index.js')
  houseDetail.onLoad({ id: String(houseId) })
  await sleep(900)
  assert(houseDetail.data.house && houseDetail.data.house.id === houseId, 'house detail loaded')
  houseDetail.toggleFavorite()
  await sleep(700)
  assert(houseDetail.data.favorited === true, 'favorite should be true')
  houseDetail.toggleFavorite()
  await sleep(700)
  assert(houseDetail.data.favorited === false, 'favorite should be false')
  console.log('房源详情 / 收藏切换 OK')

  // ============ 8. 预约提交 / 列表 / 取消 ============
  const apptCreate = loadPage('./pages/appointment/create/index.js')
  apptCreate.onLoad({ id: String(houseId) })
  await sleep(800)
  apptCreate.setData({ date: tomorrow(), time: '11:00', phone: '13912340002' })
  apptCreate.submit()
  await sleep(1200)
  const apptList = loadPage('./pages/appointment/list/index.js')
  apptList.onLoad()
  await sleep(900)
  const isPending = (item) => item.houseId === houseId && item.status === 'PENDING'
  const pending = await findAcrossPages(apptList, isPending)
  assert(pending, 'new appointment should be PENDING')
  assert(pending.contactPhone.includes('****'), 'customer phone should be masked')
  apptList.cancel({ currentTarget: { dataset: { id: pending.id } } })
  await sleep(1000)
  const cancelled = await findAcrossPages(apptList, (item) => item.id === pending.id)
  assert(cancelled && cancelled.status === 'CANCELLED', 'appointment should be CANCELLED')
  console.log('预约提交 / 列表 / 取消 OK')

  // 再建一条并确认，用于合同
  const apptCreate2 = loadPage('./pages/appointment/create/index.js')
  apptCreate2.onLoad({ id: String(houseId) })
  await sleep(700)
  apptCreate2.setData({ date: tomorrow(), time: '15:00', phone: '13912340002' })
  apptCreate2.submit()
  await sleep(1200)
  const toConfirm = await findAcrossPages(apptList, isPending)
  assert(toConfirm, 'second appointment should exist')
  const confirmRes = await adminCall(`/appointments/${toConfirm.id}/confirm`, 'POST')
  assert(confirmRes.body.code === '0', 'confirm appointment: ' + confirmRes.body.message)

  // ============ 9. 合同：列表 / 详情 / 确认 / PDF ============
  const user = auth.getUser()
  const contractCreate = await adminCall('/contracts', 'POST', {
    houseId,
    customerId: user.id,
    title: 'W5联调租赁合同' + stamp,
    amount: 5600,
    deposit: 11200,
    paymentCycle: 'MONTHLY',
    startDate: tomorrow(),
    endDate: new Date(new Date().setFullYear(new Date().getFullYear() + 1)).toISOString().slice(0, 10),
  })
  assert(contractCreate.body.code === '0', 'create contract: ' + contractCreate.body.message)
  const contractId = contractCreate.body.data.id
  const submitRes = await adminCall(`/contracts/${contractId}/submit`, 'POST')
  assert(submitRes.body.code === '0', 'submit contract: ' + submitRes.body.message)

  const contractList = loadPage('./pages/contract/list/index.js')
  contractList.onLoad()
  await sleep(900)
  const contractRow = await findAcrossPages(contractList, (row) => row.id === contractId)
  assert(contractRow && contractRow.needSign, 'contract should be PENDING_SIGN')
  console.log(`合同列表 OK：${contractRow.contractNo} ${contractRow.statusText}`)

  const contractDetail = loadPage('./pages/contract/detail/index.js')
  contractDetail.onLoad({ id: String(contractId) })
  await sleep(900)
  assert(!contractDetail.data.error, 'contract detail error: ' + contractDetail.data.error)
  contractDetail.confirm()
  await sleep(1400)
  assert(contractDetail.data.contract.status === 'ACTIVE', 'contract should be ACTIVE after confirm')
  contractDetail.download()
  await sleep(1600)
  assert(calls.documents.length === 1 && calls.documents[0] > 20000, 'contract PDF should be downloaded and opened')
  console.log(`合同确认 / PDF 下载 OK：${calls.documents[0]} bytes`)

  // ============ 10. 教程 / 公告 / 政策 ============
  const guideList = loadPage('./pages/content/list/index.js')
  guideList.onLoad({ type: 'guide' })
  await sleep(900)
  assert(guideList.data.items.length > 0, 'guide list should not be empty')
  const guideDetail = loadPage('./pages/content/detail/index.js')
  guideDetail.onLoad({ type: 'guide', id: String(guideList.data.items[0].id) })
  await sleep(800)
  assert(guideDetail.data.detail.content.length > 0, 'guide content should be plain text')

  const policyList = loadPage('./pages/content/list/index.js')
  policyList.onLoad({ type: 'policy' })
  await sleep(900)
  assert(policyList.data.items.length > 0, 'policy list should not be empty')
  const policyDetail = loadPage('./pages/content/detail/index.js')
  policyDetail.onLoad({ type: 'policy', id: String(policyList.data.items[0].id) })
  await sleep(800)
  assert(!policyDetail.data.error, 'policy detail error: ' + policyDetail.data.error)
  console.log('教程 / 公告 / 政策列表与纯文本详情 OK')

  // ============ 11. 测算 ============
  const calc = loadPage('./pages/calculator/index.js')
  calc.onLoad()
  await sleep(900)
  calc.setData({ totalPrice: 1000000, downPayment: 300000, loanTypeIndex: 0, methodIndex: 0, yearsIndex: 5 })
  calc.submitMortgage()
  await sleep(1000)
  assert(calc.data.mortgage && calc.data.mortgage.firstMonthText.startsWith('¥'), 'mortgage result missing')
  calc.switchTab({ currentTarget: { dataset: { tab: 'tax' } } })
  calc.setData({ cityIndex: 0, taxPrice: 1000000 })
  calc.submitTax()
  await sleep(1000)
  assert(calc.data.tax && calc.data.tax.items.length === 3, 'tax result missing')
  console.log(`测算 OK：首月 ${calc.data.mortgage.firstMonthText}，税费合计 ${calc.data.tax.totalTaxText}`)

  // ============ 12. 首页入口 / 个人中心 / 退出 ============
  const index = loadPage('./pages/index/index.js')
  index.goHouses({ currentTarget: { dataset: { tab: 'all' } } })
  index.goHouses({ currentTarget: { dataset: { tab: 'fav' } } })
  index.goAppointments()
  index.goContracts()
  index.goRepairs()
  index.goGuides({})
  index.goPolicies()
  index.goCalculator()
  index.goProfile()
  for (const key of ['/pages/house/list/index?tab=all', '/pages/house/list/index?tab=fav',
    '/pages/appointment/list/index', '/pages/contract/list/index', '/pages/repair/list/index',
    '/pages/content/list/index?type=guide', '/pages/content/list/index?type=policy',
    '/pages/calculator/index', '/pages/profile/profile']) {
    assert(calls.navigate.some((u) => u.includes(key)), 'index entry missing: ' + key)
  }
  console.log('首页全部入口 OK')

  const profile = loadPage('./pages/profile/profile.js')
  profile.onLoad()
  await sleep(900)
  assert(profile.data.user && profile.data.user.username === 'customer', 'profile should load current user')
  await profile.handleLogout()
  await sleep(900)
  assert(!auth.getAccessToken() && !auth.getRefreshToken(), 'logout should clear local tokens')
  console.log('个人中心 / 退出登录 OK：本地令牌已清空')

  console.log('== W5 小程序联调逻辑冒烟全部通过 ==')
  console.log('提示：微信授权与 jscode2session 真实链路需正式 AppID/AppSecret + 真机扫码，未在此脚本内验证。')
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('MINI W5 SMOKE FAILED:', e.message)
    process.exit(1)
  })
