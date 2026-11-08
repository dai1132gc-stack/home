#!/usr/bin/env bash
# W4 租后服务与工具端到端冒烟（需后端 8080、Redis、MySQL 可用）
# 覆盖：V4 迁移与 customer_follow 结构、内容/政策发布可见性、报修全链路与私有图片、
#       客源跟进、房贷/税费测算与限流、合同到期任务、EXPLAIN 索引。
# 用法：bash docs/smoke-w4.sh
set -u
BASE=http://localhost:8080/api/v1
ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT/.."
STAMP=$(date +%s)
DB_USER=$(grep '^DB_USERNAME=' realestate-server/.env | cut -d= -f2-)
DB_PASS=$(grep '^DB_PASSWORD=' realestate-server/.env | cut -d= -f2-)
MYSQL="mysql -h 127.0.0.1 -u $DB_USER -p$DB_PASS realestate -N -B"

PASS=0
FAIL=0
ok() { PASS=$((PASS + 1)); echo "  OK  $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  FAIL $1"; }
expect() {
  if [ "$1" = "$2" ]; then ok "$3（$1）"; else bad "$3：期望 $2，实际 $1"; fi
}
# 金额比较：后端 BigDecimal 序列化会省略末尾 0（10000.00 → 10000），按数值比较
expect_num() {
  local same
  same=$(node -e "console.log(Number('$1')===Number('$2'))")
  if [ "$same" = "true" ]; then ok "$3（$1）"; else bad "$3：期望 $2，实际 $1"; fi
}
jget() {
  node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{let o;try{o=JSON.parse(s)}catch(e){console.log('');return}const v='$1'.split('.').filter(Boolean).reduce((a,k)=>{if(a==null)return a;const m=k.match(/^([^\[]*)\[(\d+)\]$/);if(m){const b=m[1]?a[m[1]]:a;return b==null?b:b[Number(m[2])]}return a[k]},o);console.log(v==null?'':v)})"
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "== 0. 健康检查与登录 =="
curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"' && ok "后端健康" || { bad "后端未就绪"; exit 1; }
AT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123456","clientType":"WEB"}' | jget '.data.accessToken')
CT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"customer","password":"Customer@123456","clientType":"MINI"}' | jget '.data.accessToken')
CID=$(curl -s -H "Authorization: Bearer $CT" $BASE/auth/me | jget '.data.id')
[ -n "$AT" ] && [ -n "$CT" ] && ok "双角色登录，customerId=$CID" || { bad "登录失败"; exit 1; }

$MYSQL -e "INSERT INTO sys_user (username,password_hash,role,nickname,status,created_at,updated_at)
SELECT 'customer2', password_hash, 'CUSTOMER', '隔离测试客户', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_user WHERE username='customer'
ON DUPLICATE KEY UPDATE status='ACTIVE';" 2>/dev/null
C2T=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"customer2","password":"Customer@123456","clientType":"MINI"}' | jget '.data.accessToken')
[ -n "$C2T" ] && ok "customer2 登录成功（隔离测试）" || { bad "customer2 登录失败"; exit 1; }

echo "== 1. Flyway V4 与 customer_follow 结构 =="
FV=$($MYSQL -e "SELECT CONCAT(version,':',success) FROM flyway_schema_history WHERE version='4';" 2>/dev/null)
expect "$FV" "4:1" "Flyway V4 已成功应用"
TBL=$($MYSQL -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='realestate' AND table_name IN ('repair_ticket','repair_media','repair_status_log','guide_article','policy_document','policy_document_chunk','loan_rate_config','tax_rule_config');" 2>/dev/null)
expect "$TBL" "8" "V4 新建 8 张表"
COLS=$($MYSQL -e "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='realestate' AND table_name='customer_follow';" 2>/dev/null)
expect "$COLS" "id,owner_id,content,follow_type,next_follow_at,created_by,created_at" "customer_follow 列与 V2 一致"
IDX=$($MYSQL -e "SELECT GROUP_CONCAT(DISTINCT index_name ORDER BY index_name) FROM information_schema.statistics WHERE table_schema='realestate' AND table_name='customer_follow';" 2>/dev/null)
echo "$IDX" | grep -q "idx_customer_follow_owner_time" && echo "$IDX" | grep -q "idx_customer_follow_next" \
  && ok "customer_follow 索引保持 V2 定义" || bad "customer_follow 索引异常：$IDX"
if grep -qiE "CREATE[[:space:]]+TABLE[[:space:]]+\`?customer_follow|ALTER[[:space:]]+TABLE[[:space:]]+\`?customer_follow" realestate-server/src/main/resources/db/migration/V4__repair_content_calculator.sql; then
  bad "V4 迁移包含 customer_follow 的 CREATE/ALTER"
else
  ok "V4 迁移未创建/修改 customer_follow"
fi
SEED=$($MYSQL -e "SELECT COUNT(*) FROM guide_article WHERE title LIKE '%示例%';" 2>/dev/null)
[ "${SEED:-0}" -ge 3 ] && ok "内容示例种子标注示例（$SEED 条）" || bad "内容示例种子缺失"

echo "== 2. 内容：草稿→发布→下架可见性 =="
G=$(curl -s -X POST $BASE/guides -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"W4冒烟教程$STAMP\",\"category\":\"GUIDE\",\"summary\":\"冒烟摘要\",\"content\":\"第一行\\n第二行\\n第三行\",\"sort\":1}")
GID=$(echo "$G" | jget '.data.id')
expect "$(echo "$G" | jget '.data.status')" "DRAFT" "新建内容为草稿"
expect "$(code $BASE/guides/$GID)" "404" "草稿公开访问 404"
expect "$(code -H "Authorization: Bearer $AT" $BASE/guides/$GID)" "200" "ADMIN 可见草稿"
expect "$(curl -s -X POST $BASE/guides/$GID/publish -H "Authorization: Bearer $AT" | jget '.data.status')" "PUBLISHED" "发布内容"
expect "$(code $BASE/guides/$GID)" "200" "发布后公开可见"
expect "$(code -X PATCH $BASE/guides/$GID -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"title":"改名"}')" "409" "已发布内容直接编辑 409"
expect "$(curl -s -X POST $BASE/guides/$GID/offline -H "Authorization: Bearer $AT" | jget '.data.status')" "OFFLINE" "下架内容"
expect "$(code $BASE/guides/$GID)" "404" "下架后公开访问 404"
expect "$(code -X PATCH $BASE/guides/$GID -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"title":"下架后编辑"}')" "200" "下架后可编辑"
PUB_TOTAL=$(curl -s "$BASE/guides?page=1&size=5&category=GUIDE" | jget '.data.total')
[ "${PUB_TOTAL:-0}" -ge 1 ] && ok "公开教程列表可访问（total=$PUB_TOTAL）" || bad "公开教程列表为空"

echo "== 3. 政策：附件私有上传/鉴权下载 =="
P=$(curl -s -X POST $BASE/policies -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"W4冒烟政策$STAMP\",\"docNo\":\"示例-W4-$STAMP\",\"docType\":\"POLICY\",\"source\":\"示例数据（非实时政策）\",\"publishDate\":\"2026-11-01\",\"effectiveDate\":\"2026-11-01\",\"content\":\"示例政策正文\\n第二段\"}")
PID=$(echo "$P" | jget '.data.id')
expect "$(echo "$P" | jget '.data.status')" "DRAFT" "新建政策为草稿"
node -e "require('fs').writeFileSync('/tmp/w4-policy.pdf', Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.from('% smoke\n')]))"
ATT=$(curl -s -X POST $BASE/policies/$PID/attachment -H "Authorization: Bearer $AT" -F "file=@/tmp/w4-policy.pdf;type=application/pdf")
expect "$(echo "$ATT" | jget '.data.hasAttachment')" "true" "政策附件上传（私有存储）"
PKEY=$($MYSQL -e "SELECT attachment_key FROM policy_document WHERE id=$PID;" 2>/dev/null)
case "$PKEY" in /uploads/*) bad "政策附件落入公开目录：$PKEY";; *) ok "政策附件 key 为私有相对路径";; esac
expect "$(code $BASE/policies/$PID/attachment)" "401" "游客下载政策附件 401"
curl -s -X POST $BASE/policies/$PID/publish -H "Authorization: Bearer $AT" > /dev/null
DL=$(curl -s -D /tmp/w4-att.hdr -o /tmp/w4-att.pdf -w "%{http_code}" -H "Authorization: Bearer $CT" $BASE/policies/$PID/attachment)
expect "$DL" "200" "客户下载已发布政策附件"
grep -qi 'content-disposition: attachment' /tmp/w4-att.hdr && ok "附件按 attachment 下载" || bad "附件响应头不正确"
expect "$(head -c 4 /tmp/w4-att.pdf)" "%PDF" "附件 PDF 文件头正确"

echo "== 4. 报修：全链路与私有图片 =="
OID=$(curl -s -X POST $BASE/owners -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"name\":\"W4冒烟房东$STAMP\",\"phone\":\"13800138007\",\"type\":\"LANDLORD\"}" | jget '.data.id')
HID=$(curl -s -X POST $BASE/houses -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"W4冒烟报修房源$STAMP\",\"houseType\":\"RENT\",\"community\":\"报修苑\",\"region\":\"浦东新区\",\"address\":\"上海市浦东新区报修路 30 号 1201 室\",\"price\":5200.00,\"area\":90.00,\"rooms\":2,\"bathrooms\":1,\"ownerId\":$OID}" | jget '.data.id')
curl -s -X POST $BASE/houses/$HID/publish -H "Authorization: Bearer $AT" > /dev/null
[ -n "$HID" ] && ok "报修房源准备完成 houseId=$HID" || { bad "房源准备失败"; exit 1; }

expect "$(code -X POST $BASE/repairs -H 'Content-Type: application/json' -d "{\"houseId\":$HID,\"category\":\"PLUMBING\",\"urgency\":\"NORMAL\",\"title\":\"游客提交\",\"description\":\"游客不应该能够提交报修工单\",\"contactPhone\":\"13912345678\"}")" "401" "游客提交报修 401"
R=$(curl -s -X POST $BASE/repairs -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$HID,\"category\":\"PLUMBING\",\"urgency\":\"URGENT\",\"title\":\"W4冒烟水管漏水$STAMP\",\"description\":\"水槽下方水管持续渗水，地面已经积水，希望尽快上门处理\",\"contactPhone\":\"13912345678\",\"preferredVisitTime\":\"$(date -d '+1 day' +%Y-%m-%dT10:00:00)Z\"}")
RID=$(echo "$R" | jget '.data.id')
expect "$(echo "$R" | jget '.data.status')" "SUBMITTED" "客户提交报修"
expect "$(echo "$R" | jget '.data.contactPhone')" "139******78" "客户视角电话脱敏"
echo "$R" | jget '.data.description' | grep -q '水槽下方' && ok "详情返回问题描述" || bad "详情描述缺失"
expect "$(code -H "Authorization: Bearer $AT" $BASE/repairs/$RID)" "200" "ADMIN 可读报修"
expect "$(curl -s -H "Authorization: Bearer $AT" $BASE/repairs/$RID | jget '.data.contactPhone')" "13912345678" "ADMIN 视角电话明文"
expect "$(code -H "Authorization: Bearer $C2T" $BASE/repairs/$RID)" "404" "customer2 读取他人报修 404"

node -e "require('fs').writeFileSync('/tmp/w4-repair.jpg', Buffer.from('/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==','base64'))"
M=$(curl -s -X POST $BASE/repairs/$RID/media -H "Authorization: Bearer $CT" -F "file=@/tmp/w4-repair.jpg;type=image/jpeg")
MID=$(echo "$M" | jget '.data.id')
[ -n "$MID" ] && ok "报修图片上传成功 mediaId=$MID" || bad "报修图片上传失败"
MKEY=$($MYSQL -e "SELECT url FROM repair_media WHERE id=$MID;" 2>/dev/null)
case "$MKEY" in repair/*) ok "图片保存为私有相对 key";; *) bad "图片 key 异常：$MKEY";; esac
expect "$(code http://localhost:8080/uploads/$MKEY)" "404" "图片不能经 /uploads 公开访问"
expect "$(code $BASE/repairs/$RID/media/$MID)" "401" "游客读取报修图片 401"
expect "$(code -H "Authorization: Bearer $C2T" $BASE/repairs/$RID/media/$MID)" "404" "customer2 读取他人图片 404"
MCODE=$(curl -s -D /tmp/w4-media.hdr -o /tmp/w4-media.jpg -w "%{http_code}" -H "Authorization: Bearer $CT" $BASE/repairs/$RID/media/$MID)
expect "$MCODE" "200" "客户读取本人图片"
grep -qi 'content-type: image/jpeg' /tmp/w4-media.hdr && ok "图片 Content-Type image/jpeg" || bad "图片 Content-Type 错误"
grep -qi 'cache-control: private, no-store' /tmp/w4-media.hdr && ok "图片 Cache-Control private,no-store" || bad "图片缓存头错误"
for i in 2 3 4 5 6; do curl -s -o /dev/null -X POST $BASE/repairs/$RID/media -H "Authorization: Bearer $CT" -F "file=@/tmp/w4-repair.jpg;type=image/jpeg"; done
expect "$(code -X POST $BASE/repairs/$RID/media -H "Authorization: Bearer $CT" -F "file=@/tmp/w4-repair.jpg;type=image/jpeg")" "400" "第 7 张图片被拒绝"

expect "$(code -X POST $BASE/repairs/$RID/accept -H "Authorization: Bearer $CT")" "403" "CUSTOMER 受理工单 403"
expect "$(curl -s -X POST $BASE/repairs/$RID/accept -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"reason":"已安排师傅"}' | jget '.data.status')" "ACCEPTED" "ADMIN 受理"
expect "$(code -X POST $BASE/repairs/$RID/accept -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{}')" "409" "重复受理 409"
expect "$(curl -s -X POST $BASE/repairs/$RID/start -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{}' | jget '.data.status')" "PROCESSING" "开始处理"
expect "$(curl -s -X POST $BASE/repairs/$RID/resolve -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{}' | jget '.data.status')" "RESOLVED" "处理完成"
expect "$(curl -s -X POST $BASE/repairs/$RID/close -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{}' | jget '.data.status')" "CLOSED" "客户确认关闭"
expect "$(code -X POST $BASE/repairs/$RID/close -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{}')" "409" "重复关闭 409"
expect "$(curl -s -X POST $BASE/repairs/$RID/reopen -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{}' | jget '.data.status')" "REOPENED" "客户重新打开"
expect "$(curl -s -X POST $BASE/repairs/$RID/start -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{}' | jget '.data.status')" "PROCESSING" "重开后再次处理"
curl -s -X POST $BASE/repairs/$RID/resolve -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{}' > /dev/null
curl -s -X POST $BASE/repairs/$RID/close -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{}' > /dev/null
LOG_COUNT=$($MYSQL -e "SELECT COUNT(*) FROM repair_status_log WHERE ticket_id=$RID;" 2>/dev/null)
[ "${LOG_COUNT:-0}" -ge 8 ] && ok "状态时间线日志完整（$LOG_COUNT 条）" || bad "状态日志不足：$LOG_COUNT"

# 并发受理
R2=$(curl -s -X POST $BASE/repairs -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$HID,\"category\":\"OTHER\",\"urgency\":\"NORMAL\",\"title\":\"W4冒烟并发受理$STAMP\",\"description\":\"用于验证并发受理只允许一次成功，另一次返回冲突\",\"contactPhone\":\"13912345678\"}")
RID2=$(echo "$R2" | jget '.data.id')
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/repairs/$RID2/accept -H "Authorization: Bearer $AT" > /tmp/w4-a1.code &
P1=$!
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/repairs/$RID2/accept -H "Authorization: Bearer $AT" > /tmp/w4-a2.code &
P2=$!
wait $P1 $P2
A1=$(cat /tmp/w4-a1.code); A2=$(cat /tmp/w4-a2.code)
W=0
[ "$A1" = "200" ] && W=$((W + 1))
[ "$A2" = "200" ] && W=$((W + 1))
expect "$W" "1" "并行受理仅 1 次成功（$A1 / $A2）"

# 撤销
R3=$(curl -s -X POST $BASE/repairs -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$HID,\"category\":\"OTHER\",\"urgency\":\"NORMAL\",\"title\":\"W4冒烟撤销$STAMP\",\"description\":\"用于验证客户撤销工单与重复撤销冲突，请勿处理\",\"contactPhone\":\"13912345678\"}")
RID3=$(echo "$R3" | jget '.data.id')
expect "$(curl -s -X POST $BASE/repairs/$RID3/cancel -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{}' | jget '.data.status')" "CANCELLED" "客户撤销报修"
expect "$(code -X POST $BASE/repairs/$RID3/cancel -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{}')" "409" "重复撤销 409"

echo "== 5. 客源跟进（复用 V2 表） =="
F=$(curl -s -X POST $BASE/owners/$OID/follows -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d '{"content":"W4冒烟跟进：客户希望周末看房","followType":"PHONE","nextFollowAt":"2026-12-01T10:00:00Z"}')
expect "$(echo "$F" | jget '.data.followTypeLabel')" "电话" "新增跟进并返回方式标签"
FTOTAL=$(curl -s -H "Authorization: Bearer $AT" "$BASE/owners/$OID/follows?page=1&size=5" | jget '.data.total')
[ "${FTOTAL:-0}" -ge 1 ] && ok "跟进分页列表可访问（total=$FTOTAL）" || bad "跟进列表为空"
expect "$(code -H "Authorization: Bearer $CT" $BASE/owners/$OID/follows)" "403" "CUSTOMER 访问跟进 403"

echo "== 6. 测算：样例、边界、税费分档与限流 =="
CFG=$(curl -s $BASE/calculators/config)
expect "$(echo "$CFG" | jget '.data.rates[0].loanType')" "COMMERCIAL" "测算配置返回贷款费率"
echo "$CFG" | jget '.data.rates[0].source' | grep -q '示例' && ok "费率来源标注示例/非实时" || bad "费率来源缺少示例标注"
echo "$CFG" | jget '.data.taxRules[0].source' | grep -q '示例' && ok "税费规则来源标注示例/非实时" || bad "税费规则来源缺少示例标注"

M1=$(curl -s -X POST $BASE/calculators/mortgage -H 'Content-Type: application/json' \
  -d '{"loanType":"COMMERCIAL","repaymentMethod":"EQUAL_INSTALLMENT","totalPrice":1000000,"downPayment":300000,"termYears":30}')
expect "$(echo "$M1" | jget '.data.firstMonthPayment')" "3281.66" "等额本息已知样例首月"
expect "$(echo "$M1" | jget '.data.months')" "360" "30 年 = 360 期"
expect_num "$(echo "$M1" | jget '.data.yearlySummary[29].endingBalance')" "0.00" "末期本金归零"
M2=$(curl -s -X POST $BASE/calculators/mortgage -H 'Content-Type: application/json' \
  -d '{"loanType":"FUND","repaymentMethod":"EQUAL_INSTALLMENT","totalPrice":120000,"downPayment":0,"termYears":1,"annualRate":0}')
expect_num "$(echo "$M2" | jget '.data.firstMonthPayment')" "10000.00" "零利率月供=本金/期数"
expect_num "$(echo "$M2" | jget '.data.totalInterest')" "0.00" "零利率总利息为 0"
M3=$(curl -s -X POST $BASE/calculators/mortgage -H 'Content-Type: application/json' \
  -d '{"loanType":"COMMERCIAL","repaymentMethod":"EQUAL_PRINCIPAL","totalPrice":120000,"downPayment":0,"termYears":1,"annualRate":0.12}')
expect_num "$(echo "$M3" | jget '.data.firstMonthPayment')" "11200.00" "等额本金首月"
expect_num "$(echo "$M3" | jget '.data.lastMonthPayment')" "10100.00" "等额本金末月"
expect_num "$(echo "$M3" | jget '.data.totalInterest')" "7800.00" "等额本金总利息"
expect "$(code -X POST $BASE/calculators/mortgage -H 'Content-Type: application/json' -d '{"loanType":"COMMERCIAL","repaymentMethod":"EQUAL_INSTALLMENT","totalPrice":100000,"downPayment":100001,"termYears":10}')" "400" "首付大于总价 400"

T1=$(curl -s -X POST $BASE/calculators/tax -H 'Content-Type: application/json' -d '{"city":"上海","price":500000}')
expect_num "$(echo "$T1" | jget '.data.items[0].amount')" "5000.00" "契税 1% 档"
expect_num "$(echo "$T1" | jget '.data.totalTax')" "10000.00" "50 万税费合计（契税+个税）"
T2=$(curl -s -X POST $BASE/calculators/tax -H 'Content-Type: application/json' -d '{"city":"上海","price":1000000}')
expect_num "$(echo "$T2" | jget '.data.items[0].amount')" "15000.00" "契税 1.5% 档"
expect_num "$(echo "$T2" | jget '.data.totalTax')" "25000.00" "100 万税费合计"
T3=$(curl -s -X POST $BASE/calculators/tax -H 'Content-Type: application/json' -d '{"city":"上海","price":6000000}')
expect_num "$(echo "$T3" | jget '.data.items[0].amount')" "180000.00" "契税 3% 档"
expect_num "$(echo "$T3" | jget '.data.totalTax')" "600000.00" "600 万税费合计"
expect "$(code -X POST $BASE/calculators/tax -H 'Content-Type: application/json' -d '{"city":"不存在市","price":1000000}')" "400" "无匹配税费规则 400"

RL_IP="203.0.113.$((RANDOM % 200 + 20))"
LAST=0
for i in $(seq 1 65); do
  LAST=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/calculators/tax -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: $RL_IP" -d '{"city":"上海","price":500000}')
done
expect "$LAST" "429" "公开测算第 61 次起限流 429（ip=$RL_IP）"

echo "== 7. 合同到期任务（幂等 + 房源释放） =="
H2=$(curl -s -X POST $BASE/houses -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"W4冒烟到期房源$STAMP\",\"houseType\":\"RENT\",\"community\":\"到期苑\",\"region\":\"浦东新区\",\"address\":\"上海市浦东新区到期路 8 号\",\"price\":4800.00,\"area\":70.00,\"rooms\":1,\"bathrooms\":1,\"ownerId\":$OID}" | jget '.data.id')
curl -s -X POST $BASE/houses/$H2/publish -H "Authorization: Bearer $AT" > /dev/null
K=$(curl -s -X POST $BASE/contracts -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$H2,\"customerId\":$CID,\"title\":\"W4冒烟到期合同$STAMP\",\"customerPhone\":\"13912345678\",\"amount\":4800.00,\"deposit\":9600.00,\"paymentCycle\":\"MONTHLY\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-02-01\"}" | jget '.data.id')
curl -s -X POST $BASE/contracts/$K/submit -H "Authorization: Bearer $AT" > /dev/null
expect "$(curl -s -X POST $BASE/contracts/$K/confirm -H "Authorization: Bearer $CT" | jget '.data.status')" "ACTIVE" "到期测试合同生效"
expect "$(curl -s -H "Authorization: Bearer $AT" $BASE/houses/$H2 | jget '.data.status')" "CONTRACTED" "生效后房源 CONTRACTED"
E1=$(curl -s -X POST $BASE/admin/maintenance/expire-contracts -H "Authorization: Bearer $AT")
EXPIRED=$(echo "$E1" | jget '.data.expiredCount')
[ "${EXPIRED:-0}" -ge 1 ] && ok "到期任务处理数量 $EXPIRED" || bad "到期任务未处理合同"
expect "$(curl -s -H "Authorization: Bearer $AT" $BASE/contracts/$K | jget '.data.status')" "EXPIRED" "合同置 EXPIRED"
expect "$(curl -s -H "Authorization: Bearer $AT" $BASE/houses/$H2 | jget '.data.status')" "PUBLISHED" "房源释放为 PUBLISHED"
NODE_STATUS=$($MYSQL -e "SELECT CONCAT(SUM(status='CANCELLED'),'/',COUNT(*)) FROM transaction_process WHERE contract_id=$K;" 2>/dev/null)
expect "$NODE_STATUS" "2/3" "未完成交易节点取消（DONE 保留）"
E2=$(curl -s -X POST $BASE/admin/maintenance/expire-contracts -H "Authorization: Bearer $AT")
expect "$(echo "$E2" | jget '.data.expiredCount')" "0" "到期任务重复执行幂等"
ELOG=$($MYSQL -e "SELECT COUNT(*) FROM contract_status_log WHERE contract_id=$K AND to_status='EXPIRED' AND operator_id IS NULL;" 2>/dev/null)
expect "$ELOG" "1" "到期状态日志仅写一次"
expect "$(code -X POST $BASE/admin/maintenance/expire-contracts -H "Authorization: Bearer $CT")" "403" "CUSTOMER 触发到期任务 403"

echo "== 8. EXPLAIN：报修/内容列表命中索引 =="
$MYSQL -e "DELETE FROM repair_ticket WHERE ticket_no LIKE 'EXPLAIN%'; DELETE FROM guide_article WHERE title LIKE 'EXPLAIN%';" 2>/dev/null
$MYSQL -e "INSERT INTO repair_ticket (ticket_no, house_id, contract_id, customer_id, category, urgency, title, description, contact_phone, status, version, deleted, created_at, updated_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<2000)
SELECT CONCAT('EXPLAIN', n), $HID, NULL, $CID, 'PLUMBING', 'NORMAL', CONCAT('压测报修', n), '压测描述文本', '13900000000', 'SUBMITTED', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM seq;" 2>/dev/null
$MYSQL -e "INSERT INTO guide_article (title, category, summary, content, status, sort, version, deleted, created_at, updated_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<2000)
SELECT CONCAT('EXPLAIN', n), 'GUIDE', '压测摘要', '压测正文', 'PUBLISHED', n, 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM seq;" 2>/dev/null
RP=$($MYSQL -e "EXPLAIN SELECT id FROM repair_ticket WHERE deleted=0 AND customer_id=$CID AND status='SUBMITTED' ORDER BY created_at DESC LIMIT 10;" 2>/dev/null)
GP=$($MYSQL -e "EXPLAIN SELECT id FROM guide_article WHERE deleted=0 AND status='PUBLISHED' AND category='GUIDE' ORDER BY sort ASC, id DESC LIMIT 10;" 2>/dev/null)
echo "$RP"
echo "$GP"
echo "$RP" | grep -q "idx_repair_customer_status_created" && ok "报修列表命中 idx_repair_customer_status_created" || bad "报修列表未命中预期索引"
echo "$GP" | grep -q "idx_guide_status_category_sort" && ok "内容列表命中 idx_guide_status_category_sort" || bad "内容列表未命中预期索引"
echo "$RP" | grep -qw "ALL" && bad "报修列表全表扫描" || ok "报修列表非全表扫描"
echo "$GP" | grep -qw "ALL" && bad "内容列表全表扫描" || ok "内容列表非全表扫描"
$MYSQL -e "DELETE FROM repair_ticket WHERE ticket_no LIKE 'EXPLAIN%'; DELETE FROM guide_article WHERE title LIKE 'EXPLAIN%';" 2>/dev/null
ok "压测数据已清理"

echo "== 9. 清理本轮冒烟数据 =="
$MYSQL -e "DELETE l FROM repair_status_log l JOIN repair_ticket t ON t.id=l.ticket_id WHERE t.title LIKE 'W4冒烟%';
DELETE m FROM repair_media m JOIN repair_ticket t ON t.id=m.ticket_id WHERE t.title LIKE 'W4冒烟%';
DELETE FROM repair_ticket WHERE title LIKE 'W4冒烟%';
DELETE FROM guide_article WHERE title LIKE 'W4冒烟%';
DELETE FROM policy_document WHERE title LIKE 'W4冒烟%';
DELETE FROM customer_follow WHERE content LIKE 'W4冒烟%';
DELETE FROM contract_status_log WHERE contract_id IN (SELECT id FROM contract WHERE title LIKE 'W4冒烟%');
DELETE FROM transaction_status_log WHERE contract_id IN (SELECT id FROM contract WHERE title LIKE 'W4冒烟%');
DELETE FROM transaction_process WHERE contract_id IN (SELECT id FROM contract WHERE title LIKE 'W4冒烟%');
DELETE FROM contract_party WHERE contract_id IN (SELECT id FROM contract WHERE title LIKE 'W4冒烟%');
DELETE FROM contract WHERE title LIKE 'W4冒烟%';
DELETE FROM house WHERE title LIKE 'W4冒烟%';" 2>/dev/null
ok "冒烟数据已清理（媒体文件保留在 uploads-private，可按需清理）"

rm -f /tmp/w4-policy.pdf /tmp/w4-att.pdf /tmp/w4-att.hdr /tmp/w4-repair.jpg /tmp/w4-media.jpg /tmp/w4-media.hdr \
      /tmp/w4-a1.code /tmp/w4-a2.code

echo "======================================"
echo "W4 冒烟结果：通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" -eq 0 ] || exit 1
