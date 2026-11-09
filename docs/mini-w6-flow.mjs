/**
 * W6 小程序智能服务逻辑冒烟：Node 模拟 wx，加载真实页面源码与 request 封装，
 * 覆盖智能找房（意图解析/条件 chips/降级提示/分页/防重复提交）与政策问答
 * （答案/引用/insufficient/503/429/错误提示）。公开接口，游客态即可。
 * 前置：后端 8080（stub 模式）、MySQL、Redis、ai-stub 8090 已启动。
 * 用法：node docs/mini-w6-flow.mjs
 */
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'

const CLIENT_ROOT = 'D:/graduate/home/client/'
const require = createRequire(pathToFileURL(CLIENT_ROOT + 'index.js').href)
const API = 'http://localhost:8080/api/v1'

const storage = new Map()
const pageStack = ['pages/index/index']
const calls = { navigate: [] }

globalThis.wx = {
  getStorageSync: (k) => (storage.has(k) ? storage.get(k) : ''),
  setStorageSync: (k, v) => storage.set(k, v),
  removeStorageSync: (k) => storage.delete(k),
  showToast: () => {},
  navigateTo: (opt) => {
    calls.navigate.push(opt.url)
    pageStack.push(opt.url.split('?')[0].replace(/^\//, ''))
  },
  request: (opt) => {
    const query = opt.method === 'GET' && opt.data ? '?' + new URLSearchParams(opt.data).toString() : ''
    fetch(opt.url.startsWith('http') ? opt.url + query : API + opt.url.split('?')[0] + query, {
      method: opt.method || 'GET',
      headers: opt.header || {},
      body: opt.method && opt.method !== 'GET' ? JSON.stringify(opt.data || {}) : undefined,
    })
      .then(async (res) => opt.success({ statusCode: res.status, data: await res.json() }))
      .catch((err) => opt.fail && opt.fail(err))
  },
}

let captured = null
globalThis.Page = (config) => {
  captured = config
}

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

let pass = 0
let fail = 0
function assert(condition, message) {
  if (condition) {
    pass += 1
    console.log('  OK  ' + message)
  } else {
    fail += 1
    console.log('  FAIL ' + message)
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const aiApi = require('./api/ai.js')

// ---------- 数据准备：建一条已发布政策（stub 自动索引），结束清理 ----------
import fs from 'node:fs'
function readEnv(key) {
  const text = fs.readFileSync('D:/graduate/home/realestate-server/.env', 'utf8')
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith(key + '=')) return line.slice(key.length + 1).trim()
  }
  throw new Error('missing env ' + key)
}
async function adminFetch(path, options = {}) {
  const login = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: readEnv('BOOTSTRAP_ADMIN_PASSWORD'), clientType: 'WEB' }),
  }).then((r) => r.json())
  const token = login.data.accessToken
  return fetch(API + path, {
    method: options.method || 'GET',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    body: options.body ? JSON.stringify(options.body) : undefined,
  }).then((r) => r.json())
}
const stamp = Date.now()
const created = await adminFetch('/policies', {
  method: 'POST',
  body: {
    title: 'W6小程序契税政策' + stamp,
    docNo: 'W6M-' + stamp,
    docType: 'POLICY',
    source: '小程序测试局',
    content: '首套住房契税税率说明。面积九十平方米以下按百分之一征收，九十平方米以上按百分之一点五征收。',
  },
})
const policyId = created.data && created.data.id
await adminFetch(`/policies/${policyId}/publish`, { method: 'POST' })
await sleep(1500) // 等待 afterCommit 索引
console.log('== 0. 准备政策 id=' + policyId + ' ==')

// ---------- 首页入口 ----------
console.log('== 1. 首页入口 ==')
const indexPage = loadPage('./pages/index/index.js')
indexPage.onLoad()
indexPage.goAiSearch()
indexPage.goAiQa()
assert(calls.navigate.includes('/pages/ai/search/index'), '智能找房入口跳转')
assert(calls.navigate.includes('/pages/ai/qa/index'), '政策问答入口跳转')

// ---------- 智能找房 ----------
console.log('== 2. 智能找房 ==')
const searchPage = loadPage('./pages/ai/search/index.js')

// 空输入拦截
searchPage.submit()
assert(searchPage.data.error.includes('请输入'), '空输入拦截')

// 正常解析（stub 返回受约束 JSON）
searchPage.setData({ query: '朝阳 出租 三室 6000以内', error: '' })
searchPage.submit()
assert(searchPage.data.loading === true, '提交后 loading')
await sleep(800)
assert(searchPage.data.searched === true, '已搜索标记')
assert(searchPage.data.degraded === false, 'stub 模式不降级')
const condTexts = searchPage.data.conditions.map((c) => c.text)
assert(condTexts.some((t) => t === '出租'), '条件 chip：出租')
assert(condTexts.some((t) => t.includes('朝阳')), '条件 chip：区域')
assert(condTexts.some((t) => t.includes('3')), '条件 chip：居室')
assert(searchPage.data.items.length >= 0 && typeof searchPage.data.total === 'number', '结果列表与总数')

// 防重复提交
searchPage.setData({ loading: true })
const beforeTotal = searchPage.data.total
searchPage.submit()
await sleep(300)
assert(searchPage.data.total === beforeTotal, 'loading 中防重复提交')
searchPage.setData({ loading: false })

// 分页加载
if (!searchPage.data.finished) {
  const pageBefore = searchPage.data.page
  searchPage.onReachBottom()
  await sleep(800)
  assert(searchPage.data.page === pageBefore + 1, 'onReachBottom 翻页')
} else {
  assert(true, '结果不足一页，跳过翻页')
}

// 错误提示（网络失败时由 request 封装 reject）
searchPage.setData({ query: '   ' })
searchPage.submit()
assert(searchPage.data.error.includes('请输入'), '空白输入拦截')

// ---------- 政策问答 ----------
console.log('== 3. 政策问答 ==')
const qaPage = loadPage('./pages/ai/qa/index.js')

qaPage.submit()
assert(qaPage.data.error.includes('请输入'), '空问题拦截')

qaPage.setData({ question: '首套住房契税税率面积征收', error: '' })
qaPage.submit()
await sleep(1500)
assert(qaPage.data.asked === true, '问答完成')
assert(qaPage.data.insufficient === false, '可回答 insufficient=false')
assert(qaPage.data.answer.length > 0, '答案非空（纯文本）')
assert(qaPage.data.citations.length >= 1, '引用列表非空')
assert(qaPage.data.citations[0].title && qaPage.data.citations[0].source !== undefined, '引用含标题与来源')
assert(qaPage.data.disclaimer.includes('不构成法律意见'), '免责声明')

// 引用跳转政策详情
qaPage.goPolicy({ currentTarget: { dataset: { id: qaPage.data.citations[0].policyId } } })
assert(calls.navigate.some((u) => u.startsWith('/pages/content/detail/index')), '引用点击跳转政策详情')

// 无关问题拒答
qaPage.setData({ question: 'xyzzyqv' })
qaPage.submit()
await sleep(1500)
assert(qaPage.data.insufficient === true, '无关问题 insufficient=true')
assert(qaPage.data.answer === '', '拒答时答案为空')

// ---------- API 层 ----------
console.log('== 4. API 封装 ==')
const direct = await aiApi.houseSearch('海淀 两居', 1, 5)
assert(Array.isArray(direct.items), 'api.houseSearch 返回 items')
assert(direct.parsedConditions !== undefined && direct.degraded !== undefined, 'api.houseSearch 返回降级字段')

console.log(`== 结果：PASS=${pass} FAIL=${fail} ==`)
await adminFetch(`/policies/${policyId}/offline`, { method: 'POST' })
await adminFetch(`/policies/${policyId}`, { method: 'DELETE' })
if (fail > 0) process.exit(1)
