/**
 * W4 Web 冒烟：Chrome CDP 驱动，验证报修管理、内容管理、客源跟进与测算工具。
 * 前置：后端 8080、Web 5174 已启动；Chrome 以 --headless=new --remote-debugging-port=9222 运行。
 * 脚本会通过 API 自建房源/报修/跟进数据，可重复执行。
 * 用法：node docs/web-w4-flow.mjs <screenshotDir>
 */
import fs from 'node:fs'

const BASE = 'http://localhost:5174'
const API = 'http://localhost:8080/api/v1'
const CDP = 'http://127.0.0.1:9222'
const outDir = process.argv[2] || '.'

function readEnv(key) {
  const text = fs.readFileSync('D:/graduate/home/realestate-server/.env', 'utf8')
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith(key + '=')) return line.slice(key.length + 1).trim()
  }
  throw new Error('missing env ' + key)
}

const ADMIN_PASSWORD = readEnv('BOOTSTRAP_ADMIN_PASSWORD')
const CUSTOMER_PASSWORD = readEnv('BOOTSTRAP_CUSTOMER_PASSWORD')

let nextId = 0
const pending = new Map()

function connect(wsUrl) {
  const ws = new WebSocket(wsUrl)
  const ready = new Promise((resolve, reject) => {
    ws.onopen = () => resolve()
    ws.onerror = (e) => reject(new Error('ws error ' + e.message))
  })
  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data)
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id)
      pending.delete(msg.id)
      if (msg.error) reject(new Error(JSON.stringify(msg.error)))
      else resolve(msg.result)
    }
  }
  const send = async (method, params = {}) => {
    await ready
    const id = ++nextId
    return new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject })
      ws.send(JSON.stringify({ id, method, params }))
    })
  }
  return { send, ready, close: () => ws.close() }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function evalJs(client, expression) {
  const res = await client.send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  })
  if (res.exceptionDetails) {
    throw new Error('JS error: ' + JSON.stringify(res.exceptionDetails))
  }
  return res.result.value
}

function assert(condition, message) {
  if (!condition) throw new Error('ASSERT FAILED: ' + message)
}

async function shot(client, name) {
  const res = await client.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync(`${outDir}/${name}`, Buffer.from(res.data, 'base64'))
}

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

async function adminFetch(path, method = 'GET', body) {
  const login = await apiLogin('admin', ADMIN_PASSWORD, 'WEB')
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + login.accessToken,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, body: await res.json() }
}

async function customerFetch(token, path, method = 'GET', body) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + token,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, body: await res.json() }
}

const JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==',
  'base64',
)

async function prepareData(stamp) {
  const owner = await adminFetch('/owners', 'POST', {
    name: 'W4 Web 客源' + stamp,
    phone: '13800138006',
    type: 'CUSTOMER_LEAD',
  })
  assert(owner.body.code === '0', 'create owner: ' + owner.body.message)
  const ownerId = owner.body.data.id

  const follow = await adminFetch(`/owners/${ownerId}/follows`, 'POST', {
    content: 'W4 Web 冒烟跟进：客户希望本周末看两套房',
    followType: 'PHONE',
    nextFollowAt: new Date(Date.now() + 86400000).toISOString(),
  })
  assert(follow.body.code === '0', 'create follow: ' + follow.body.message)

  const house = await adminFetch('/houses', 'POST', {
    title: 'W4 Web 报修房源' + stamp,
    houseType: 'RENT',
    community: '报修苑',
    region: '浦东新区',
    address: '上海市浦东新区报修路 20 号 801 室',
    price: 5600,
    area: 92,
    rooms: 2,
    bathrooms: 1,
    ownerId,
  })
  assert(house.body.code === '0', 'create house: ' + house.body.message)
  const houseId = house.body.data.id
  assert((await adminFetch(`/houses/${houseId}/publish`, 'POST')).body.code === '0', 'publish house')

  const customer = await apiLogin('customer', CUSTOMER_PASSWORD, 'MINI')
  const repair = await customerFetch(customer.accessToken, '/repairs', 'POST', {
    houseId,
    category: 'PLUMBING',
    urgency: 'URGENT',
    title: 'W4 Web 冒烟报修：卫生间渗水',
    description: '卫生间地漏附近持续渗水，墙面已经返潮，希望尽快安排师傅上门处理',
    contactPhone: '13912340002',
  })
  assert(repair.body.code === '0', 'create repair: ' + repair.body.message)
  const ticketId = repair.body.data.id

  const ticketNo = repair.body.data.ticketNo
  assert(ticketNo, 'ticketNo missing')
  const form = new FormData()
  form.append('file', new Blob([JPEG], { type: 'image/jpeg' }), 'repair.jpg')
  const upload = await fetch(`${API}/repairs/${ticketId}/media`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + customer.accessToken },
    body: form,
  })
  const uploadBody = await upload.json()
  assert(uploadBody.code === '0', 'upload repair image: ' + uploadBody.message)

  assert(
    (await adminFetch(`/repairs/${ticketId}/accept`, 'POST', {})).body.code === '0',
    'accept repair',
  )
  return { ownerId, ticketId, ticketNo }
}

async function main() {
  const stamp = Date.now().toString().slice(-6)
  const { ownerId, ticketId, ticketNo } = await prepareData(stamp)
  console.log(`准备数据 OK：ownerId=${ownerId} ticketId=${ticketId} ticketNo=${ticketNo}`)

  const target = await fetch(`${CDP}/json/new?${encodeURIComponent(BASE + '/login')}`, { method: 'PUT' })
  const page = await target.json()
  const client = connect(page.webSocketDebuggerUrl)
  await client.ready
  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: 1500,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await sleep(2500)

  await evalJs(client, `(() => {
    const setValue = (el, value) => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
      setter.call(el, value)
      el.dispatchEvent(new Event('input', { bubbles: true }))
    }
    const inputs = document.querySelectorAll('input')
    setValue(inputs[0], 'admin')
    setValue(inputs[1], '${ADMIN_PASSWORD}')
    const btn = [...document.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g, '').includes('登录'))
    if (!btn) throw new Error('login button missing')
    btn.click()
    return true
  })()`)
  await sleep(3000)
  assert((await evalJs(client, 'location.pathname')) === '/dashboard', 'login should land dashboard')
  const menu = await evalJs(client, "document.querySelector('.el-menu').innerText.replace(/\\n+/g,' | ')")
  for (const entry of ['报修管理', '内容管理', '测算工具']) {
    assert(menu.includes(entry), 'menu should contain ' + entry + ': ' + menu)
  }
  console.log('登录与 W4 菜单 OK：', menu)

  // 报修管理
  await client.send('Page.navigate', { url: BASE + '/repairs' })
  await sleep(3000)
  assert((await evalJs(client, 'location.pathname')) === '/repairs', 'repairs route')
  const repairHeader = await evalJs(client, "document.querySelector('.card-header').innerText.replace(/\\n+/g,' | ')")
  assert(repairHeader.includes('报修管理'), 'repair header: ' + repairHeader)
  const repairRows = await evalJs(client, "document.querySelectorAll('.el-table__body tbody tr').length")
  assert(repairRows > 0, 'repair rows should be > 0')
  const repairBody = await evalJs(client, 'document.body.innerText')
  assert(/待受理|已受理|处理中|已解决|已关闭/.test(repairBody), 'repair status rendered')
  await shot(client, 'web-repairs.png')

  await evalJs(client, `(() => {
    const row = [...document.querySelectorAll('.el-table__body tbody tr')].find(r => r.innerText.includes('${ticketNo}'))
    if (!row) throw new Error('repair row missing')
    const btn = [...row.querySelectorAll('button')].find(b => b.innerText.includes('详情'))
    if (!btn) throw new Error('repair detail button missing')
    btn.click()
    return true
  })()`)
  await sleep(2000)
  const drawerText = await evalJs(client, "document.querySelector('.el-drawer')?.innerText || ''")
  for (const section of ['问题描述', '现场图片', '状态时间线']) {
    assert(drawerText.includes(section), 'drawer should contain ' + section)
  }
  const drawerImages = await evalJs(client, "document.querySelectorAll('.el-drawer .el-image').length")
  assert(drawerImages >= 1, 'repair image should render in drawer')
  console.log(`报修管理 OK：${repairHeader.replace(/\|/g, ' ')}，行数=${repairRows}，详情含图片 ${drawerImages} 张`)
  await shot(client, 'web-repair-detail.png')

  // 内容管理：教程
  await client.send('Page.navigate', { url: BASE + '/contents/guides' })
  await sleep(3000)
  const guideHeader = await evalJs(client, "document.querySelector('.card-header').innerText.replace(/\\n+/g,' | ')")
  assert(guideHeader.includes('教程'), 'guide header: ' + guideHeader)
  const guideRows = await evalJs(client, "document.querySelectorAll('.el-table__body tbody tr').length")
  assert(guideRows > 0, 'guide rows should be > 0')
  await evalJs(client, `(() => {
    const btn = [...document.querySelectorAll('button')].find(b => b.innerText.includes('新建内容'))
    if (!btn) throw new Error('create guide button missing')
    btn.click()
    return true
  })()`)
  await sleep(1200)
  const dialogText = await evalJs(client, "document.querySelector('.el-dialog')?.innerText || ''")
  assert(dialogText.includes('正文') && dialogText.includes('预览'), 'guide dialog should have editor and preview')
  await shot(client, 'web-guides.png')
  await evalJs(client, `(() => {
    const dialog = document.querySelector('.el-dialog')
    const btn = [...dialog.querySelectorAll('button')].find(b => b.innerText.includes('取消'))
    if (btn) btn.click()
    return true
  })()`)
  console.log(`内容管理（教程）OK：${guideHeader.replace(/\|/g, ' ')}，行数=${guideRows}，编辑器+预览正常`)

  // 内容管理：政策
  await client.send('Page.navigate', { url: BASE + '/contents/policies' })
  await sleep(3000)
  const policyHeader = await evalJs(client, "document.querySelector('.card-header').innerText.replace(/\\n+/g,' | ')")
  assert(policyHeader.includes('政策文件'), 'policy header: ' + policyHeader)
  const policyRows = await evalJs(client, "document.querySelectorAll('.el-table__body tbody tr').length")
  assert(policyRows > 0, 'policy rows should be > 0')
  console.log(`内容管理（政策）OK：${policyHeader.replace(/\|/g, ' ')}，行数=${policyRows}`)
  await shot(client, 'web-policies.png')

  // 测算工具
  await client.send('Page.navigate', { url: BASE + '/calculator' })
  await sleep(3000)
  await evalJs(client, `(() => {
    const btn = [...document.querySelectorAll('button')].find(b => b.innerText.includes('开始测算'))
    if (!btn) throw new Error('mortgage button missing')
    btn.click()
    return true
  })()`)
  await sleep(1800)
  const mortgageText = await evalJs(client, 'document.body.innerText')
  for (const section of ['首月还款', '总利息', '总还款', '按年汇总', '假设与说明']) {
    assert(mortgageText.includes(section), 'mortgage result should contain ' + section)
  }
  console.log('房贷测算 OK：首月还款/总利息/总还款/按年汇总/假设说明均渲染')
  await shot(client, 'web-calculator.png')

  await evalJs(client, `(() => {
    const tab = [...document.querySelectorAll('.el-tabs__item')].find(t => t.innerText.includes('税费测算'))
    if (!tab) throw new Error('tax tab missing')
    tab.click()
    return true
  })()`)
  await sleep(1200)
  await evalJs(client, `(() => {
    const btns = [...document.querySelectorAll('button')].filter(b => b.innerText.includes('开始测算'))
    const btn = btns[btns.length - 1]
    if (!btn) throw new Error('tax button missing')
    btn.click()
    return true
  })()`)
  await sleep(1800)
  const taxText = await evalJs(client, 'document.body.innerText')
  for (const section of ['税费明细', '契税', '税费合计']) {
    assert(taxText.includes(section), 'tax result should contain ' + section)
  }
  console.log('税费测算 OK：契税/增值税/个税明细与合计渲染')

  // 客源跟进
  await client.send('Page.navigate', { url: BASE + `/owners/${ownerId}` })
  await sleep(3000)
  const followText = await evalJs(client, 'document.body.innerText')
  assert(followText.includes('跟进记录'), 'owner detail should contain follow section')
  assert(followText.includes('W4 Web 冒烟跟进'), 'owner detail should render follow content')
  assert(followText.includes('下次跟进'), 'next follow time should render')
  console.log('客源跟进 OK：跟进记录、下次跟进时间正常')
  await shot(client, 'web-owner-follow.png')

  const bad = await evalJs(client, "document.body.innerText.includes('Failed to resolve component')")
  assert(bad === false, 'page should not contain unresolved components')

  client.close()
  await fetch(`${CDP}/json/close/${page.id}`)
  console.log('== W4 Web 冒烟全部通过 ==')
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('WEB W4 SMOKE FAILED:', e.message)
    process.exit(1)
  })
