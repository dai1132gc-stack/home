#!/usr/bin/env bash
# W2 房源/客源/媒体/收藏端到端冒烟（需后端已启动、Redis 与 MySQL 可用）
# 用法：bash docs/smoke-w2.sh
set -u
BASE=http://localhost:8080/api/v1
ROOT=$(cd "$(dirname "$0")" && pwd)
IMG="$ROOT/smoke-sample.png"
IMG_WIN=$(cygpath -m "$IMG" 2>/dev/null || echo "$IMG")
STAMP=$(date +%s)

json_field() { grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }

# 70 字节 1x1 PNG，仅用于上传校验；Windows 版 curl 需要 Windows 路径，故用 cygpath -m
printf 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' | base64 -d > "$IMG"

echo "== 1. 健康检查 =="
curl -s http://localhost:8080/actuator/health | head -c 120; echo

echo "== 2. ADMIN 登录 =="
ADMIN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123456","clientType":"WEB"}')
AT=$(echo "$ADMIN" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "admin token=${AT:0:20}..."

echo "== 3. 新建客源（ADMIN）=="
OWNER=$(curl -s -X POST $BASE/owners -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"name\":\"冒烟房东$STAMP\",\"phone\":\"13800138000\",\"type\":\"LANDLORD\",\"remark\":\"W2 冒烟\"}")
echo "$OWNER" | head -c 300; echo
OID=$(echo "$OWNER" | json_field id)

echo "== 4. 新建房源（ADMIN，初始 PENDING）=="
HOUSE=$(curl -s -X POST $BASE/houses -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"冒烟房源$STAMP\",\"houseType\":\"RENT\",\"community\":\"测试小区\",\"region\":\"浦东新区\",\"address\":\"测试路 1 号\",\"price\":4500.00,\"area\":89.50,\"rooms\":2,\"bathrooms\":1,\"floor\":\"3/18\",\"description\":\"W2 冒烟房源\",\"ownerId\":$OID}")
echo "$HOUSE" | head -c 400; echo
HID=$(echo "$HOUSE" | json_field id)
STATUS=$(echo "$HOUSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "houseId=$HID status=$STATUS"

echo "== 5. 游客读取 PENDING 房源（应 404）=="
curl -s -o /dev/null -w "%{http_code}" $BASE/houses/$HID; echo

echo "== 6. PENDING 直接下架（应 409 状态机拒绝）=="
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/houses/$HID/offline -H "Authorization: Bearer $AT"; echo

echo "== 7. 上传图片（ADMIN）=="
UP=$(curl -s -X POST $BASE/files/upload -H "Authorization: Bearer $AT" -F "file=@$IMG_WIN;type=image/png")
echo "$UP" | head -c 300; echo
URL=$(echo "$UP" | grep -o '"url":"[^"]*"' | cut -d'"' -f4)

echo "== 7.1 上传非图片（应 400）=="
BAD="$ROOT/smoke-bad.txt"
printf 'not an image' > "$BAD"
BAD_WIN=$(cygpath -m "$BAD" 2>/dev/null || echo "$BAD")
curl -s -o /dev/null -w "non-image=%{http_code}\n" -X POST $BASE/files/upload \
  -H "Authorization: Bearer $AT" -F "file=@$BAD_WIN;type=text/plain"
rm -f "$BAD"

echo "== 8. 关联媒体（ADMIN）=="
curl -s -X POST $BASE/houses/$HID/media -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"url\":\"$URL\",\"mediaType\":\"IMAGE\",\"sort\":0}" | head -c 250; echo

echo "== 9. 发布房源（PENDING → PUBLISHED）=="
PUB=$(curl -s -X POST $BASE/houses/$HID/publish -H "Authorization: Bearer $AT")
echo "$PUB" | head -c 400; echo

echo "== 10. 游客列表筛选（type=RENT + 价格区间 + 排序）=="
curl -s "$BASE/houses?page=1&size=5&houseType=RENT&minPrice=1000&maxPrice=9000&sort=price_asc&keyword=%E5%86%92%E7%83%9F%E6%88%BF%E6%BA%90" | head -c 500; echo

echo "== 11. 游客详情（不应出现 ownerName/ownerPhone）=="
GUEST=$(curl -s $BASE/houses/$HID)
echo "$GUEST" | head -c 500; echo
echo "$GUEST" | grep -q 'ownerPhone' && echo "FAIL: 游客看到 ownerPhone" || echo "OK: 游客无 ownerPhone"

echo "== 12. CUSTOMER 登录 =="
MINI=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"customer","password":"Customer@123456","clientType":"MINI"}')
CT=$(echo "$MINI" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "customer token=${CT:0:20}..."

echo "== 13. CUSTOMER 访问 ADMIN 接口（应 403）=="
echo -n "POST /houses: "; curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/houses \
  -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' \
  -d '{"title":"x","houseType":"RENT","community":"c","region":"r","address":"a","price":1,"area":1,"rooms":1,"bathrooms":1}'; echo
echo -n "GET /owners: "; curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $CT" $BASE/owners; echo
echo -n "POST /files/upload: "; curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/files/upload -H "Authorization: Bearer $CT" -F "file=@$IMG_WIN;type=image/png"; echo

echo "== 14. CUSTOMER 收藏 / 重复收藏（幂等）/ 我的收藏 =="
curl -s -o /dev/null -w "favorite=%{http_code}\n" -X POST $BASE/houses/$HID/favorite -H "Authorization: Bearer $CT"
curl -s -o /dev/null -w "favorite-again=%{http_code}\n" -X POST $BASE/houses/$HID/favorite -H "Authorization: Bearer $CT"
curl -s -H "Authorization: Bearer $CT" "$BASE/me/favorites?page=1&size=5" | head -c 400; echo

echo "== 15. CUSTOMER 详情标记 favorited =="
curl -s -H "Authorization: Bearer $CT" $BASE/houses/$HID | grep -o '"favorited":[a-z]*' | head -1; echo

echo "== 16. 下架房源（PUBLISHED → OFFLINE）=="
curl -s -X POST $BASE/houses/$HID/offline -H "Authorization: Bearer $AT" | grep -o '"status":"[^"]*"' | head -1; echo

echo "== 17. 下架后游客详情（应 404）、收藏列表（应为空）=="
curl -s -o /dev/null -w "guest-detail=%{http_code}\n" $BASE/houses/$HID
curl -s -H "Authorization: Bearer $CT" "$BASE/me/favorites?page=1&size=5" | head -c 200; echo

echo "== 18. 重新上架 → 取消收藏 =="
curl -s -X POST $BASE/houses/$HID/publish -H "Authorization: Bearer $AT" | grep -o '"status":"[^"]*"' | head -1
curl -s -o /dev/null -w "unfavorite=%{http_code}\n" -X DELETE $BASE/houses/$HID/favorite -H "Authorization: Bearer $CT"
curl -s -H "Authorization: Bearer $CT" "$BASE/me/favorites?page=1&size=5" | grep -o '"total":[0-9]*' | head -1

echo "== 19. 客源列表（ADMIN，phone 明文）=="
curl -s -H "Authorization: Bearer $AT" "$BASE/owners?page=1&size=3&keyword=138001" | head -c 400; echo

echo "== 20. 逻辑删除房源 =="
curl -s -o /dev/null -w "delete=%{http_code}\n" -X DELETE $BASE/houses/$HID -H "Authorization: Bearer $AT"
curl -s -o /dev/null -w "detail-after-delete=%{http_code}\n" $BASE/houses/$HID

echo "== 21. 图片静态访问 =="
curl -s -o /dev/null -w "image=%{http_code}\n" "http://localhost:8080$URL"

echo "== 冒烟完成：houseId=$HID ownerId=$OID =="
