#!/usr/bin/env bash
# W1 端到端认证冒烟脚本（需后端已启动：mvn spring-boot:run）
# 用法：bash smoke-w1.sh
set -u
BASE=http://localhost:8080/api/v1
COOKIE=/tmp/realestate-smoke-cookie.txt
rm -f "$COOKIE"

echo "== 1. 健康检查 =="
curl -s $BASE/../actuator/health | head -c 200; echo

echo "== 2. ADMIN 登录（Web）=="
curl -s -c "$COOKIE" -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123456","clientType":"WEB"}' | head -c 400; echo

echo "== 3. 获取当前用户（带 Access Token）=="
TOKEN=$(curl -s -c "$COOKIE" -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123456","clientType":"WEB"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "accessToken=${TOKEN:0:30}..."
curl -s -H "Authorization: Bearer $TOKEN" $BASE/auth/me | head -c 300; echo

echo "== 4. ADMIN 权限接口 =="
curl -s -H "Authorization: Bearer $TOKEN" $BASE/smoke/admin | head -c 200; echo

echo "== 5. ADMIN 访问 CUSTOMER 接口（应 403）=="
curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" $BASE/smoke/customer; echo

echo "== 6. 刷新令牌（Web 用 Cookie）=="
curl -s -b "$COOKIE" -c "$COOKIE" -X POST $BASE/auth/refresh -H 'Content-Type: application/json' -d '{}' | head -c 300; echo

echo "== 7. 错误密码（应 401 统一提示）=="
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"wrong","clientType":"WEB"}'; echo

echo "== 8. 未登录访问受保护接口（应 401）=="
curl -s -o /dev/null -w "%{http_code}" $BASE/auth/me; echo

echo "== 9. CUSTOMER 登录（MINI，refreshToken 在 body）=="
MINI=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer","password":"Customer@123456","clientType":"MINI"}')
echo "$MINI" | head -c 400; echo
MINI_TOKEN=$(echo "$MINI" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
MINI_RT=$(echo "$MINI" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4)

echo "== 10. CUSTOMER 权限接口 =="
curl -s -H "Authorization: Bearer $MINI_TOKEN" $BASE/smoke/customer | head -c 200; echo

echo "== 11. CUSTOMER 访问 ADMIN 接口（应 403）=="
curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $MINI_TOKEN" $BASE/smoke/admin; echo

echo "== 12. 小程序刷新轮换 =="
curl -s -X POST $BASE/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$MINI_RT\"}" | head -c 300; echo

echo "== 13. 重放旧 refresh token（应 401 重放撤销）=="
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$MINI_RT\"}"; echo

echo "== 14. 退出登录 =="
curl -s -b "$COOKIE" -X POST $BASE/auth/logout -H 'Content-Type: application/json' -d '{}' | head -c 200; echo
echo "== 完成 =="
