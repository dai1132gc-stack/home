# 基于 Spring Boot 的房产中介管理系统

## 当前状态

- 当前技术方案版本：**V6.0.1**（W5 小程序联调已完成并通过端到端验收；V6.0.1 完成 W1～W5 审查修复 F1～F8，见方案 25.9 与 `docs/review-w1-w5.md`；W1 见方案第 21 节，W2 第 22 节，W3 第 23 节，W4 第 24 节，W5 第 25 节）。
- 完整方案：[`技术方案.md`](../技术方案.md)。
- W1 已交付三端工程：后端 `realestate-server`（Spring Boot 3.5.6 + JWT + Redis 会话）、Web `realestate-admin`（Vue 3 + TS）、小程序 `client`（原生，增量新增认证模块）。
- W2 在此基础上新增：房源 CRUD 与状态机、条件筛选与排序（下推 SQL）、图片上传与媒体关联、房东/客源管理、客户收藏、数据权限隔离；Web 新增房源/客源页面，小程序新增房源列表/详情/收藏。
- W3 新增：预约带看（客户提交、ADMIN 确认/取消/完成/爽约、客户取消）、合同（起草→待签→客户确认生效→终止，含房源/双方/条款快照）、交易节点（定金/签约/交房 + 状态日志）、中文合同 PDF（字体嵌入、金额大写、长地址换行、表格跨页、私有存储 + 鉴权下载）、房源状态扩展 `RESERVED/CONTRACTED` 并与预约/合同联动；Web 新增预约/合同页面，小程序新增预约看房/我的预约/我的合同。
- W4 新增：报修工单（客户提交、ADMIN 受理/处理/解决、客户关闭/撤销/重开、状态时间线、私有图片鉴权预览）、内容政策（教程/网签指引/公告、政策文档、草稿/发布/下架、纯文本正文、政策 PDF 附件）、房贷与税费测算（商贷/公积金、等额本息/本金、税费分档、公开限流、配置来源与免责声明）、客源跟进（复用 V2 `customer_follow`）、合同到期定时任务（Redis 锁、幂等、释放房源）；Web 新增报修/内容/跟进/测算页面，小程序新增报修/内容/测算页面。
- W5 新增：微信小程序 code 登录（`POST /auth/wechat-login` + `user_wechat_binding`，Flyway V5；未配置 AppID/AppSecret 返回 503 并降级账号密码，不信任客户端 openid、不保存 session_key）；小程序登录页微信一键登录与账号密码降级、401 单飞刷新、上传/下载显式 Bearer 与刷新重试、GET 重试/写请求不重试、私有图片预览、列表分页与错误重试、个人中心登录态修复；输出真机与合法域名清单。真实微信授权与真机扫码仍待正式凭据后由用户验证。
- `home/client` 是用户已创建的原生微信小程序工程，W1～W4 均保留并增量修改，未迁移 uni-app。
- 下文启动命令已随工程落地生效。
- 所有项目源码、启动说明和项目内文档统一放在 `D:/graduate/home/`；总体技术方案保留在 `D:/graduate/技术方案.md`。

## 一、规划中的项目结构

```text
D:/graduate/
├─ 技术方案.md
├─ 项目设计.txt
├─ 参考文献.txt
└─ home/
   ├─ README.md            # 项目启动与运行说明
   ├─ realestate-admin/     # Vue 3 + TypeScript Web 管理端
   ├─ realestate-server/    # Java 21 + Spring Boot 后端
   ├─ client/               # 已存在的原生微信小程序端（JavaScript + WXML/WXSS）
   ├─ deploy/               # Nginx 与部署模板
   └─ docs/                 # 数据库、接口、测试等项目文档
```

详细到模块和文件的目录约束见 `技术方案.md` 第 5 节。

## 二、开发环境要求与本机检查

### 2.1 推荐基线

| 软件 | 推荐版本 | 2026-11-08 本机检查 |
|---|---|---|
| Java | 21 LTS | 已安装 21.0.8，JAVA_HOME 已配置 |
| Maven | 3.9+ | 已安装 3.9.16 |
| Node.js | 22 LTS | 已安装 22.22.2 |
| npm | 10+ | 已安装 10.9.7 |
| Git | 2.x | 已安装 2.55.0 |
| MySQL | 8.0 | 已安装 8.0.43，服务与 3306 端口正常 |
| Redis | 7.x | 已安装 7.4.0，按 2.2 启动并验证 PONG |
| 微信开发者工具 | 稳定版 | 已安装，项目 AppID 与登录状态待联调时确认 |
| Docker | 可选 | 未安装，不阻塞原生开发 |

当前端口：后端固定 8080、Web 固定 5174；5173 由荣耀 `HnDocManagerJSServe.exe` 占用，本项目不停止该组件。

### 2.2 编码前必须完成：启动 Redis

在独立终端运行：

```bash
redis-server --bind 127.0.0.1 --port 6379
```

另开终端验证：

```bash
redis-cli -h 127.0.0.1 -p 6379 ping
```

预期返回：

```text
PONG
```

若系统找不到命令，可使用本机已安装路径：

```bash
"/c/Users/HONOR/dev-tools/redis/redis-server" --bind 127.0.0.1 --port 6379
```

该命令适用于 Git Bash。保持 Redis 终端运行，再进行后端开发。

### 2.3 MySQL 准备

W1 提供一键初始化脚本 `home/docs/init-db.sql`，用 root 执行一次即可创建 `realestate` 库与 `realestate_dev` 独立开发账号：

```bash
# PowerShell（不支持 < 重定向，用管道）
Get-Content D:/graduate/home/docs/init-db.sql | mysql -u root -p
# 或 CMD / Git Bash
mysql -u root -p < D:/graduate/home/docs/init-db.sql
```

- 字符集：`utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。
- 独立开发账号：`realestate_dev`，仅授予 `realestate` 库必要权限，应用不长期使用 root。
- 若忘记 root 密码，使用 `home/docs/reset-root.ps1`（管理员 PowerShell 执行 `& "D:\graduate\home\docs\reset-root.ps1"`）。
- 连接密码写入 `realestate-server/.env` 的 `DB_PASSWORD`，不提交 Git。

## 三、环境变量

W1 会在后端和 Web 目录提供 `.env.example`，在原生小程序目录提供 `config/env.example.js`。复制后填写本地值：

```bash
cp /d/graduate/home/realestate-server/.env.example /d/graduate/home/realestate-server/.env
cp /d/graduate/home/realestate-admin/.env.example /d/graduate/home/realestate-admin/.env
cp /d/graduate/home/client/config/env.example.js /d/graduate/home/client/config/env.js
```

原生小程序没有 uni-app 的 `.env` 构建流程；`client/config/env.js` 只保存 API 地址等非密钥配置，并加入 `.gitignore`。

最少需要配置：

| 端 | 配置 |
|---|---|
| 后端 | MySQL、Redis、JWT Access/Refresh 独立密钥、CORS Allowed Origins、日志目录 |
| Web | `VITE_API_BASE_URL=http://localhost:8080/api/v1` |
| 小程序 | `client/config/env.js` 中配置开发 API Base URL；测试号 AppID 已在 project.config.json |
| 文件阶段 | 公开媒体目录 `STORAGE_LOCAL_ROOT`（默认 `uploads`）；合同 PDF 私有目录 `STORAGE_PRIVATE_ROOT`（默认 `uploads-private`，不注册静态映射）；生产再配置 OSS |
| 微信登录（W5） | 后端 `WECHAT_MINI_APP_ID`、`WECHAT_MINI_APP_SECRET`、`WECHAT_MINI_TIMEOUT_SECONDS`（默认 4）；未配置时微信登录返回 503，账号密码登录不受影响 |
| Cookie（V6.0.1） | 后端 `APP_COOKIE_SECURE`：dev=false（http://localhost 可登录），prod 必须 true（仅 HTTPS 发送 Refresh Cookie）；Refresh Cookie 固定 HttpOnly + SameSite=Lax + Path=/api/v1/auth，前提是 Web 与 API 同站部署，跨站需另行评估 None+Secure |
| AI 阶段 | 后端 AI Base URL、模型、API Key、超时与限额 |

注意：

1. `.env` 不提交 Git，仓库只提交无害示例。
2. 前端 `VITE_*` 和小程序 `config/env.js` 都会被打包，不能存放任何 Secret。
3. 开发 CORS Origin 明确设置为 `http://localhost:5174`，不能在携带 Cookie 时使用 `*`。
4. 修改环境变量后重启后端、重新构建 Web 或重新编译小程序。

完整变量表见 `技术方案.md` 第 9 节。

## 四、本地启动步骤（W1 已验证可用）

### 4.1 启动顺序

```text
MySQL → Redis → Spring Boot 后端 → Web 管理端 → 小程序编译与开发者工具
```

### 4.2 启动后端

打开 Git Bash：

```bash
cd /d/graduate/home/realestate-server
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
```

> 注意：本机环境下 `spring-boot:run` 的 `server.port` 可能被环境源注入为随机端口，必须通过 `-Dspring-boot.run.arguments="--server.port=8080"` 强制指定 8080（详见技术方案 21.6 已知限制）。

首次启动由 Flyway 自动执行 `V1__init_auth.sql`、`V2__house_owner.sql`、`V3__appointment_contract.sql`、`V4__repair_content_calculator.sql` 与 `V5__wechat_binding.sql` 建表，`BootstrapInitializer` 创建初始 `admin`/`customer` 账号（dev 默认密码 `Admin@123456`/`Customer@123456`，仅本地）。

图片上传目录 `realestate-server/uploads/` 由 `LocalStorageService` 启动时自动创建（已加入 `.gitignore`），dev 下通过 `http://localhost:8080/uploads/...` 访问；生产用 `STORAGE_TYPE=oss` 切换对象存储（OSS 实现尚未接入，W4 私有文件仍落本地私有目录）。

合同 PDF 存放在**私有目录** `realestate-server/uploads-private/`（`STORAGE_PRIVATE_ROOT`，已加入 `.gitignore`），不注册静态映射，只能通过 `GET /api/v1/contracts/{id}/pdf` 鉴权下载。中文字体为 Noto Sans SC（SIL OFL 1.1），位于 `src/main/resources/fonts/`，来源与许可见该目录 `OFL.txt` 与技术方案 23.5。

W4 的报修图片与政策附件同样存放在 `uploads-private/`（`repair/yyyyMM/`、`policy/yyyyMM/`），数据库只保存相对 key，不产生公开 URL；报修图片通过 `GET /api/v1/repairs/{id}/media/{mediaId}`、政策附件通过 `GET /api/v1/policies/{id}/attachment` 鉴权流式读取。

### 4.3 启动 Web 管理端

另开 Git Bash：

```bash
cd /d/graduate/home/realestate-admin
npm install
npm run dev -- --port 5174
```

访问：

```text
http://localhost:5174
```

`npm install` 完成后应保留并提交 `package-lock.json`，后续环境优先使用 `npm ci` 还原依赖。

### 4.4 启动小程序端

原生小程序不需要执行 uni-app 构建命令。直接在微信开发者工具中：

1. 打开已有工程 `D:/graduate/home/client`，不要重新创建、覆盖或迁移该目录。
2. 沿用当前测试号 AppID；W5 起登录页支持「微信一键登录」，未配置后端 `WECHAT_MINI_APP_ID`/`WECHAT_MINI_APP_SECRET` 时提示后使用账号密码登录。
3. 确认 `client/config/env.js` 的开发 API Base URL 为 `http://localhost:8080/api/v1`，然后点击编译。
4. 本地开发时按开发规范处理“不校验合法域名”选项；该选项只用于开发，不代表真机和生产可绕过域名要求。
5. 验证账号密码登录、Token 刷新和本人数据页面。
6. W2 已新增房源入口：首页「浏览房源」进入 `pages/house/list`，卡片支持出租/出售筛选与最新/价格排序；详情页 `pages/house/detail` 支持图片轮播与收藏（未登录点收藏会跳登录）。列表页「我的收藏」需要客户登录。
7. W3 新增客户入口：房源详情底部「预约看房」→ `pages/appointment/create`（选日期/时间、填电话，未登录跳登录）；首页「我的预约」→ `pages/appointment/list`（可取消待确认/已确认预约）；首页「我的合同」→ `pages/contract/list`，详情页可确认签署并下载 PDF（`wx.downloadFile` + `wx.openDocument`）。
8. W5 新增登录方式：登录页「微信一键登录」（`wx.login` → `POST /auth/wechat-login`），失败时明确提示并保留账号密码登录；未配置 `WECHAT_MINI_APP_ID`/`WECHAT_MINI_APP_SECRET` 时后端返回 503，小程序提示“微信登录未配置，请使用账号密码”，不会伪造成功。真实微信授权与真机扫码需正式凭据，见 `docs/w5-device-checklist.md`。
9. W4 新增客户入口：首页「租房指南/公告」进入 `pages/content/list`（按分类筛选）→ 详情纯文本渲染；「政策文件」进入政策列表；「房贷计算」进入 `pages/calculator/index`（房贷/税费）；登录后「我的报修」进入 `pages/repair/list`，可提交、撤销、确认完成、重新打开并查看私有图片；房源详情与 ACTIVE 合同详情提供「申请报修」入口。

小程序 `wx.request` 不依赖浏览器 CORS，但后端接口认证、权限和 HTTPS 要求不变。

### 4.5 W2/W3/W4 功能入口与权限

| 端 | 入口 | 说明 |
|---|---|---|
| Web | 左侧菜单「房源管理」 | 列表（状态/类型/区域/价格/排序筛选）→ 新建/编辑（含图片上传）→ 详情（发布/下架/删除）；状态含待发布/已发布/已预定/已签约/已下架 |
| Web | 左侧菜单「客源管理」 | 房东与客源建档、编辑、逻辑删除；手机号仅 ADMIN 可见；列表「跟进/详情」进入客源跟进（新增、分页、下次跟进时间） |
| Web | 左侧菜单「预约管理」 | 状态/日期筛选；确认（房源置已预定）、取消、完成、标记爽约 |
| Web | 左侧菜单「合同管理」 | 列表/起草（选房源与客户、租期、金额、押金）/详情（双方、交易节点、状态日志、PDF 预览与下载）/提交待签/终止 |
| Web | 左侧菜单「报修管理」 | 状态/分类/日期筛选；受理、开始处理、完成处理、关闭；详情抽屉含问题描述、私有图片预览与状态时间线 |
| Web | 左侧菜单「内容管理」 | 教程与公告、政策文件两页；新建/编辑（纯文本 + 换行预览）/发布/下架/删除；政策附件上传与鉴权下载 |
| Web | 左侧菜单「测算工具」 | 商业贷款/公积金、等额本息/本金、首付与利率、按年汇总、费率来源与免责声明；税费明细与分档规则 |
| 小程序 | 首页「登录 / 微信一键登录」 | 微信 code 登录；未配置凭据时提示后使用账号密码降级；登录成功保留返回页面 |
| 小程序 | 首页「浏览房源」 | 已发布房源卡片列表，支持出租/出售筛选与最新/价格排序 |
| 小程序 | 房源详情页 | 图片轮播、价格与户型信息、收藏、预约看房、申请报修 |
| 小程序 | 首页「我的收藏」 | 需客户登录；仅展示仍在架房源 |
| 小程序 | 首页「我的预约」 | 提交记录、状态、取消本人待确认/已确认预约 |
| 小程序 | 首页「我的合同」 | 列表/详情、待签署合同确认、下载 PDF、ACTIVE 合同申请报修 |
| 小程序 | 首页「我的报修」 | 提交（分类/紧急度/描述/最多 6 张图片/电话/期望时间）、状态筛选、撤销、确认完成、重新打开、私有图片预览 |
| 小程序 | 首页「租房指南/公告/政策文件」 | 公开只读已发布内容，详情按纯文本换行渲染 |
| 小程序 | 首页「房贷计算」 | 商贷/公积金、等额本息/本金、税费测算与结果说明 |

权限规则（接口端强制，前端菜单只是展示）：

- 游客：仅 `GET /houses`、`GET /houses/{id}`、`GET /guides`、`GET /guides/{id}`、`GET /policies`、`GET /policies/{id}`、`POST /calculators/mortgage`、`POST /calculators/tax`、`GET /calculators/config`；报修/合同/预约/PDF/政策附件一律 401。
- CUSTOMER：可浏览已发布房源与内容、收藏、提交与取消本人预约、查看与确认本人合同、下载本人 PDF、提交/撤销/关闭/重开本人报修、读取本人报修图片；访问他人报修/图片/合同/预约返回 404。
- ADMIN：可管理全部房源、媒体、客源与跟进、预约、合同、报修、内容政策与测算配置；合同起草/提交/终止、内容发布/下架、到期任务手动触发仅 ADMIN。
- 房源状态联动：预约确认 `PUBLISHED→RESERVED`；合同生效 `→CONTRACTED`；合同终止或到期且无其他占用合同 `→PUBLISHED`。`RESERVED/CONTRACTED` 不出现在公开列表，ADMIN 管理端可见。
- 报修状态联动：`SUBMITTED→ACCEPTED→PROCESSING→RESOLVED→CLOSED`，允许 `CANCELLED`/`REOPENED`；非法迁移与重复操作 409。

## 五、构建与测试命令（对应阶段完成后生效）

### 5.1 后端

```bash
cd /d/graduate/home/realestate-server
mvn clean test
mvn clean package
```

生产启动入口：

```bash
java -jar target/realestate-server.jar --spring.profiles.active=prod
```

实际 jar 文件名以 W1 `pom.xml` 配置为准；生产密钥通过环境变量注入，不能附加在公开命令或提交到仓库。

### 5.2 Web

```bash
cd /d/graduate/home/realestate-admin
npm run lint
npm run test:unit
npm run build
```

生产构建产物为 `dist/`，由 Nginx 托管。

### 5.3 小程序

原生 `client` 以微信开发者工具的编译结果为校验依据；若 W1 添加 npm 工具脚本，可额外执行：

```bash
cd /d/graduate/home/client
npm run lint
```

在微信开发者工具中打开 `D:/graduate/home/client` 并执行编译、模拟器和体验版验证。不存在 `dist/dev/mp-weixin` 或 `build:mp-weixin` 命令；真机预览与发布前检查按后续交付流程执行。

### 5.4 每阶段最低验证

- 后端编译和测试通过。
- Web lint、单测和构建通过。
- 涉及小程序的阶段完成编译与关键页面冒烟。
- 登录、Access Token 过期、Refresh Token 轮换、退出登录无循环刷新。
- 微信登录未配置返回 503 且账号密码可用；不信任客户端 openid、不保存 session_key。
- 客户不能读取他人合同、预约和报修。
- 核心异常返回正确 HTTP 状态、统一响应和 traceId。
- 请求日志不含密码、令牌、完整手机号或合同正文。
- V6.0.1 安全口径：Refresh Token 轮换由 Redis Lua **单脚本原子完成“旧 token 标记 ROTATED + 新 token 写入 + 新 token 入会话族”**，同一 token 并发刷新恰 1 成功 1 401，败者撤销族后胜者新 token 必然失效（客户端必须单飞刷新）；禁用账号最迟 30 秒生效（`UserStatusService` 缓存 + 过滤器校验）；登录 IP 维度统计所有尝试（10 次/分）、用户名维度只统计失败（5 次/分，成功登录清零），微信登录按 IP 10 次/分，超限 429，Redis 故障 fail-open；`X-Forwarded-For` 仅在可信代理后可信；`JWT_REFRESH_SECRET` 为历史兼容/预留，不参与签名。

### 5.5 W2/W3/W4/W5 冒烟脚本（需后端、Web、Redis、MySQL 已启动）

```bash
cd /d/graduate/home

# 1) 后端接口端到端
bash docs/smoke-w2.sh          # W2 回归：建房→上传→发布→权限→收藏→下架→删除（21 项）
bash docs/smoke-w3.sh          # W3：预约→确认→RESERVED→合同→PDF→生效→CONTRACTED→终止→PUBLISHED，
                               #     含 401/403/404/409、脱敏、快照、PDF 鉴权、并发激活与占用、EXPLAIN（54 项）
bash docs/smoke-w4.sh          # W4：V4 迁移与 customer_follow 结构、内容/政策、报修全链路与私有图片、
                               #     跟进、测算与限流、到期任务、EXPLAIN（94 项）

# 2) Web 真实浏览器（需 Chrome 以 --headless=new --remote-debugging-port=9222 启动）
node docs/web-smoke.mjs docs        # 登录/菜单/房源列表/客源页/刷新恢复会话
node docs/web-house-flow.mjs docs   # 表单填写→图片上传→保存→发布→列表校验
node docs/web-w3-flow.mjs docs      # 预约页/合同列表/详情/起草表单/房源状态筛选（截图写入 docs/）
node docs/web-w4-flow.mjs docs      # 报修/内容/政策/测算/客源跟进（截图写入 docs/）

# 3) 小程序页面逻辑（Node 模拟 wx，不含 GUI 渲染）
node docs/mini-logic-smoke.mjs      # W2 浏览/收藏 + W3 预约/取消/合同确认/PDF 下载
node docs/mini-w4-flow.mjs          # W4 报修提交/图片上传/流转、内容政策、房贷与税费测算
node docs/mini-w5-flow.mjs          # W5 微信未配置降级/模拟成功、账号密码登录、401 单飞刷新、
                                    #     弱网重试策略、上传下载鉴权、全流程页面与个人中心

# 4) V6.0.1 认证加固（18 项）：F1 并发 200/401 + 胜者新 token 失效、F2 Cookie 属性、
#    F4 IP 第 11 次 429 + 成功登录不误伤
bash docs/smoke-auth-hardening.sh
```

W5 真机与合法域名项无法自动化，清单与验收边界见 `docs/w5-device-checklist.md`；真实微信授权、jscode2session 与扫码项标注“待用户验证”，不得表述为真机通过。

`smoke-w3.sh`/`smoke-w4.sh` 会创建隔离测试账号 `customer2`（复用 `customer` 密码哈希，仅本地 dev），并临时插入 2000 条样本做 EXPLAIN 后立即清理。W4 脚本会按 `W4冒烟%` 前缀清理数据库测试行；`uploads-private/` 下的测试图片文件不会自动删除，可按需手工清理。脚本仅限本地 dev，生产不得执行。

合同 PDF 样张：`docs/sample-contract.pdf` 与 `docs/sample-contract-page1.png`、`sample-contract-page2.png`（人工核对中文、长地址与跨页）。


## 六、生产部署摘要

推荐使用单台阿里云 ECS + Nginx + Spring Boot + MySQL/Redis + OSS：

1. 准备域名、HTTPS 证书、ECS 安全组和独立生产数据库。
2. 配置生产环境变量，确认 JWT/OSS/微信/AI 密钥不进入前端。
3. 执行数据库备份后再运行 Flyway 迁移。
4. Nginx 托管 Web `dist/` 并反向代理 `/api`，不向公网转发非必要 Actuator 端点。
5. 后端启用优雅关闭和健康检查；MySQL/Redis 不开放公网访问。
6. 合同、报修图片和政策附件放私有 OSS，通过短期签名 URL 下载；公开房源图片走 CDN。
7. 微信公众平台配置 API、上传和下载合法域名。
8. 验证备份恢复、日志滚动、磁盘容量和告警。

详细约束见 `技术方案.md` 第 16 节。

## 七、常见问题

| 问题 | 优先检查 |
|---|---|
| 后端启动即认证/Redis 异常 | Redis 是否启动、6379 是否监听、密码和逻辑库是否一致 |
| Web 端口自动变化 | 5173 已占用，本方案统一使用 5174，并同步 CORS |
| 浏览器跨域但 Postman 正常 | Origin 是否明确允许、预检 OPTIONS、withCredentials 与 allowCredentials 是否匹配 |
| 页面刷新后重复退出 | Web Access Token 应在内存，刷新 Cookie 是否正确发送；检查单飞刷新和重试次数 |
| 小程序开发者工具可用、真机失败 | HTTPS 合法域名、AppID、Token Storage、上传/下载域名和网络权限 |
| 微信登录返回 503 `AUTH_50301` | 后端 `.env` 是否配置 `WECHAT_MINI_APP_ID`/`WECHAT_MINI_APP_SECRET`，是否重启；未配置时属预期降级，用账号密码登录 |
| 微信登录 401 `AUTH_40105` | code 一次性使用，已使用/过期需重新点击登录；不要复用同一 code |
| 微信登录 502 `AUTH_50201` | 服务器到 `api.weixin.qq.com` 的网络/DNS/超时；接口不自动重试，稍后再试 |
| 小程序上传/下载 401 | 请求是否显式带 Bearer；Access Token 过期应自动刷新并重试一次，仍失败回登录页 |
| 真机请求全部超时、工具正常 | `apiBaseUrl` 不能是 `localhost`；手机与后端网络互通，使用 HTTPS 且配置合法域名 |
| PDF 中文乱码或方块 | 字体是否嵌入（`resources/fonts/NotoSansSC-Regular.ttf`）、`OFL.txt` 是否随包；PDF 由服务端生成，浏览器字体不影响 |
| 合同提交/确认报 409 | 该房源已有待签署或生效中的合同；先终止旧合同，或确认操作重复提交 |
| 报修受理/关闭报 409 | 工单状态已被他人变更（重复受理、跳过处理直接关闭、已撤销后再操作）；刷新详情查看时间线 |
| 报修图片 401/404 | 图片为私有存储，需登录且仅本人或 ADMIN；他人访问按 404 处理，不能直接用 `/uploads` 访问 |
| 报修图片上传 400/413 | 仅 JPG/PNG/GIF/WEBP 且真实文件头；单图 ≤10MB；单工单最多 6 张 |
| 测算接口 429 | 同一 IP 每分钟超过 60 次；等待下一分钟窗口或检查代理是否统一了 X-Forwarded-For |
| 测算返回 400“未找到规则” | 城市/金额区间或贷款类型+年限没有生效配置；检查 `loan_rate_config`/`tax_rule_config` 或手工输入利率 |
| 政策附件 401 | 附件下载需登录；公开列表/详情匿名可读，附件不匿名开放 |
| 内容草稿/下架后看不到 | 公开接口只返回 PUBLISHED；ADMIN 登录后可按状态筛选查看 |
| 到期任务没有处理合同 | end_date 必须早于今天且状态为 ACTIVE；检查 Redis 锁 `lock:contract:expiry` 与后端日志 |
| 合同 PDF 404 | 草稿阶段生成失败不阻塞（看后端日志）；提交待签/生效前必须生成，失败会返回 500 |
| 预约确认报 409 | 房源已被其他预约占用或已下架/签约；先在预约管理取消占用预约 |
| 房源列表看不到已预定房源 | 公开列表仅展示 `PUBLISHED`；`RESERVED/CONTRACTED` 只在 Web 管理端可见（见技术方案 23.8） |
| 重复签约或状态覆盖 | 事务边界、房源行锁、状态机、version 和幂等键，不在 Controller 补丁式判断 |
| 房源发布/下架报 409 | 状态机只允许 PENDING/OFFLINE→PUBLISHED、PUBLISHED→OFFLINE；提示“已被他人修改”说明 version 过期，刷新后重试 |
| 图片上传 400 | 仅接受 JPG/PNG/GIF/WEBP 且需真实文件头；非图片或超过 10MB 会被拒绝 |
| Web 图片裂图 | 后端 8080 是否启动、`/uploads` 代理是否生效、`uploads/` 目录是否有文件 |
| 房源列表游客看不到 | 只有 `PUBLISHED` 对游客可见；PENDING 需先在管理端发布 |
| Docker 未安装 | 本地原生开发可继续；仅 Testcontainers/容器部署需要补装 |

## 八、技术方案版本维护

每完成一个阶段并验收后，必须同步更新 `技术方案.md` 和本 README：

| 已完成阶段 | 技术方案版本 |
|---|---|
| 当前初始方案 | V1.0 |
| W1 工程基座与认证 | V2.0 |
| W2 房源与客源 | V3.0 |
| W3 交易与合同 | V4.0 |
| W4 租后服务与工具 | **V5.0** |
| W5 小程序联调 | **V6.0** |
| W1～W5 审查修复（F1～F8） | **V6.0.1（当前）** |
| W6 智能增强 | V7.0 |
| W7 测试与优化 | V8.0 |
| W8 部署与论文材料 | V9.0 |

更新版本时必须同时记录真实交付、验证结果、架构/接口/数据库变化和已知限制。未验收不得只改版本号；启动命令、端口、目录或环境变量发生变化时，本 README 必须同步修改。
