# W1～W5 阶段性代码审查报告

- 审查对象：`d:/graduate/home`（git 根），分支 `main`，HEAD `ed653ef`（W5），工作区干净，审查针对 HEAD。
- 审查时间：2026-11-08
- 范围：后端 221 个主源码文件 + 27 个测试类、Web 全 src、小程序 30 个 JS / 20 个 JSON、Flyway V1~V5、docs 冒烟脚本
- 模式：只读静态审查 + 构建/单测；未启动服务、未跑运行时冒烟、未修改任何源码/配置/数据

## 1. 审查结论

**有条件通过** — 无 P0 阻断项；1 个 P1（Refresh Token 轮换非原子）与 3 个 P2（Refresh Cookie 缺 Secure/SameSite、禁用账号 Access Token 不失效、登录接口无限流）须在 W6 前修复（见第 9 节）。整体架构分层、状态机条件更新、数据权限 SQL 下推、私有存储隔离、唯一键并发兜底均与文档声明一致。

## 2. 评分卡

| 维度 | 分(1-5) | 理由 |
|---|---|---|
| 架构与分层 | 5 | Controller/Service/Mapper 边界清晰，Service 不依赖 Servlet API，VO/DTO 分离 |
| 认证与授权 | 3 | 设计优秀（opaque Refresh、重放检测、绑定校验），但存在轮换竞态 P1 与 Cookie/状态/限流 3 个 P2 |
| 数据与 SQL | 5 | 无 `${}`、排序白名单、显式列、deleted=0 全覆盖、size 上限 100、V1~V5 未被修改 |
| 并发与事务 | 4 | 条件更新 + 唯一键 + 行锁闭环完整；扣分：Refresh 竞态、PDF 事务内 IO 残留 |
| 文件与 PDF | 4 | 私有目录未注册静态映射、鉴权下载、字体子集嵌入；扣分：孤儿文件无清理 |
| 业务正确性 | 5 | 快照固化、测算尾差处理、状态机非法迁移 409 均有测试与冒烟证据 |
| Web 前端 | 5 | 内存 Token、单飞刷新、无 v-html、构建无 sourcemap |
| 小程序 | 5 | 显式 Bearer、401 队列、GET/写重试纪律、navigateTo 溢出兜底、纯文本渲染 |
| 配置/密钥/日志 | 4 | fail-fast、无真实密钥入库、日志脱敏；扣分：Cookie 属性缺失、冗余 refresh-secret |
| 异常与可观测 | 4 | 统一响应 + traceId；扣分：Swagger 放行分散、禁用账号 access 窗口 |
| 测试质量 | 4 | 167 项全部行为级断言、无 mock 被测对象；扣分：无 Mapper XML 集成测试 |
| 性能 | 4 | 索引命中、分页下推；扣分：PDF 同步生成（已声明）、价格排序 filesort（已声明） |
| 文档一致性 | 4 | 大体诚实；扣分：SameSite/HS384 两处声明超前于实现 |

## 3. 问题清单

| ID | 级别 | 维度 | 文件:行 | 问题 | 影响 | 证据 | 建议修复 | 状态 |
|---|---|---|---|---|---|---|---|---|
| F1 | P1 | 认证并发 | realestate-server/src/main/java/com/example/realestate/security/service/RefreshTokenService.java:68-88 | refresh() 为 GET→SET 两步，无 Lua/锁；同一 token 并发刷新可双成功且不触发重放检测 | 窃取 token 竞速可静默获得并行会话 | 静态（2/2 验证） | Lua 原子化“读状态+标记+签发” | 确认 |
| F2 | P2 | 认证配置 | realestate-server/src/main/java/com/example/realestate/modules/auth/controller/AuthController.java:112-118 | Refresh Cookie 未设 Secure/SameSite，方案声称 SameSite=Lax 未实现 | HTTPS 下可经 HTTP 泄露；CSRF 依赖浏览器默认值 | 静态（2/2） | ResponseCookie 显式 SameSite=Lax + prod Secure | 确认 |
| F3 | P2 | 授权 | realestate-server/src/main/java/com/example/realestate/security/filter/JwtAuthenticationFilter.java:44-51 | 过滤器仅验签不查用户状态；禁用账号 access token 剩余 TTL（≤15min）内可用 | 封号不能即时生效 | 静态（2/2） | Redis 用户状态缓存校验或文档明示权衡 | 确认 |
| F4 | P2 | 认证 | realestate-server/src/main/java/com/example/realestate/modules/auth/controller/AuthController.java:45-56 | /login 与 /wechat-login 无任何限流（全工程仅测算有限流） | 密码爆破、jscode2session 调用放大 | 静态（2/2） | 复用 Redis 计数限流 + 失败退避 | 确认 |
| F5 | P3 | 文件一致性 | realestate-server/src/main/java/com/example/realestate/modules/contract/service/impl/ContractServiceImpl.java:549-561 | 事务内写 PDF 文件，状态更新回滚后文件残留孤儿，无清理 | uploads-private 缓慢膨胀 | 静态（2/2） | afterCommit 写盘或失败路径删除 | 确认 |
| F6 | P3 | 配置 | realestate-server/src/main/java/com/example/realestate/config/SecurityConfig.java:64-68 | swagger/api-docs 无条件放行；prod 仅靠 springdoc 开关兜底 | dev/test API 文档公开 | 静态（2/2） | 按 profile 收紧 | 确认 |
| F7 | P3 | 文档 | realestate-server/src/main/java/com/example/realestate/security/jwt/JwtTokenProvider.java:38-43 | 方案声称固定 HMAC-SHA384，实现按密钥长度动态选 HS256/384/512 | 文档失真 | 静态（2/2） | 固定算法或改文档 | 确认 |
| F8 | P3 | 配置 | realestate-server/src/main/resources/application-dev.yml:23-24 | refresh-secret 不参与任何运算（Refresh 已 opaque），仍被强制配置 | 误导运维/评审 | 静态（1/2：一方认为属“仅校验的死配置”） | 移除或改名注释“预留未用” | 确认 |

无 P0。

## 4. 需求-实现-验证追溯矩阵

| 阶段 | 核心能力 | 代码位置 | 验证证据 | 状态 |
|---|---|---|---|---|
| W1 | JWT+Refresh 轮换/重放/撤销 | security/*、AuthServiceImpl | 单测（Redis mock 行为断言）+ mvn test 通过 | 已实现且验证（轮换原子性除外，见 F1） |
| W1 | 角色↔客户端绑定 | AuthServiceImpl:173-178 | 单测 + W1 冒烟记录 | 已实现且验证 |
| W2 | 房源 CRUD/状态机/筛选下推 | HouseMapper.xml:38-51 等 | 单测 + smoke-w2（未复跑） | 已实现，运行时验证未复核 |
| W2 | 收藏唯一键幂等、数据权限 404 | HouseFavoriteServiceImpl | 单测 | 已实现且验证 |
| W3 | 预约/合同状态机、并发占用唯一键、行锁 | ContractServiceImpl:297-355 | 单测 + smoke-w3 并发 200/409（未复跑） | 已实现且验证 |
| W3 | 快照固化、中文 PDF 私有下载 | ContractSnapshot、PdfBox 生成器 | 单测 16 项金额大写 + 样张 | 已实现且验证 |
| W4 | 报修七态、私有图片鉴权 | RepairServiceImpl:159-269 | 单测 + smoke-w4（未复跑） | 已实现且验证 |
| W4 | 测算精度/限流/到期任务幂等 | MortgageCalculator、CalculatorRateLimiter、ContractExpiryServiceImpl | 单测（含 fail-open、幂等） | 已实现且验证 |
| W5 | 微信 code 登录 503/401/502、唯一键兜底 | WechatAuthServiceImpl、WechatUserRegistrar | 15 项单测覆盖冲突回滚重查 | 已实现且验证（真实 provider 未验证，已知限制） |
| W5 | 小程序 401 单飞/上传下载鉴权 | client/utils/request.js:66-179 | mini-w5-flow.mjs（wx+后端混合 mock） | 已实现且验证 |

## 5. 高风险点逐项结论（12 项）

| # | 项 | 结论 |
|---|---|---|
| 1 | Refresh 并发轮换 | **风险**（F1，P1）：非原子，可双成功；重放撤销可能误伤正常重试 |
| 2 | 微信首次登录并发 | **通过**：独立事务 Bean，冲突整体回滚重查，无孤儿 sys_user（WechatUserRegistrar.java:47-68） |
| 3 | 微信登录无限流 | **风险**（F4）：可刷接口放大上游调用；错误码未泄露配置细节 |
| 4 | 公开白名单 | **通过**：Spring `*` 单段通配，favorite/media 等为 POST/DELETE 不受 GET 放行影响；`/uploads/**` 仅映射公开根（WebMvcConfig.java:27-31） |
| 5 | 数据权限 SQL 下推 | **通过**：appointment/contract/repair 的 forcedCustomerId 下推且 Service 强制覆写客户端参数；详情/媒体/PDF 归属校验 404 |
| 6 | 合同激活并发 | **通过**：行锁 + 生成列唯一键 + 条件更新闭环（ContractServiceImpl.java:335-350） |
| 7 | 到期任务锁与幂等 | **通过**：SET NX + Lua token 比对解锁（RedisDistributedLock.java:18-46）；逐条独立事务幂等 |
| 8 | 私有文件 | **通过**：文件头校验、路径 normalize、无私有静态映射、鉴权流式 + nosniff；残留问题仅 F5 |
| 9 | 测算精度 | **通过**：末期取剩余本金保证本金合计恒等（MortgageCalculator.java:66-88） |
| 10 | 小程序 401 与上传重试 | **通过**：单飞 + 队列 + 重试一次；上传 401 先于业务处理，重试不产生重复落库；跳登录 reLaunch 兜底 |
| 11 | 日志隐私 | **通过**：AccessLogFilter 只记元数据，不记请求体/令牌/完整手机号；微信日志不记 code/openid/session_key |
| 12 | 测试真实性 | **基本通过**：surefire 实测 167 项全过且为行为断言；缺口为无 XML 集成测试（测试债，计入评分） |

## 6. 测试与构建实测记录

| 命令 | 结果 | 备注 |
|---|---|---|
| `mvn clean test`（后端） | 通过，**surefire 实测 167 项、0 失败** | 与文档声称一致；控制台中文乱码为已知 GBK 显示问题 |
| `npm run lint`（Web） | 通过 | eslint --fix 无改动源码语义 |
| `npm run test:unit`（Web） | 通过，23 项 | 与声称一致 |
| `npm run build`（Web） | 通过，dist 产出；chunk >500kB 警告 | 无 sourcemap |
| 小程序 30 个 JS `node --check` + 20 个 JSON 解析 | 全部通过 | |
| `bash -n docs/smoke-w{1..4}.sh` | 语法全部通过（未执行） | 运行时冒烟未授权，未执行 |
| git 历史核查 V1~V5 | 各自仅出现在本阶段提交，未被后续修改 | |

副作用说明：mvn/npm 构建重建了 target/ 与 dist/ 产物目录（正常构建行为）；未启动任何服务、未连接数据库/Redis、未改动源码与数据。

## 7. 文档一致性偏差

1. 技术方案 21.2「SameSite=Lax」与「生产 Secure」— 代码未实现（F2）。
2. 技术方案 21.2「HMAC-SHA384」— 实现按密钥长度动态选择（F7）。
3. 技术方案 23.8 只声明「PDF 同步生成阻塞线程」，未声明回滚孤儿文件（F5 为未声明缺陷）。
4. 其余声称（测试数 167/23、表结构、接口清单、已知限制 24.9/25.8）抽查均与实际一致；限流 XFF 伪造、fail-open、单实例到期任务、私有存储本地化均已在文档诚实声明，未重复计为缺陷。

## 8. 做得好的地方

1. Refresh 采用 opaque + Redis 哈希摘要 + 会话族撤销，设计上优于 JWT Refresh。
2. 合同并发占用用「生成列 + 唯一键 + DuplicateKeyException 映射 409」三重兜底，且有实测记录。
3. 微信首次登录用独立事务 Bean，并发冲突回滚重查，不留孤儿用户。
4. 全部列表数据权限下推 SQL 且强制覆写客户端参数，杜绝应用层过滤绕过。
5. Mapper XML 零 `${}`，排序白名单，列表显式列不带大字段。
6. 分布式锁解锁用 Lua token 比对，是教科书式正确实现。
7. 冒烟脚本有真实断言、失败退出非零、前缀清理、dev-only 声明。
8. 单测 167 项全部行为级断言，无 mock 被测对象、无 sleep 依赖。

## 9. W6 前修复优先级（backlog）

| 序 | 项 | 影响面 | 建议改法 | 验证 |
|---|---|---|---|---|
| 1 | F1 Refresh 轮换原子化 | 全部会话 | Lua：`GET` 状态→仅 ACTIVE 才 `SET` ROTATED 并返回会话，脚本外签发新 token | 新增并发刷新单测（双线程同一 token 断言 1 成功 1 401） |
| 2 | F2 Cookie Secure/SameSite | Web 端 | ResponseCookie 显式 SameSite=Lax，`app.cookie.secure` 配置驱动 prod=true | 登录后检查 Set-Cookie 头 |
| 3 | F4 登录限流 | 认证入口 | 复用 CalculatorRateLimiter：login 按 IP+用户名失败计数退避；wechat-login 按 IP 限流 | 第 N+1 次 429 单测 |
| 4 | F3 禁用账号窗口 | 账号管理 | Redis 用户状态缓存（禁用即失效）或文档明示 15min 窗口 | 禁用后立即访问断言 401 |
| 5 | F5 PDF 孤儿文件 | 磁盘 | 失败路径删文件或 afterCommit 写盘 | 模拟 activate 冲突后目录无新增文件 |
| 6 | F6/F7/F8 配置与文档 | 可维护性 | prod 收紧 Swagger；固定或更正算法声明；移除 refresh-secret | 文档与代码一致 |

## 10. 遗留风险与未验证项

- 运行时冒烟（w2/w3/w4）、EXPLAIN 复核未执行（需授权，会写 dev 库）。
- 真实微信 jscode2session、真机、合法域名（无正式凭据，已知限制）。
- Mapper XML 无集成测试，SQL 正确性依赖冒烟兜底。
- XFF 伪造绕过限流（文档已声明需 Nginx 覆盖）、限流 fail-open、到期任务单实例（均已知限制）。
- OSS、生产部署、性能压测不在本轮范围。

## 11. 覆盖声明

已读：后端 security/config/auth/wechat 全部，contract/appointment/repair/house/calculator 核心 Service 与 XML，V1~V5 全文，logging/lock/pdf 组件，Web request/auth/router 与关键视图，小程序 request/auth/api 与关键页面，smoke-w4 全文与其余脚本抽读，README/技术方案 21~25 节/MEMORY.md。抽样未逐行覆盖：全部 WXML/WXSS、部分 VO/DTO/entity、logback 细则。方法局限：竞态与并发结论为静态推断，未经运行时复现。

## 12. 追加 25 项速查

| # | 项 | 结论 |
|---|---|---|
| 1 | git ls-files 误提交检查 | 通过（仅 .env.example，无 .env/target/dist/node_modules/uploads/日志） |
| 2 | 全仓密钥模式扫描 | 通过（仅占位符与 dev 默认密码，已标注仅本地） |
| 3 | init-db.sql / reset-root.ps1 硬编码 | 通过（无真实密码） |
| 4 | 请求体日志 | 通过（AccessLogFilter 不读 body，无 LOG_BODY_ENABLED 滥用） |
| 5 | 上传目录脚本执行风险 | 通过（UUID 重命名 + 真实文件头校验） |
| 6 | prod CORS | 通过（明确 Origin，无 `*`） |
| 7 | Flyway clean/失败阻断 | 通过 |
| 8 | 优雅关闭 | 待确认（未细查 shutdown 配置） |
| 9 | Redis key TTL | 通过（会话/限流/锁均有 TTL） |
| 10 | X-Forwarded-For 信任 | 风险（可伪造绕过限流；技术方案 24.9 已声明需 Nginx 覆盖） |
| 11 | 金额前端运算 | 通过（前端仅格式化展示，不参与运算） |
| 12 | 日期/时区 | 通过（UTC + DATETIME(3)） |
| 13 | 分页 size 上限 | 通过（PageQuery 上限 100） |
| 14 | 排序注入 | 通过（`<choose>` 白名单） |
| 15 | 404/403 语义 | 通过（他人资源 404，管理员接口 403） |
| 16 | 唯一约束参与写入 | 通过（收藏/合同占用/openid 均落库校验） |
| 17 | 合同/房源异常路径一致性 | 风险（F5 孤儿 PDF 文件） |
| 18 | 定时+手动触发互斥 | 通过（同一 Redis 锁） |
| 19 | 微信 code 重放/限流 | 风险（F4：code 一次性由微信保证，但接口无限流） |
| 20 | 多设备会话隔离 | 通过（familyId 会话族隔离，退出只撤销当前） |
| 21 | Cookie Path/SameSite | 部分通过（Path 收敛，SameSite/Secure 缺失见 F2） |
| 22 | 小程序 Storage 清理时机 | 通过（退出/刷新失败均 clearAuth） |
| 23 | 前端错误提示直出后端 message | 通过（错误码已归一化，泄露面小） |
| 24 | Web 生产 sourcemap | 通过（未开启） |
| 25 | 小程序包体积/首屏 | 通过（代码层未见明显问题） |

---

> 交互式审查报告（含文件级证据与逐项修复入口）已通过 code-review-report 插件同步发布（review-308d1550-34bf-4fa7-bbc5-2dda26a92863，466 个文件、8 项发现）。
