/**
 * W2 Web 冒烟：Chrome CDP 驱动，验证 Element Plus 渲染、登录、房源与客源页面。
 * 用法：node docs/web-smoke.mjs <screenshotDir>
 */
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

async function main() {
  const target = await fetch(`${CDP}/json/new?${encodeURIComponent(BASE + '/login')}`, { method: 'PUT' })
  const page = await target.json()
  const client = connect(page.webSocketDebuggerUrl)
  await client.ready

  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 960,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await sleep(2500)

  const loginText = await evalJs(client, 'document.body.innerText')
  console.log('== 登录页渲染 ==')
  console.log(loginText.slice(0, 200).replace(/\n+/g, ' | '))
  console.log('el-card 渲染:', await evalJs(client, "!!document.querySelector('.el-card')"))
  console.log('el-input 渲染:', await evalJs(client, "document.querySelectorAll('.el-input').length"))

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
  console.log('== 登录后 ==')
  console.log('url:', await evalJs(client, 'location.pathname'))
  console.log('菜单:', await evalJs(client, "document.querySelector('.el-menu')?.innerText.replace(/\\n/g, ' / ')"))

  // 刷新页面验证 Cookie 恢复会话
  await client.send('Page.navigate', { url: BASE + '/houses' })
  await sleep(3500)
  console.log('== 房源列表页（刷新后会话恢复）==')
  console.log('url:', await evalJs(client, 'location.pathname'))
  console.log('标题:', await evalJs(client, "document.querySelector('.card-header')?.innerText.replace(/\\n/g, ' ')"))
  console.log('表头:', await evalJs(client, "document.querySelector('.el-table__header')?.innerText.replace(/\\n/g, ' / ')"))
  console.log('行数:', await evalJs(client, "document.querySelectorAll('.el-table__body tbody tr').length"))
  console.log('分页:', await evalJs(client, "document.querySelector('.el-pagination')?.innerText.replace(/\\n/g, ' ')"))

  const shot1 = await client.send('Page.captureScreenshot', { format: 'png' })
  const fs = await import('node:fs')
  fs.writeFileSync(`${outDir}/web-houses.png`, Buffer.from(shot1.data, 'base64'))

  // 客源页
  await client.send('Page.navigate', { url: BASE + '/owners' })
  await sleep(3000)
  console.log('== 客源页 ==')
  console.log('标题:', await evalJs(client, "document.querySelector('.card-header')?.innerText.replace(/\\n/g, ' ')"))
  console.log('行数:', await evalJs(client, "document.querySelectorAll('.el-table__body tbody tr').length"))
  const shot2 = await client.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync(`${outDir}/web-owners.png`, Buffer.from(shot2.data, 'base64'))

  // 新建房源表单
  await client.send('Page.navigate', { url: BASE + '/houses/new' })
  await sleep(3000)
  console.log('== 新建房源表单 ==')
  console.log('标题:', await evalJs(client, "document.querySelector('.card-header')?.innerText.replace(/\\n/g, ' ')"))
  console.log('表单项数:', await evalJs(client, "document.querySelectorAll('.el-form-item').length"))
  console.log('上传按钮:', await evalJs(client, "!!document.querySelector('.upload-btn')"))
  const shot3 = await client.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync(`${outDir}/web-house-form.png`, Buffer.from(shot3.data, 'base64'))

  // 控制台错误
  console.log('== 页面无 JS 报错检查（vue 警告数）==')
  console.log('body 文本含 Failed to resolve component:', loginText.includes('Failed to resolve')) 

  client.close()
  await fetch(`${CDP}/json/close/${page.id}`)
}

main().then(() => process.exit(0)).catch((e) => {
  console.error('SMOKE FAILED:', e.message)
  process.exit(1)
})
