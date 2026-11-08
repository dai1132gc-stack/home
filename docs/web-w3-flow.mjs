/**
 * W3 Web 冒烟：Chrome CDP 驱动，验证预约管理、合同列表/详情/表单与房源状态扩展。
 * 前置：后端 8080、Web 5174 已启动；Chrome 以 --remote-debugging-port=9222 运行；
 *       数据库中已有 W3 冒烟产生的预约与合同（先执行 docs/smoke-w3.sh）。
 * 用法：node docs/web-w3-flow.mjs <screenshotDir>
 */
import fs from 'node:fs'

const BASE = 'http://localhost:5174'
const CDP = 'http://127.0.0.1:9222'
const outDir = process.argv[2] || '.'

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

const text = (sel) => `document.querySelector('${sel}')?.innerText.replace(/\\n+/g,' | ') || ''`

function assert(condition, message) {
  if (!condition) throw new Error('ASSERT FAILED: ' + message)
}

async function shot(client, name) {
  const res = await client.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync(`${outDir}/${name}`, Buffer.from(res.data, 'base64'))
}

async function main() {
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

  // 登录
  await evalJs(client, `(() => {
    const setValue = (el, value) => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
      setter.call(el, value)
      el.dispatchEvent(new Event('input', { bubbles: true }))
    }
    const inputs = document.querySelectorAll('input')
    setValue(inputs[0], 'admin')
    setValue(inputs[1], 'Admin@123456')
    const btn = [...document.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g, '').includes('登录'))
    if (!btn) throw new Error('login button missing')
    btn.click()
    return true
  })()`)
  await sleep(3000)
  assert((await evalJs(client, 'location.pathname')) === '/dashboard', 'login should land dashboard')
  const menu = await evalJs(client, text('.el-menu'))
  assert(menu.includes('预约管理') && menu.includes('合同管理'), 'menu should contain W3 entries: ' + menu)
  console.log('登录与菜单 OK：', menu)

  // 预约管理
  await client.send('Page.navigate', { url: BASE + '/appointments' })
  await sleep(3000)
  assert((await evalJs(client, 'location.pathname')) === '/appointments', 'appointments route')
  const apptHeader = await evalJs(client, text('.card-header'))
  assert(apptHeader.includes('预约带看'), 'appointment header: ' + apptHeader)
  const apptRows = await evalJs(client, "document.querySelectorAll('.el-table__body tbody tr').length")
  assert(apptRows > 0, 'appointment rows should be > 0')
  const apptBody = await evalJs(client, 'document.body.innerText')
  assert(/待确认|已确认|已完成|已取消|已爽约/.test(apptBody), 'appointment status label rendered')
  console.log(`预约页 OK：${apptHeader}，行数=${apptRows}`)
  await shot(client, 'web-appointments.png')

  // 合同列表
  await client.send('Page.navigate', { url: BASE + '/contracts' })
  await sleep(3000)
  const conHeader = await evalJs(client, text('.card-header'))
  assert(conHeader.includes('合同管理'), 'contract header: ' + conHeader)
  const conRows = await evalJs(client, "document.querySelectorAll('.el-table__body tbody tr').length")
  assert(conRows > 0, 'contract rows should be > 0')
  const statusTags = await evalJs(client, "[...document.querySelectorAll('.el-table__body .el-tag')].map(t=>t.innerText).join(',')")
  console.log(`合同列表 OK：${conHeader}，行数=${conRows}，状态=${statusTags}`)
  await shot(client, 'web-contracts.png')

  // 打开第一份合同详情
  await evalJs(client, `(() => {
    const btn = [...document.querySelectorAll('.el-table__body button')].find(b => b.innerText.includes('详情'))
    if (!btn) throw new Error('detail button missing')
    btn.click()
    return true
  })()`)
  await sleep(3000)
  const path = await evalJs(client, 'location.pathname')
  assert(/^\/contracts\/\d+$/.test(path), 'contract detail path: ' + path)
  const detailText = await evalJs(client, 'document.body.innerText')
  for (const section of ['合同信息', '签署方', '交易节点', '状态日志']) {
    assert(detailText.includes(section), 'detail should contain ' + section)
  }
  const partyRows = await evalJs(client, "document.querySelectorAll('.el-table__body tbody tr').length")
  assert(partyRows >= 3, 'party/node/log rows should render')
  console.log(`合同详情 OK：${path}，区块完整，表格行=${partyRows}`)
  await shot(client, 'web-contract-detail.png')

  // 起草合同表单
  await client.send('Page.navigate', { url: BASE + '/contracts/new' })
  await sleep(3000)
  const formHeader = await evalJs(client, text('.card-header'))
  assert(formHeader.includes('起草合同'), 'form header: ' + formHeader)
  const formItems = await evalJs(client, "document.querySelectorAll('.el-form-item').length")
  assert(formItems >= 12, 'form items should be >= 12, got ' + formItems)
  await evalJs(client, `(() => {
    const select = document.querySelector('.el-select')
    if (!select) throw new Error('select missing')
    select.click()
    return true
  })()`)
  await sleep(1200)
  const options = await evalJs(client, "document.querySelectorAll('.el-select-dropdown__item').length")
  assert(options > 0, 'house options should be loaded from API')
  console.log(`起草表单 OK：${formHeader}，表单项=${formItems}，房源可选项=${options}`)
  await shot(client, 'web-contract-form.png')

  // 房源状态筛选扩展
  await client.send('Page.navigate', { url: BASE + '/houses' })
  await sleep(3000)
  await evalJs(client, `(() => {
    const items = [...document.querySelectorAll('.el-form-item')]
    const statusItem = items.find(i => i.innerText.includes('状态'))
    const select = statusItem && statusItem.querySelector('.el-select')
    if (!select) throw new Error('status select missing')
    select.click()
    return true
  })()`)
  await sleep(900)
  const statusOptions = await evalJs(client, "[...document.querySelectorAll('.el-select-dropdown__item')].map(i=>i.innerText).join(',')")
  assert(/已预定/.test(statusOptions) && /已签约/.test(statusOptions), 'house status options: ' + statusOptions)
  console.log('房源状态筛选 OK：', statusOptions)
  await shot(client, 'web-house-status.png')

  const bad = await evalJs(client, "document.body.innerText.includes('Failed to resolve component')")
  assert(bad === false, 'page should not contain unresolved components')

  client.close()
  await fetch(`${CDP}/json/close/${page.id}`)
  console.log('== W3 Web 冒烟全部通过 ==')
}

main().then(() => process.exit(0)).catch((e) => {
  console.error('WEB W3 SMOKE FAILED:', e.message)
  process.exit(1)
})
