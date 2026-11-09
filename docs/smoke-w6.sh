#!/usr/bin/env bash
# W6 智能增强端到端冒烟（需后端 8080、Redis、MySQL 可用；stub 模式另需 docs/ai-stub.mjs :8090）
# 自动识别两种模式：
#   AI_ENABLED=false（未配置）：house-search 200+degraded，policy-answer 503 AI_50301，基础业务不受影响；
#   AI_ENABLED=true（stub）：选房解析、政策发布自动索引、问答引用、insufficient、限流 429、metrics。
# 用法：bash docs/smoke-w6.sh
set -u
BASE=${BASE_URL:-http://localhost:8080/api/v1}
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
jget() {
  node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{let o;try{o=JSON.parse(s)}catch(e){console.log('');return}const v='$1'.split('.').filter(Boolean).reduce((a,k)=>{if(a==null)return a;const m=k.match(/^([^\[]*)\[(\d+)\]$/);if(m){const b=m[1]?a[m[1]]:a;return b==null?b:b[Number(m[2])]}return a[k]},o);console.log(v==null?'':v)})"
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "== 0. 健康检查 =="
curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"' && ok "后端健康" || { bad "后端未就绪"; exit 1; }
AI_ENABLED=$(curl -s $BASE/ai/status | jget '.data.enabled')
echo "  AI enabled=$AI_ENABLED"

echo "== 1. 白名单与安全边界 =="
expect "$(code $BASE/ai/status)" "200" "ai/status 公开可访问"
expect "$(code $BASE/admin/ai/metrics)" "401" "admin metrics 匿名拒绝"
expect "$(code -X POST $BASE/admin/ai/reindex)" "401" "admin reindex 匿名拒绝"
expect "$(code -X POST $BASE/admin/policies/1/reindex)" "401" "单条 reindex 匿名拒绝"
expect "$(code $BASE/houses)" "200" "基础房源接口不受 AI 影响"

echo "== 2. 自然语言选房 =="
if [ "$AI_ENABLED" = "false" ]; then
  RESP=$(curl -s -X POST $BASE/ai/house-search -H 'Content-Type: application/json' -d '{"query":"朝阳三室一厅","page":1,"size":5}')
  expect "$(echo "$RESP" | jget '.data.degraded')" "true" "AI 关闭时降级标记"
  expect "$(echo "$RESP" | jget '.data.degradedReason')" "AI_DISABLED" "降级原因"
  expect "$(echo "$RESP" | jget '.data.items[0].id' | grep -c .)" "1" "降级仍返回公开列表"
  expect "$(code -X POST $BASE/ai/policy-answer -H 'Content-Type: application/json' -d '{"question":"契税多少"}')" "503" "问答未配置 503"
  curl -s -X POST $BASE/ai/policy-answer -H 'Content-Type: application/json' -d '{"question":"契税多少"}' | grep -q "AI_50301" && ok "错误码 AI_50301" || bad "错误码 AI_50301"
else
  RESP=$(curl -s -X POST $BASE/ai/house-search -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.77.0.1' \
    -d '{"query":"朝阳 出租 三室 6000以内","page":1,"size":5}')
  expect "$(echo "$RESP" | jget '.data.degraded')" "false" "stub 模式不降级"
  expect "$(echo "$RESP" | jget '.data.parsedConditions.houseType')" "RENT" "意图 houseType"
  expect "$(echo "$RESP" | jget '.data.parsedConditions.region')" "朝阳" "意图 region"
  expect "$(echo "$RESP" | jget '.data.parsedConditions.rooms')" "3" "意图 rooms"
  expect "$(echo "$RESP" | jget '.data.parsedConditions.maxPrice')" "6000" "意图 maxPrice"
fi

echo "== 3. 敏感串与参数校验 =="
expect "$(code -X POST $BASE/ai/house-search -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.77.0.2' -d '{"query":""}')" "422" "空 query 422"
LONG=$(node -e "console.log('长'.repeat(201))")
expect "$(code -X POST $BASE/ai/house-search -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.77.0.3' -d "{\"query\":\"$LONG\"}")" "422" "query>200 字 422"

echo "== 4. AI 限流（独立 IP 打满 20 次/分钟）=="
LAST=""
for i in $(seq 1 21); do
  LAST=$(code -X POST $BASE/ai/house-search -H 'Content-Type: application/json' -H "X-Forwarded-For: 10.88.$STAMP.1" -d '{"query":"限流"}')
done
expect "$LAST" "429" "第 21 次请求限流 429"

if [ "$AI_ENABLED" = "true" ]; then
  echo "== 5. 政策索引与问答（stub）=="
  ADMIN_PASS=$(grep '^BOOTSTRAP_ADMIN_PASSWORD=' realestate-server/.env | cut -d= -f2-)
  AT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASS\",\"clientType\":\"WEB\"}" | jget '.data.accessToken')
  [ -n "$AT" ] && ok "管理员登录" || { bad "管理员登录失败"; exit 1; }

  # 造一条政策并发布（发布后 afterCommit 自动索引）
  PID=$(curl -s -X POST $BASE/policies -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
    -d "{\"title\":\"W6冒烟契税政策$STAMP\",\"docNo\":\"W6-$STAMP\",\"docType\":\"POLICY\",\"source\":\"测试住建局\",\"content\":\"首套住房契税税率说明。面积九十平方米以下按百分之一征收，九十平方米以上按百分之一点五征收。二套住房按百分之二征收。本条为冒烟测试数据。\"}" | jget '.data.id')
  [ -n "$PID" ] && ok "政策创建 id=$PID" || { bad "政策创建失败"; exit 1; }
  curl -s -X POST $BASE/policies/$PID/publish -H "Authorization: Bearer $AT" -o /dev/null
  sleep 2
  CHUNKS=$($MYSQL -e "SELECT COUNT(*) FROM policy_document_chunk WHERE policy_id=$PID AND embedding IS NOT NULL" 2>/dev/null)
  [ "${CHUNKS:-0}" -ge 1 ] && ok "发布后自动索引分块=$CHUNKS" || bad "自动索引分块（实际 $CHUNKS）"

  # 问答：共享字符袋向量保证可召回
  QA=$(curl -s -X POST $BASE/ai/policy-answer -H 'Content-Type: application/json' -H "X-Forwarded-For: 10.77.0.4" \
    -d '{"question":"首套住房契税税率面积征收"}')
  expect "$(echo "$QA" | jget '.data.insufficient')" "false" "问答可回答"
  expect "$(echo "$QA" | jget '.data.citations[0].title' | grep -c "W6冒烟")" "1" "引用含政策标题"
  expect "$(echo "$QA" | jget '.data.citations[0].source')" "测试住建局" "引用含来源"
  echo "$QA" | grep -q "不构成法律意见" && ok "免责声明存在" || bad "免责声明缺失"

  # 拒答：与政策完全无关的问题（字符袋相似度低）
  QA2=$(curl -s -X POST $BASE/ai/policy-answer -H 'Content-Type: application/json' -H "X-Forwarded-For: 10.77.0.5" \
    -d '{"question":"xyzzyqv"}')
  expect "$(echo "$QA2" | jget '.data.insufficient')" "true" "无关问题拒答 insufficient"

  # 重建索引幂等
  RE=$(curl -s -X POST $BASE/admin/policies/$PID/reindex -H "Authorization: Bearer $AT")
  [ "$(echo "$RE" | jget '.data.chunks')" -ge 1 ] && ok "单条重建索引幂等" || bad "单条重建索引"
  REALL=$(curl -s -X POST $BASE/admin/ai/reindex -H "Authorization: Bearer $AT")
  [ "$(echo "$REALL" | jget '.data.policies')" -ge 1 ] && ok "全量重建 policies=$(echo "$REALL" | jget '.data.policies')" || bad "全量重建"

  # 下架后分块删除
  curl -s -X POST $BASE/policies/$PID/offline -H "Authorization: Bearer $AT" -o /dev/null
  sleep 1
  LEFT=$($MYSQL -e "SELECT COUNT(*) FROM policy_document_chunk WHERE policy_id=$PID" 2>/dev/null)
  expect "${LEFT:-0}" "0" "下架后分块清除"
  curl -s -X DELETE $BASE/policies/$PID -H "Authorization: Bearer $AT" -o /dev/null
fi

echo "== 6. 调用统计 =="
ADMIN_PASS=${ADMIN_PASS:-$(grep '^BOOTSTRAP_ADMIN_PASSWORD=' realestate-server/.env | cut -d= -f2-)}
AT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASS\",\"clientType\":\"WEB\"}" | jget '.data.accessToken')
M=$(curl -s "$BASE/admin/ai/metrics" -H "Authorization: Bearer $AT")
[ "$(echo "$M" | jget '.data.total')" -ge 1 ] && ok "metrics 总量=$(echo "$M" | jget '.data.total')" || bad "metrics 总量"
echo "$M" | grep -q '"scene"' && ok "metrics 场景分组" || bad "metrics 场景分组"
[ "$(echo "$M" | jget '.data.recent[0].scene' | grep -c .)" = "1" ] && ok "最近记录（不含内容）" || bad "最近记录"
# 统计不含原始内容：字段名黑名单
echo "$M" | grep -qE '"query"|"question"|"answer"|"prompt"' && bad "metrics 泄露内容字段" || ok "metrics 无原始内容字段"

echo "== 结果：PASS=$PASS FAIL=$FAIL =="
[ "$FAIL" -eq 0 ] || exit 1
