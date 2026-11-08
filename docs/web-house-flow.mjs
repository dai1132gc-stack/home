/**
 * W2 Web 房源全流程冒烟：登录 → 新建房源（含图片上传）→ 保存 → 发布 → 列表校验。
 * 依赖：后端 8080、Web 5174、Chrome 9222、docs/w2-upload.png
 * 用法：node docs/web-house-flow.mjs <screenshotDir>
 */
const BASE = 'http://localhost:5174'
const CDP = 'http://127.0.0.1:9222'
const outDir = process.argv[2] || '.'
const uploadFile = 'D:/graduate/home/docs/w2-upload.png'

let nextId = 0
const pending = new Map()

function connect(wsUrl) {
  const ws = new WebSocket(wsUrl)
  const ready = new Promise((resolve, reject) => {
    ws.onopen = () => resolve()
    ws.onerror = () => reject(new Error('ws error'))
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

async function main() {
  const fs = await import('node:fs')
  const target = await fetch(`${CDP}/json/new?${encodeURIComponent(BASE + '/login')}`, { method: 'PUT' })
  const page = await target.json()
  const client = connect(page.webSocketDebuggerUrl)
  await client.ready
  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('DOM.enable')
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false,
  })

  const evalJs = async (expression) => {
    const res = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (res.exceptionDetails) throw new Error('JS: ' + (res.exceptionDetails.exception?.description || res.exceptionDetails.text))
    return res.result.value
  }

  await sleep(2500)
  await evalJs(`(() => {
    const setValue = (el, value) => {
      const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      el.dispatchEvent(new Event('blur', { bubbles: true }))
    }
    const inputs = document.querySelectorAll('input')
    setValue(inputs[0], 'admin')
    setValue(inputs[1], 'Admin@123456')
    ;[...document.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g, '').includes('登录')).click()
    return true
  })()`)
  await sleep(3000)
  console.log('登录后路径:', await evalJs('location.pathname'))

  await client.send('Page.navigate', { url: BASE + '/houses/new' })
  await sleep(3000)

  const stamp = Date.now()
  const title = `W2浏览器房源${stamp}`
  await evalJs(`(() => {
    const setValue = (el, value) => {
      const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      el.dispatchEvent(new Event('blur', { bubbles: true }))
    }
    const byLabel = (label) => [...document.querySelectorAll('.el-form-item')]
      .find(i => (i.querySelector('.el-form-item__label')?.innerText || '').trim().startsWith(label))
    const fill = (label, value) => {
      const item = byLabel(label)
      if (!item) throw new Error('missing field ' + label)
      const el = item.querySelector('textarea, input')
      setValue(el, value)
    }
    fill('标题', ${JSON.stringify(title)})
    fill('小区', '浏览器小区')
    fill('区域', '浦东新区')
    fill('楼层', '5/20')
    fill('地址', '浏览器测试路 88 号')
    fill('价格', '6800')
    fill('面积', '96.5')
    fill('室', '3')
    fill('卫', '2')
    fill('描述', '通过无头浏览器创建的房源，用于 UI 冒烟。')
    return true
  })()`)

  // 上传图片：定位 el-upload 的 file input，直接注入文件
  const doc = await client.send('DOM.getDocument', { depth: -1 })
  const node = await client.send('DOM.querySelector', { nodeId: doc.root.nodeId, selector: 'input[type=file]' })
  await client.send('DOM.setFileInputFiles', { nodeId: node.nodeId, files: [uploadFile] })
  await sleep(2500)
  console.log('已上传图片数:', await evalJs("document.querySelectorAll('.media-item').length"))

  await evalJs(`(() => {
    ;[...document.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g, '') === '保存').click()
    return true
  })()`)
  await sleep(3500)
  const detailPath = await evalJs('location.pathname')
  const detailText = await evalJs('document.body.innerText')
  console.log('保存后路径:', detailPath)
  console.log('状态含待发布:', detailText.includes('待发布'))
  console.log('详情含图片:', await evalJs("document.querySelectorAll('.media-img').length"))
  const shot1 = await client.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync(`${outDir}/web-house-detail.png`, Buffer.from(shot1.data, 'base64'))

  // 发布
  await evalJs(`(() => {
    const btn = [...document.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g, '') === '发布')
    if (!btn) throw new Error('publish button missing')
    btn.click()
    return true
  })()`)
  await sleep(800)
  await evalJs(`(() => {
    const box = document.querySelector('.el-message-box__btns')
    const ok = [...box.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g, '') === '确定')
    if (!ok) throw new Error('confirm missing')
    ok.click()
    return true
  })()`)
  await sleep(2500)
  const afterPublish = await evalJs('document.body.innerText')
  console.log('发布后状态含已发布:', afterPublish.includes('已发布'))
  const shot2 = await client.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync(`${outDir}/web-house-published.png`, Buffer.from(shot2.data, 'base64'))

  await client.send('Page.navigate', { url: BASE + '/houses' })
  await sleep(3000)
  console.log('列表行数:', await evalJs("document.querySelectorAll('.el-table__body tbody tr').length"))
  console.log('列表首行包含新标题:', await evalJs(`document.body.innerText.includes(${JSON.stringify(title)})`))
  const shot3 = await client.send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync(`${outDir}/web-house-list.png`, Buffer.from(shot3.data, 'base64'))

  client.close()
  await fetch(`${CDP}/json/close/${page.id}`)
}

main().then(() => process.exit(0)).catch((e) => {
  console.error('FLOW FAILED:', e.message)
  process.exit(1)
})
