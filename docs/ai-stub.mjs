/**
 * ai-stub.mjs — OpenAI 兼容 stub（仅 dev/test，端口 8090）。
 * 不得进入生产构建或默认启动；真实 Provider 未配置时用于降级/联调验证。
 *
 * 模型名控制行为：
 *   stub-chat / stub-embed      正常返回（可预测）
 *   *-invalid                   chat 返回非法 JSON（选房降级 AI_INVALID_JSON）
 *   *-429                       返回 HTTP 429
 *   *-500                       返回 HTTP 500
 *   *-timeout                   挂起 30s（触发后端读取超时）
 * embedding：固定 64 维，按字符袋哈希（共享字符越多相似度越高），保证政策问答可召回。
 */
import http from 'node:http'

const PORT = Number(process.env.STUB_PORT || 8090)
const DIM = 256

function hashEmbed(text) {
  // 256 维字符袋：拉丁与 CJK 字符几乎不碰撞，保证无关问题余弦相似度低于阈值
  const vec = new Array(DIM).fill(0)
  for (const ch of String(text)) {
    vec[ch.codePointAt(0) % DIM] += 1
  }
  const norm = Math.sqrt(vec.reduce((s, v) => s + v * v, 0)) || 1
  return vec.map((v) => Number((v / norm).toFixed(6)))
}

function chatReply(messages) {
  const user = (messages.find((m) => m.role === 'user') || {}).content || ''
  // 政策问答：提示词含「片段1」即按 QA 协议返回
  if (user.includes('片段1') || user.includes('问题：')) {
    return JSON.stringify({ answer: '根据已发布政策片段，以上内容适用（stub 可预测答案）。', citations: [1] })
  }
  // 选房意图解析：关键词 → 受约束 JSON
  const intent = {}
  if (/出租|租房|租/.test(user)) intent.houseType = 'RENT'
  if (/出售|买|购房/.test(user)) intent.houseType = 'SALE'
  const region = user.match(/(朝阳|海淀|东城|西城|丰台|通州|昌平|大兴)/)
  if (region) intent.region = region[1]
  const rooms = user.match(/([一二三四五1-5])[室居]/)
  if (rooms) intent.rooms = '一二三四五'.indexOf(rooms[1]) + 1 || Number(rooms[1])
  const price = user.match(/(\d{3,6})\s*(元|块)?\s*(以内|以下)/)
  if (price) intent.maxPrice = Number(price[1])
  if (/便宜|低价/.test(user)) intent.sort = 'price_asc'
  return JSON.stringify(intent)
}

const server = http.createServer((req, res) => {
  let body = ''
  req.on('data', (c) => (body += c))
  req.on('end', () => {
    let payload = {}
    try { payload = JSON.parse(body || '{}') } catch { /* ignore */ }
    const model = String(payload.model || '')

    const send = (status, obj) => {
      res.writeHead(status, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify(obj))
    }

    if (model.endsWith('-timeout')) {
      setTimeout(() => send(200, {}), 30_000)
      return
    }
    if (model.endsWith('-429')) return send(429, { error: { message: 'rate limited' } })
    if (model.endsWith('-500')) return send(500, { error: { message: 'server error' } })

    if (req.url.endsWith('/chat/completions')) {
      const invalid = model.endsWith('-invalid')
      const content = invalid ? '这不是JSON，无法解析' : chatReply(payload.messages || [])
      return send(200, {
        choices: [{ message: { role: 'assistant', content } }],
        usage: { prompt_tokens: 120, completion_tokens: 30 },
      })
    }
    if (req.url.endsWith('/embeddings')) {
      const inputs = Array.isArray(payload.input) ? payload.input : [String(payload.input || '')]
      return send(200, { data: inputs.map((t, i) => ({ index: i, embedding: hashEmbed(t) })) })
    }
    send(404, { error: { message: 'unknown path' } })
  })
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`ai-stub listening on http://127.0.0.1:${PORT}/v1 (dev/test only)`)
})
