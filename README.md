# 基于 Spring Boot 的房产中介管理系统

## 当前状态

- 当前技术方案版本：**V3.0**（W2 房源与客源管理已完成并通过端到端验收；W1 基座见方案第 21 节，W2 交付见第 22 节）。
- 完整方案：[`技术方案.md`](../技术方案.md)。
- W1 已交付三端工程：后端 `realestate-server`（Spring Boot 3.5.6 + JWT + Redis 会话）、Web `realestate-admin`（Vue 3 + TS）、小程序 `client`（原生，增量新增认证模块）。
- W2 在此基础上新增：房源 CRUD 与三态状态机（PENDING/PUBLISHED/OFFLINE）、条件筛选与排序（下推 SQL）、图片上传与媒体关联、房东/客源管理、客户收藏、数据权限隔离；Web 新增房源/客源页面，小程序新增房源列表/详情/收藏。
- `home/client` 是用户已创建的原生微信小程序工程，W1/W2 均保留并增量修改，未迁移 uni-app。
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
| Redis | 7.x | 已安装 7.4.0，**尚未启动** |
| 微信开发者工具 | 稳定版 | 已安装，项目 AppID 与登录状态待联调时确认 |
| Docker | 可选 | 未安装，不阻塞原生开发 |

当前端口：8080 和 5174 空闲；5173 由荣耀 `HnDocManagerJSServe.exe` 占用。已确认 Web 固定使用 5174，后端使用 8080，不停止该荣耀系统组件。

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
| 文件阶段 | 本地存储目录；生产再配置 OSS |
| 正式微信联调阶段 | 后端正式 AppID/AppSecret；W1 测试号使用账号密码登录，不需要 AppSecret |
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

首次启动由 Flyway 自动执行 `V1__init_auth.sql` 与 `V2__house_owner.sql` 建表，`BootstrapInitializer` 创建初始 `admin`/`customer` 账号（dev 默认密码 `Admin@123456`/`Customer@123456`，仅本地）。

图片上传目录 `realestate-server/uploads/` 由 `LocalStorageService` 启动时自动创建（已加入 `.gitignore`），dev 下通过 `http://localhost:8080/uploads/...` 访问；生产用 `STORAGE_TYPE=oss` 切换对象存储（W4 接入实现）。

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
2. 沿用当前测试号 AppID；W1 通过客户账号密码登录并验证 JWT/Refresh Token，不依赖 code2session。
3. 确认 `client/config/env.js` 的开发 API Base URL 为 `http://localhost:8080/api/v1`，然后点击编译。
4. 本地开发时按开发规范处理“不校验合法域名”选项；该选项只用于开发，不代表真机和生产可绕过域名要求。
5. 验证账号密码登录、Token 刷新和本人数据页面。
6. W2 已新增房源入口：首页「浏览房源」进入 `pages/house/list`，卡片支持出租/出售筛选与最新/价格排序；详情页 `pages/house/detail` 支持图片轮播与收藏（未登录点收藏会跳登录）。列表页「我的收藏」需要客户登录。
7. 正式微信 code 登录待 AppID/AppSecret 可用后接入；W5 再进行真机预览、上传/下载合法域名与弱网测试。

小程序 `wx.request` 不依赖浏览器 CORS，但后端接口认证、权限和 HTTPS 要求不变。

### 4.5 W2 功能入口与权限

| 端 | 入口 | 说明 |
|---|---|---|
| Web | 左侧菜单「房源管理」 | 列表（状态/类型/区域/价格/排序筛选）→ 新建/编辑（含图片上传）→ 详情（发布/下架/删除） |
| Web | 左侧菜单「客源管理」 | 房东与客源建档、编辑、逻辑删除；手机号仅 ADMIN 可见 |
| 小程序 | 首页「浏览房源」 | 已发布房源卡片列表，支持出租/出售筛选与最新/价格排序 |
| 小程序 | 房源详情页 | 图片轮播、价格与户型信息、收藏按钮 |
| 小程序 | 首页「我的收藏」 | 需客户登录；仅展示仍在架房源 |

权限规则（接口端强制，前端菜单只是展示）：

- 游客：仅 `GET /houses`、`GET /houses/{id}`，且只能看到 `PUBLISHED`；响应不含房东姓名与电话。
- CUSTOMER：可浏览已发布房源、收藏/取消收藏、查看本人收藏；访问房东接口或房源写接口返回 403。
- ADMIN：可管理全部房源、媒体与客源；房源详情返回房东联系方式。

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
- 客户不能读取他人合同、预约和报修。
- 核心异常返回正确 HTTP 状态、统一响应和 traceId。
- 请求日志不含密码、令牌、完整手机号或合同正文。

### 5.5 W2 冒烟脚本（需后端、Web、Redis、MySQL 已启动）

```bash
cd /d/graduate/home

# 1) 后端接口端到端（建房→上传→发布→权限→收藏→下架→删除，含 403/404/409 断言）
bash docs/smoke-w2.sh

# 2) Web 真实浏览器（需 Chrome 以 --remote-debugging-port=9222 启动）
node docs/web-smoke.mjs docs          # 登录/菜单/房源列表/客源页/刷新恢复会话
node docs/web-house-flow.mjs docs     # 表单填写→图片上传→保存→发布→列表校验

# 3) 小程序页面逻辑（Node 模拟 wx，不含 GUI 渲染）
node docs/mini-logic-smoke.mjs
```

脚本会自动创建测试数据；如需清理开发库，可执行
`DELETE FROM house_favorite; DELETE FROM house_media; DELETE FROM house; DELETE FROM owner_profile;`（仅本地 dev）。


## 六、生产部署摘要

推荐使用单台阿里云 ECS + Nginx + Spring Boot + MySQL/Redis + OSS：

1. 准备域名、HTTPS 证书、ECS 安全组和独立生产数据库。
2. 配置生产环境变量，确认 JWT/OSS/微信/AI 密钥不进入前端。
3. 执行数据库备份后再运行 Flyway 迁移。
4. Nginx 托管 Web `dist/` 并反向代理 `/api`，不向公网转发非必要 Actuator 端点。
5. 后端启用优雅关闭和健康检查；MySQL/Redis 不开放公网访问。
6. 合同和隐私文件放私有 OSS，通过短期签名 URL 下载。
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
| PDF 中文乱码或方块 | 字体是否嵌入、字体授权、HTML 模板字体声明 |
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
| W2 房源与客源 | **V3.0（当前）** |
| W3 交易与合同 | V4.0 |
| W4 租后服务与工具 | V5.0 |
| W5 小程序联调 | V6.0 |
| W6 智能增强 | V7.0 |
| W7 测试与优化 | V8.0 |
| W8 部署与论文材料 | V9.0 |

更新版本时必须同时记录真实交付、验证结果、架构/接口/数据库变化和已知限制。未验收不得只改版本号；启动命令、端口、目录或环境变量发生变化时，本 README 必须同步修改。
