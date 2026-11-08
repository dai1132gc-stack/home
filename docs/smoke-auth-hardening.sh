#!/usr/bin/env bash
# V6.0.1 认证加固冒烟（F1/F2/F4）：需后端 8080、Redis、MySQL 可用，仅限本地 dev。
# 覆盖：Refresh 并发轮换恰 1 成功 1 401、Refresh Cookie 属性、登录/微信登录限流 429、
#       限流不影响正常账号密码登录。
# 注意：限流测试通过 X-Forwarded-For 使用独立伪造 IP 段（dev 环境该头被信任），
#       避免占用 127.0.0.1 的真实登录配额。
# 用法：bash docs/smoke-auth-hardening.sh
set -u
BASE=http://localhost:8080/api/v1
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

echo "== 0. 健康检查与基线登录 =="
curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"' && ok "后端健康" || { bad "后端未就绪"; exit 1; }

echo "== 1. F2：Web 登录 Set-Cookie 属性 =="
HEADERS=$(curl -s -D - -o /dev/null -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123456","clientType":"WEB"}')
SET_COOKIE=$(echo "$HEADERS" | grep -i '^Set-Cookie:' | head -1)
echo "$SET_COOKIE" | grep -qi 'HttpOnly' && ok "Cookie 含 HttpOnly" || bad "Cookie 缺 HttpOnly：$SET_COOKIE"
echo "$SET_COOKIE" | grep -qi 'SameSite=Lax' && ok "Cookie 含 SameSite=Lax" || bad "Cookie 缺 SameSite=Lax：$SET_COOKIE"
echo "$SET_COOKIE" | grep -q 'Path=/api/v1/auth' && ok "Cookie Path=/api/v1/auth" || bad "Cookie Path 异常：$SET_COOKIE"
echo "$SET_COOKIE" | grep -qi 'Max-Age' && ok "Cookie 含 Max-Age" || bad "Cookie 缺 Max-Age：$SET_COOKIE"
# dev 默认 APP_COOKIE_SECURE=false，不应出现 Secure；prod 必须含 Secure（部署时人工核对）
echo "$SET_COOKIE" | grep -qi 'Secure' && bad "dev 下 Cookie 不应含 Secure：$SET_COOKIE" || ok "dev 下 Cookie 无 Secure（prod 由 APP_COOKIE_SECURE=true 开启）"

echo "== 2. F1：同一 Refresh Token 两路并发刷新，恰 1 个 200、1 个 401 =="
CT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"customer","password":"Customer@123456","clientType":"MINI"}' | jget '.data.refreshToken')
[ -n "$CT" ] && ok "customer 登录取得 refreshToken" || { bad "customer 登录失败"; exit 1; }
B1=$(mktemp); B2=$(mktemp); code1=$(mktemp); code2=$(mktemp)
curl -s -o "$B1" -w "%{http_code}" -X POST $BASE/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$CT\"}" > "$code1" &
curl -s -o "$B2" -w "%{http_code}" -X POST $BASE/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$CT\"}" > "$code2" &
wait
R1=$(cat "$code1"); R2=$(cat "$code2")
if { [ "$R1" = "200" ] && [ "$R2" = "401" ]; } || { [ "$R1" = "401" ] && [ "$R2" = "200" ]; }; then
  ok "并发刷新恰 1 个 200、1 个 401（$R1/$R2）"
else
  bad "并发刷新结果异常：$R1/$R2（期望 200/401 各一）"
fi
# 败者按重放撤销会话族后，胜者刚签发的新 refresh token 必须一并失效
if [ "$R1" = "200" ]; then WINNER_BODY="$B1"; else WINNER_BODY="$B2"; fi
RT1=$(jget '.data.refreshToken' < "$WINNER_BODY")
if [ -n "$RT1" ]; then
  C=$(code -X POST $BASE/auth/refresh -H 'Content-Type: application/json' \
    -d "{\"refreshToken\":\"$RT1\"}")
  expect "$C" "401" "败者撤销会话族后，胜者新 refresh token 已失效"
else
  bad "胜者响应未返回新 refreshToken"
fi
rm -f "$B1" "$B2" "$code1" "$code2"

echo "== 3. F4：登录限流（伪造独立 IP，不占用本机配额）=="
LIP="10.99.1.1"
for i in $(seq 1 10); do
  C=$(code -X POST $BASE/auth/login -H 'Content-Type: application/json' -H "X-Forwarded-For: $LIP" \
    -d "{\"username\":\"nouser$i\",\"password\":\"Wrong@123456\",\"clientType\":\"MINI\"}")
  [ "$C" = "401" ] || { bad "第 $i 次爆破尝试期望 401，实际 $C"; break; }
done
C=$(code -X POST $BASE/auth/login -H 'Content-Type: application/json' -H "X-Forwarded-For: $LIP" \
  -d '{"username":"nouserX","password":"Wrong@123456","clientType":"MINI"}')
expect "$C" "429" "同一 IP 第 11 次登录尝试被限流"

echo "== 4. F4：微信登录限流（凭据未配置时跳过）=="
WC=$(code -X POST $BASE/auth/wechat-login -H 'Content-Type: application/json' -H "X-Forwarded-For: 10.99.2.1" \
  -d '{"code":"x","clientType":"MINI"}')
if [ "$WC" = "503" ]; then
  ok "微信凭据未配置（503），限流用例标记为待凭据就绪后验证"
else
  WIP="10.99.2.1"
  for i in $(seq 1 9); do
    code -X POST $BASE/auth/wechat-login -H 'Content-Type: application/json' -H "X-Forwarded-For: $WIP" \
      -d '{"code":"invalid-code","clientType":"MINI"}' > /dev/null
  done
  C=$(code -X POST $BASE/auth/wechat-login -H 'Content-Type: application/json' -H "X-Forwarded-For: $WIP" \
    -d '{"code":"invalid-code","clientType":"MINI"}')
  expect "$C" "429" "同一 IP 第 11 次微信登录被限流"
fi

echo "== 5. 限流不影响正常账号密码登录 =="
C=$(code -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"customer","password":"Customer@123456","clientType":"MINI"}')
expect "$C" "200" "正常 customer 登录不受限流影响"

echo "== 6. F4：成功登录清零用户名失败计数，正常重复登录不被误伤 =="
for i in $(seq 1 6); do
  C=$(code -X POST $BASE/auth/login -H 'Content-Type: application/json' -H "X-Forwarded-For: 10.99.4.$i" \
    -d '{"username":"customer","password":"Customer@123456","clientType":"MINI"}')
  expect "$C" "200" "第 $i 次成功登录未被用户名限流误伤"
done

echo "======================================"
echo "认证加固冒烟结果：通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" -eq 0 ] || exit 1
