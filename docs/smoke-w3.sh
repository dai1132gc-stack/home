#!/usr/bin/env bash
# W3 预约 / 合同 / 交易 / PDF 端到端冒烟（需后端 8080、Redis、MySQL 可用）
# 用法：bash docs/smoke-w3.sh
set -u
BASE=http://localhost:8080/api/v1
ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT/.."
STAMP=$(date +%s)
DB_USER=$(grep '^DB_USERNAME=' realestate-server/.env | cut -d= -f2-)
DB_PASS=$(grep '^DB_PASSWORD=' realestate-server/.env | cut -d= -f2-)
MYSQL="mysql -h 127.0.0.1 -u $DB_USER -p$DB_PASS realestate -N -B"
FUTURE=$(date -d "+2 days" +%Y-%m-%dT10:00:00)

PASS=0
FAIL=0
ok() { PASS=$((PASS + 1)); echo "  OK  $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  FAIL $1"; }
expect() { # actual expected label
  if [ "$1" = "$2" ]; then ok "$3（$1）"; else bad "$3：期望 $2，实际 $1"; fi
}

# 从 JSON 中取值：jget '.code' / '.data.id' / '.data.items[0].status'
jget() {
  node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{let o;try{o=JSON.parse(s)}catch(e){console.log('');return}const v='$1'.split('.').filter(Boolean).reduce((a,k)=>{if(a==null)return a;const m=k.match(/^([^\[]*)\[(\d+)\]$/);if(m){const b=m[1]?a[m[1]]:a;return b==null?b:b[Number(m[2])]}return a[k]},o);console.log(v==null?'':v)})"
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "== 0. 健康检查 =="
curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"' && ok "后端健康" || bad "后端未就绪"

echo "== 1. 登录（ADMIN / CUSTOMER）=="
AT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123456","clientType":"WEB"}' | jget '.data.accessToken')
CT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"customer","password":"Customer@123456","clientType":"MINI"}' | jget '.data.accessToken')
CID=$(curl -s -H "Authorization: Bearer $CT" $BASE/auth/me | jget '.data.id')
[ -n "$AT" ] && [ -n "$CT" ] && ok "双角色登录，customerId=$CID" || { bad "登录失败"; exit 1; }

# 第二个客户用于数据隔离验证（复用 customer 密码哈希，仅本地 dev）
echo "== 2. 创建隔离测试客户 customer2 =="
$MYSQL -e "INSERT INTO sys_user (username,password_hash,role,nickname,status,created_at,updated_at)
SELECT 'customer2', password_hash, 'CUSTOMER', '隔离测试客户', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_user WHERE username='customer'
ON DUPLICATE KEY UPDATE status='ACTIVE';" 2>/dev/null
C2T=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"customer2","password":"Customer@123456","clientType":"MINI"}' | jget '.data.accessToken')
C2ID=$(curl -s -H "Authorization: Bearer $C2T" $BASE/auth/me | jget '.data.id')
[ -n "$C2T" ] && ok "customer2 登录成功，id=$C2ID" || { bad "customer2 登录失败"; exit 1; }

echo "== 3. 准备房源（ADMIN）=="
OID=$(curl -s -X POST $BASE/owners -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"name\":\"W3冒烟房东$STAMP\",\"phone\":\"13800138003\",\"type\":\"LANDLORD\"}" | jget '.data.id')
HID=$(curl -s -X POST $BASE/houses -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"W3冒烟房源$STAMP\",\"houseType\":\"RENT\",\"community\":\"冒烟花园\",\"region\":\"浦东新区\",\"address\":\"上海市浦东新区冒烟路 88 弄 6 号 1502 室（近地铁 9 号线）\",\"price\":6200.00,\"area\":95.50,\"rooms\":2,\"bathrooms\":1,\"floor\":\"15/26\",\"description\":\"W3 冒烟\",\"ownerId\":$OID}" | jget '.data.id')
P1=$(code -X POST $BASE/houses/$HID/publish -H "Authorization: Bearer $AT")
expect "$P1" "200" "房源创建并发布"
[ -n "$HID" ] && [ -n "$OID" ] || { bad "房源准备失败"; exit 1; }

echo "== 4. 预约：权限与创建 =="
expect "$(code -X POST $BASE/appointments -H 'Content-Type: application/json' -d "{\"houseId\":$HID,\"preferredTime\":\"$FUTURE\",\"contactPhone\":\"13912345678\"}")" "401" "游客提交预约 401"
APP=$(curl -s -X POST $BASE/appointments -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$HID,\"preferredTime\":\"$FUTURE\",\"contactPhone\":\"13912345678\",\"remark\":\"希望看采光\"}")
AID=$(echo "$APP" | jget '.data.id')
expect "$(echo "$APP" | jget '.data.status')" "PENDING" "客户创建预约"
expect "$(echo "$APP" | jget '.data.contactPhone')" "139******78" "客户视角手机号脱敏"
ADMIN_APP=$(curl -s -H "Authorization: Bearer $AT" $BASE/appointments/$AID)
expect "$(echo "$ADMIN_APP" | jget '.data.contactPhone')" "13912345678" "ADMIN 视角手机号明文"
expect "$(code -H "Authorization: Bearer $C2T" $BASE/appointments/$AID)" "404" "customer2 读取他人预约 404"
expect "$(code -X POST $BASE/appointments/$AID/confirm -H "Authorization: Bearer $CT")" "403" "CUSTOMER 确认预约 403"

echo "== 5. 预约确认与房源联动 =="
expect "$(curl -s -X POST $BASE/appointments/$AID/confirm -H "Authorization: Bearer $AT" | jget '.data.status')" "CONFIRMED" "ADMIN 确认预约"
expect "$(curl -s -H "Authorization: Bearer $AT" $BASE/houses/$HID | jget '.data.status')" "RESERVED" "房源置 RESERVED"
expect "$(code $BASE/houses/$HID)" "404" "RESERVED 房源游客不可见 404"
expect "$(curl -s -o /dev/null -w '%{http_code}' -X POST $BASE/appointments/$AID/complete -H "Authorization: Bearer $AT")" "200" "带看完成"
expect "$(curl -s -H "Authorization: Bearer $AT" $BASE/houses/$HID | jget '.data.status')" "PUBLISHED" "完成后房源释放为 PUBLISHED"

echo "== 6. 合同：起草 / 快照 / PDF =="
CON=$(curl -s -X POST $BASE/contracts -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$HID,\"customerId\":$CID,\"title\":\"冒烟花园 1502 室租赁合同\",\"amount\":6200.00,\"deposit\":12400.00,\"paymentCycle\":\"MONTHLY\",\"startDate\":\"$(date -d '+7 days' +%Y-%m-%d)\",\"endDate\":\"$(date -d '+1 year' +%Y-%m-%d)\",\"lessorIdCard\":\"310101199001011234\",\"customerIdCard\":\"310105199203045678\"}")
KID=$(echo "$CON" | jget '.data.id')
expect "$(echo "$CON" | jget '.data.status')" "DRAFT" "起草合同为草稿"
expect "$(echo "$CON" | jget '.data.pdfAvailable')" "true" "草稿阶段已生成 PDF"
expect "$(echo "$CON" | jget '.data.amountInWords')" "陆仟贰佰元整" "金额大写正确"
expect "$(echo "$CON" | jget '.data.parties[0].idCardMasked')" "310101********1234" "证件号脱敏入库"
expect "$(code -H "Authorization: Bearer $C2T" $BASE/contracts/$KID)" "404" "customer2 读取他人合同 404"
expect "$(code -H "Authorization: Bearer $C2T" $BASE/contracts/$KID/pdf)" "404" "customer2 下载他人 PDF 404"
expect "$(code $BASE/contracts/$KID/pdf)" "401" "游客下载 PDF 401"
expect "$(code -X POST $BASE/contracts/$KID/confirm -H "Authorization: Bearer $CT")" "409" "草稿直接确认 409"

echo "== 7. 提交待签与唯一占用 =="
expect "$(curl -s -X POST $BASE/contracts/$KID/submit -H "Authorization: Bearer $AT" | jget '.data.status')" "PENDING_SIGN" "提交待签"
expect "$(code -X POST $BASE/contracts -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d "{\"houseId\":$HID,\"customerId\":$CID,\"title\":\"重复合同\",\"amount\":6200,\"deposit\":0,\"paymentCycle\":\"MONTHLY\",\"startDate\":\"$(date -d '+7 days' +%Y-%m-%d)\",\"endDate\":\"$(date -d '+1 year' +%Y-%m-%d)\"}")" "409" "同一房源第二份占用合同 409"

echo "== 8. 客户确认生效与房源联动 =="
ACT=$(curl -s -X POST $BASE/contracts/$KID/confirm -H "Authorization: Bearer $CT")
expect "$(echo "$ACT" | jget '.data.status')" "ACTIVE" "客户确认合同生效"
expect "$(echo "$ACT" | jget '.data.signedAt' | cut -c1-4)" "$(date +%Y)" "写入签署时间"
expect "$(curl -s -H "Authorization: Bearer $AT" $BASE/houses/$HID | jget '.data.status')" "CONTRACTED" "房源置 CONTRACTED"
expect "$(code -X POST $BASE/contracts/$KID/confirm -H "Authorization: Bearer $CT")" "409" "重复确认 409"
expect "$(curl -s -H "Authorization: Bearer $CT" $BASE/contracts/$KID/transactions | jget '.data[1].status')" "DONE" "SIGN 节点自动完成"

echo "== 9. 快照不可变（修改房源不影响历史合同）=="
curl -s -X PATCH $BASE/houses/$HID -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"房源已改名\",\"price\":9999.00}" > /dev/null
SNAP_TITLE=$(curl -s -H "Authorization: Bearer $AT" $BASE/contracts/$KID | jget '.data.houseTitle')
expect "$SNAP_TITLE" "W3冒烟房源$STAMP" "合同保留创建时房源标题快照"

echo "== 10. 交易节点 =="
expect "$(curl -s -X POST $BASE/contracts/$KID/transactions/DEPOSIT/done -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"remark":"定金已收"}' | jget '.data.status')" "DONE" "完成 DEPOSIT 节点"
expect "$(code -X POST $BASE/contracts/$KID/transactions/DEPOSIT/done -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{}')" "409" "重复完成节点 409"
expect "$(curl -s -X POST $BASE/contracts/$KID/transactions/HANDOVER/done -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{}' | jget '.data.status')" "DONE" "完成 HANDOVER 节点"
expect "$(code -X POST $BASE/contracts/$KID/transactions/SIGN/done -H 'Content-Type: application/json' -d '{}')" "401" "游客完成节点 401"

echo "== 11. PDF 鉴权下载 =="
PDF_CODE=$(curl -s -D "$ROOT/w3-pdf.hdr" -o "$ROOT/w3-contract.pdf" -w "%{http_code}" -H "Authorization: Bearer $CT" $BASE/contracts/$KID/pdf)
expect "$PDF_CODE" "200" "客户下载本人合同 PDF"
grep -qi 'content-type: application/pdf' "$ROOT/w3-pdf.hdr" && ok "Content-Type application/pdf" || bad "PDF Content-Type 不正确"
grep -qi 'content-disposition: attachment' "$ROOT/w3-pdf.hdr" && ok "Content-Disposition attachment" || bad "PDF 非下载响应"
HEAD4=$(head -c 4 "$ROOT/w3-contract.pdf")
expect "$HEAD4" "%PDF" "PDF 文件头正确"
PDF_SIZE=$(wc -c < "$ROOT/w3-contract.pdf")
[ "$PDF_SIZE" -gt 20000 ] && ok "PDF 大小 ${PDF_SIZE}B" || bad "PDF 过小 ${PDF_SIZE}B"
expect "$(code -H "Authorization: Bearer $AT" $BASE/contracts/$KID/pdf)" "200" "ADMIN 下载 PDF"

echo "== 12. 终止合同与房源释放 =="
expect "$(curl -s -X POST $BASE/contracts/$KID/terminate -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"reason":"租客工作调动，协商解除"}' | jget '.data.status')" "TERMINATED" "终止合同"
expect "$(curl -s -H "Authorization: Bearer $AT" $BASE/houses/$HID | jget '.data.status')" "PUBLISHED" "终止后房源回到 PUBLISHED"
expect "$(code -X POST $BASE/contracts/$KID/terminate -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"reason":"重复终止"}')" "409" "终态合同再次终止 409"
MY_TOTAL=$(curl -s -H "Authorization: Bearer $CT" "$BASE/me/contracts?page=1&size=5" | jget '.data.total')
[ "${MY_TOTAL:-0}" -ge 1 ] && ok "我的合同可访问（total=$MY_TOTAL）" || bad "我的合同列表为空"

echo "== 13. 并发激活（同一合同两次并行确认）=="
H3=$(curl -s -X POST $BASE/houses -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"W3并发房源$STAMP\",\"houseType\":\"RENT\",\"community\":\"并发苑\",\"region\":\"浦东新区\",\"address\":\"上海市浦东新区并发路 1 号\",\"price\":5000.00,\"area\":80.00,\"rooms\":2,\"bathrooms\":1,\"ownerId\":$OID}" | jget '.data.id')
curl -s -X POST $BASE/houses/$H3/publish -H "Authorization: Bearer $AT" > /dev/null
K3=$(curl -s -X POST $BASE/contracts -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$H3,\"customerId\":$CID,\"title\":\"并发激活合同$STAMP\",\"customerPhone\":\"13912345678\",\"amount\":5000.00,\"deposit\":10000.00,\"paymentCycle\":\"MONTHLY\",\"startDate\":\"$(date -d '+7 days' +%Y-%m-%d)\",\"endDate\":\"$(date -d '+1 year' +%Y-%m-%d)\"}" | jget '.data.id')
curl -s -X POST $BASE/contracts/$K3/submit -H "Authorization: Bearer $AT" > /dev/null
curl -s -o "$ROOT/w3-c1.json" -w "%{http_code}" -X POST $BASE/contracts/$K3/confirm -H "Authorization: Bearer $CT" > "$ROOT/w3-c1.code" &
PIDA=$!
curl -s -o "$ROOT/w3-c2.json" -w "%{http_code}" -X POST $BASE/contracts/$K3/confirm -H "Authorization: Bearer $CT" > "$ROOT/w3-c2.code" &
PIDB=$!
wait $PIDA $PIDB
CODE1=$(cat "$ROOT/w3-c1.code")
CODE2=$(cat "$ROOT/w3-c2.code")
SUCC=0
[ "$CODE1" = "200" ] && SUCC=$((SUCC + 1))
[ "$CODE2" = "200" ] && SUCC=$((SUCC + 1))
expect "$SUCC" "1" "并行确认仅 1 次成功（$CODE1 / $CODE2）"
[ "$CODE1" = "409" ] || [ "$CODE2" = "409" ] && ok "另一次返回 409" || bad "失败分支未返回 409"
curl -s -X POST $BASE/contracts/$K3/terminate -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"reason":"并发测试清理"}' > /dev/null

echo "== 14. 并发占用（两份草稿同时提交，唯一键兜底）=="
H4=$(curl -s -X POST $BASE/houses -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"W3并发房源B$STAMP\",\"houseType\":\"RENT\",\"community\":\"并发苑\",\"region\":\"浦东新区\",\"address\":\"上海市浦东新区并发路 2 号\",\"price\":5100.00,\"area\":81.00,\"rooms\":2,\"bathrooms\":1,\"ownerId\":$OID}" | jget '.data.id')
curl -s -X POST $BASE/houses/$H4/publish -H "Authorization: Bearer $AT" > /dev/null
KB1=$(curl -s -X POST $BASE/contracts -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$H4,\"customerId\":$CID,\"title\":\"并发合同A$STAMP\",\"customerPhone\":\"13912345678\",\"amount\":5100.00,\"deposit\":10200.00,\"paymentCycle\":\"MONTHLY\",\"startDate\":\"$(date -d '+7 days' +%Y-%m-%d)\",\"endDate\":\"$(date -d '+1 year' +%Y-%m-%d)\"}" | jget '.data.id')
KB2=$(curl -s -X POST $BASE/contracts -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"houseId\":$H4,\"customerId\":$CID,\"title\":\"并发合同B$STAMP\",\"customerPhone\":\"13912345678\",\"amount\":5200.00,\"deposit\":10400.00,\"paymentCycle\":\"MONTHLY\",\"startDate\":\"$(date -d '+7 days' +%Y-%m-%d)\",\"endDate\":\"$(date -d '+1 year' +%Y-%m-%d)\"}" | jget '.data.id')
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/contracts/$KB1/submit -H "Authorization: Bearer $AT" > "$ROOT/w3-s1.code" &
PIDS=$!
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/contracts/$KB2/submit -H "Authorization: Bearer $AT" > "$ROOT/w3-s2.code" &
PIDT=$!
wait $PIDS $PIDT
SC1=$(cat "$ROOT/w3-s1.code")
SC2=$(cat "$ROOT/w3-s2.code")
WIN=0
[ "$SC1" = "200" ] && WIN=$((WIN + 1))
[ "$SC2" = "200" ] && WIN=$((WIN + 1))
expect "$WIN" "1" "并行提交仅 1 份进入待签（$SC1 / $SC2）"
OCCUPIED=$($MYSQL -e "SELECT COUNT(*) FROM contract WHERE house_id=$H4 AND status IN ('PENDING_SIGN','ACTIVE') AND deleted=0;" 2>/dev/null)
expect "$OCCUPIED" "1" "同一房源占用合同数量为 1"
curl -s -X POST $BASE/contracts/$KB1/terminate -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"reason":"并发占用清理"}' > /dev/null
curl -s -X POST $BASE/contracts/$KB2/terminate -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{"reason":"并发占用清理"}' > /dev/null

echo "== 15. EXPLAIN：预约/合同列表命中索引 =="
$MYSQL -e "DELETE FROM viewing_appointment WHERE contact_phone='13900000000';
DELETE FROM contract WHERE contract_no LIKE 'EXPLAIN%';" 2>/dev/null
$MYSQL -e "INSERT INTO viewing_appointment (house_id, customer_id, preferred_time, contact_phone, status, version, deleted, created_at, updated_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<2000)
SELECT $HID, $CID, DATE_ADD(UTC_TIMESTAMP(3), INTERVAL n HOUR), '13900000000', 'CONFIRMED', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM seq;" 2>/dev/null
$MYSQL -e "INSERT INTO contract (contract_no, house_id, customer_id, owner_id, title, amount, deposit, payment_cycle, start_date, end_date, status, template_version, snapshot_json, version, deleted, created_at, updated_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<2000)
SELECT CONCAT('EXPLAIN', n), $HID, $CID, $OID, CONCAT('压测合同', n), 6000.00, 12000.00, 'MONTHLY', '2026-12-01', '2027-11-30', 'TERMINATED', 'STANDARD_LEASE-v1', '{}', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM seq;" 2>/dev/null
APPT_PLAN=$($MYSQL -e "EXPLAIN SELECT id FROM viewing_appointment WHERE deleted=0 AND customer_id=$CID AND status='CONFIRMED' ORDER BY preferred_time DESC LIMIT 10;" 2>/dev/null)
CON_PLAN=$($MYSQL -e "EXPLAIN SELECT id FROM contract WHERE deleted=0 AND customer_id=$CID AND status='ACTIVE' ORDER BY created_at DESC LIMIT 10;" 2>/dev/null)
echo "$APPT_PLAN"
echo "$CON_PLAN"
echo "$APPT_PLAN" | grep -q "idx_appointment_customer_status_time" && ok "预约列表命中 idx_appointment_customer_status_time" || bad "预约列表未命中预期索引"
echo "$CON_PLAN" | grep -q "idx_contract_customer_status_created" && ok "合同列表命中 idx_contract_customer_status_created" || bad "合同列表未命中预期索引"
echo "$APPT_PLAN" | grep -qw "ALL" && bad "预约列表全表扫描" || ok "预约列表非全表扫描"
echo "$CON_PLAN" | grep -qw "ALL" && bad "合同列表全表扫描" || ok "合同列表非全表扫描"
$MYSQL -e "DELETE FROM viewing_appointment WHERE contact_phone='13900000000';
DELETE FROM contract WHERE contract_no LIKE 'EXPLAIN%';" 2>/dev/null
ok "压测数据已清理"

rm -f "$ROOT"/w3-c1.json "$ROOT"/w3-c2.json "$ROOT"/w3-c1.code "$ROOT"/w3-c2.code \
      "$ROOT"/w3-s1.code "$ROOT"/w3-s2.code "$ROOT"/w3-pdf.hdr "$ROOT"/w3-contract.pdf

echo "======================================"
echo "W3 冒烟结果：通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" -eq 0 ] || exit 1
