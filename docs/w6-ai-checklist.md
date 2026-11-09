# W6 智能增强验收清单（V7.0）

## 1. 真实 Provider 配置（由用户提供 Key 后执行）

在 `realestate-server/.env` 配置（Key 只允许放 `.env`，禁止提交/打印/出现在前端）：

```bash
AI_ENABLED=true
AI_BASE_URL=https://<provider>/v1        # OpenAI 兼容，仅 http/https
AI_API_KEY=                              # 真实 Key，留空则启动 fail-fast
AI_CHAT_MODEL=                           # 如 qwen-plus / gpt-4o-mini 等
AI_EMBEDDING_MODEL=                      # 如 text-embedding-v3 等
AI_TIMEOUT_MS=12000                      # 10000~15000
AI_MAX_INPUT_CHARS=200
AI_MAX_OUTPUT_TOKENS=800
AI_DAILY_TOKEN_LIMIT=100000
AI_RATE_LIMIT_PER_MINUTE=20
AI_TOP_K=4
AI_SIMILARITY_THRESHOLD=0.30
AI_CONCURRENCY=4
```

真实联调步骤：
1. 启动 Redis（PING=PONG）→ 后端（health=UP）→ Web 5174。
2. `GET /api/v1/ai/status` 应返回 `enabled=true` 与实际模型名。
3. 管理端发布一条政策 → 确认 `policy_document_chunk` 生成向量分块（afterCommit 自动索引，失败仅 warn 不回滚发布）。
4. `POST /api/v1/ai/policy-answer` 提真实问题：检查答案质量、引用标题/文号/来源/定位、耗时；无关问题应 `insufficient=true`。
5. `POST /api/v1/ai/house-search` 输入自然语言：检查 `parsedConditions` 是否符合预期、结果是否只含 PUBLISHED。
6. 记录真实模型名、耗时、成功率，写入论文实验数据。
7. **embedding 模型变更后必须全量重建索引**（`POST /api/v1/admin/ai/reindex`），旧模型向量不参与召回。

**当前状态：真实 Provider 未提供 Key，真实模型未验证。所有自动化结果来自 stub，不代表真实模型联调通过。**

## 2. stub 使用（仅 dev/test）

```bash
node docs/ai-stub.mjs   # 127.0.0.1:8090，固定 256 维字符袋向量、可预测 JSON
# 后端以 stub 模式启动：
AI_ENABLED=true AI_BASE_URL=http://127.0.0.1:8090/v1 AI_API_KEY=stub-key \
AI_CHAT_MODEL=stub-chat AI_EMBEDDING_MODEL=stub-embed mvn spring-boot:run
```

- 模型名后缀控制故障：`*-invalid`（chat 返回非法 JSON）、`*-429`、`*-500`、`*-timeout`（挂起 30s）。
- stub 不进入生产构建、不作为默认启动项；冒烟结束应关闭 8090。

## 3. 限额与降级口径

| 项 | 口径 |
|---|---|
| 公开 AI 限流 | 每 IP 20 次/分钟，超限 429 `COMMON_42901` |
| 日 token 预算 | `AI_DAILY_TOKEN_LIMIT`，超限 429，不静默降级；key 带 UTC 日期 + TTL |
| 并发 | 信号量 4，获取超时 1s，失败 429，finally 释放 |
| Redis 故障 | AI 接口 fail-closed 503 `AI_50302`；登录/测算仍 fail-open |
| 选房降级 | AI_DISABLED/TIMEOUT/UPSTREAM/INVALID_JSON/INVALID_INTENT → 200 + degraded + 最新发布列表 |
| 问答未配置 | 503 `AI_50301`；上游异常 502 `AI_50201`；检索不足 200 + `insufficient=true`（不调用 chat） |

## 4. 隐私与安全

- 发送模型前清理手机号/身份证/邮箱；模型不得生成/执行 SQL；意图字段白名单 + 参数化下推。
- `ai_interaction_log` 只记指标（场景/状态/耗时/token 估算/降级原因/traceId），不记录原始问题/答案/prompt。
- 日志/响应/前端不出现 API Key、base-url 内部地址、完整 prompt/答案。
- 公开白名单精确到方法+路径：`POST /api/v1/ai/house-search`、`POST /api/v1/ai/policy-answer`、`GET /api/v1/ai/status`；`/api/v1/admin/**` 全部认证 + `@PreAuthorize`。

## 5. 已验证 / 未验证边界

已验证（2026-11-09）：
- `mvn clean package`：210 项单测全通过（181 基线 + 29 新增）。
- `docs/smoke-w6.sh`：未配置模式 18 项、stub 模式 29 项全通过。
- `node docs/mini-w6-flow.mjs`：25 项全通过；`node docs/web-w6-flow.mjs docs`：全通过。
- 回归：smoke-w2/w3/w4/auth-hardening、mini-w5-flow、web-w4-flow 全通过；Web lint/23 项单测/build、小程序 JS/JSON 校验通过。
- Flyway V6 迁移成功，新列与索引生效，V1～V5 未修改。

未验证（不得声称通过）：
- **真实 Provider 模型调用**（无 Key）。
- 微信真机/合法域名（W5 遗留，待正式 AppID/AppSecret）。
- prod 部署、OSS（W8）；性能压测（W7）。

## 6. 已知限制

- 索引在发布请求线程 afterCommit 同步执行（事务外），政策极大时发布略慢；W7 可改有界线程池。
- 向量召回为全量内存余弦 + 缓存，分块 >1000 仅 warn，超限再评估 pgvector/Qdrant。
- 政策问答引用为模型返回序号 + 后端越界校验；真实模型的引用准确率需 Provider 配置后评估。
