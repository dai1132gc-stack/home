/**
 * W6 Web 管理端智能服务流程验证：CDP 驱动 Chrome。
 * 覆盖：登录菜单含「智能服务」、/ai 页面状态卡片/统计表格/最近记录、
 *       政策管理「重建索引」按钮（loading + 成功提示）、截图存档。
 * 前置：后端 8080（stub 模式）、Web 5174、Chrome --remote-debugging-port=9222、ai-stub 8090。
 * 用法：node docs/web-w6-flow.mjs <screenshotDir>
 */
import fs from 'node:fs'

const CDP = 'http://127.0.0.1:9222'
const BASE = 'http://localhost:5174'
const API = 'http://localhost:8080/api/v1'
const outDir = process.argv[2] || 'docs/w6-shots'
fs.mkdirSync(outDir, { recursive: true })

function readEnv(key) {
  const text = fs.readFileSync('D:/graduate/home/realestate-server/.env', 'utf8')
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith(key + '=')) return line.slice(key.length + 1).trim()
  }
  throw new Error('missing env ' + key)
}
const ADMIN_PASSWORD = readEnv('BOOTSTRAP_ADMIN_PASSWORD')

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
  const res = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (res.exceptionDetails) throw new Error('JS error: ' + JSON.stringify(res.exceptionDetails))
  return res.result.value
}

function assert(condition, message) {
  if (!condition) throw new Error('ASSERT FAILED: ' + message)
  console.log('  OK  ' + message)
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
  assert(body.code === '0', `login ${username}`)
  return body.data
}

async function adminFetch(token, path, method = 'GET', body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, body: await res.json() }
}

async function main() {
  const stamp = Date.now()
  const token = (await apiLogin('admin', ADMIN_PASSWORD, 'WEB')).accessToken

  // 准备一条已发布政策（stub 自动索引）
  const created = await adminFetch(token, '/policies', 'POST', {
    title: 'W6Web冒烟政策' + stamp,
    docNo: 'W6W-' + stamp,
    docType: 'POLICY',
    source: 'Web测试局',
    content: '首套房贷款利率下限说明。各地可在全国统一基础上适度浮动，具体以当地公告为准。',
  })
  const policyId = created.body.data.id
  await adminFetch(token, `/policies/${policyId}/publish`, 'POST')
  await sleep(1500)
  console.log(`准备政策 OK：id=${policyId}`)

  const target = await fetch(`${CDP}/json/new?${encodeURIComponent(BASE + '/login')}`, { method: 'PUT' })
  const page = await target.json()
  const client = connect(page.webSocketDebuggerUrl)
  await client.ready
  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('Emulation.setDeviceMetricsOverride', { width: 1500, height: 1000, deviceScaleFactor: 1, mobile: false })
  await sleep(2500)

  // 登录
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
    btn.click()
    return true
  })()`)
  await sleep(3000)
  assert((await evalJs(client, 'location.pathname')) === '/dashboard', '登录跳转工作台')
  const menu = await evalJs(client, "document.querySelector('.el-menu').innerText")
  assert(menu.includes('智能服务'), '菜单含智能服务')

  // 智能服务页
  await client.send('Page.navigate', { url: BASE + '/ai' })
  await sleep(3000)
  assert((await evalJs(client, 'location.pathname')) === '/ai', '路由 /ai')
  const aiText = await evalJs(client, 'document.body.innerText')
  assert(aiText.includes('AI 服务状态'), '状态卡片')
  assert(aiText.includes('已启用') || aiText.includes('未启用'), '启用状态标签')
  assert(aiText.includes('已索引分块'), 'indexedChunks 展示')
  assert(aiText.includes('调用量') && aiText.includes('成功率') && aiText.includes('P95'), '指标卡片')
  assert(aiText.includes('按场景分布'), '场景分布表')
  assert(aiText.includes('最近 20 条记录'), '最近记录表')
  assert(aiText.includes('智能选房') || aiText.includes('政策问答') || aiText.includes('政策索引'), '场景中文标签')
  await shot(client, 'w6-ai-service.png')
  console.log('智能服务页 OK')

  // 政策管理页：重建索引按钮
  await client.send('Page.navigate', { url: BASE + '/contents/policies' })
  await sleep(3000)
  const policyText = await evalJs(client, 'document.body.innerText')
  assert(policyText.includes('重建索引'), '政策页含重建索引操作')
  assert(policyText.includes('W6Web冒烟政策' + stamp), '新政策出现在列表')
  const clicked = await evalJs(client, `(() => {
    const rows = [...document.querySelectorAll('.el-table__body tbody tr')]
    const row = rows.find(r => r.innerText.includes('W6Web冒烟政策${stamp}'))
    if (!row) throw new Error('row missing')
    const btn = [...row.querySelectorAll('button')].find(b => b.innerText.includes('重建索引'))
    if (!btn) throw new Error('reindex button missing')
    btn.click()
    return true
  })()`)
  assert(clicked, '点击重建索引')
  await sleep(2500)
  const afterClick = await evalJs(client, 'document.body.innerText')
  assert(afterClick.includes('索引已重建'), '重建成功提示')
  await shot(client, 'w6-policy-reindex.png')
  console.log('政策重建索引 OK')

  client.close()
  await adminFetch(token, `/policies/${policyId}/offline`, 'POST')
  await adminFetch(token, `/policies/${policyId}`, 'DELETE')
  console.log('W6 Web 流程全部通过')
}

main().catch((e) => {
  console.error(e.message || e)
  process.exit(1)
})
