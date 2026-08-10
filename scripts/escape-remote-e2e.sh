#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${ESCAPE_E2E_BASE_URL:-http://127.0.0.1:18084}"
ENV_FILE="${NBYS_ENV_FILE:-/home/nbys-platform/.env}"
RUN_TAG="E2E-$(date +%Y%m%d%H%M%S)"
STARTED_AT="$(date '+%F %T')"

while IFS= read -r line; do
  export "$line"
done < <(grep -E '^(DB_(HOST|PORT|NAME|USER|PASSWORD)|AUTH_TOKEN_SECRET)=' "$ENV_FILE")

mysql_exec() {
  MYSQL_PWD="$DB_PASSWORD" mysql -h"${DB_HOST:-127.0.0.1}" -P"${DB_PORT:-3306}" \
    -u"${DB_USER:-root}" -N "${DB_NAME:-nbys-activity-manager}" "$@"
}

ADMIN_ID="$(mysql_exec -e "select id from users where disabled=0 and role in ('superadmin','admin') order by id limit 1")"
test -n "$ADMIN_ID"
ASSET_EXISTED="$(mysql_exec -e "select count(*) from escape_user_assets where user_id=${ADMIN_ID}")"
ASSET_SNAPSHOT=""
if [[ "$ASSET_EXISTED" != "0" ]]; then
  ASSET_SNAPSHOT="$(mysql_exec -e "
    select concat_ws('|', cash_balance, personal_width, personal_height,
      buffer_width, buffer_height, version,
      date_format(created_at, '%Y-%m-%d %H:%i:%s'),
      date_format(updated_at, '%Y-%m-%d %H:%i:%s'))
    from escape_user_assets where user_id=${ADMIN_ID}")"
fi

TOKEN="$(
  USER_ID="$ADMIN_ID" python3 - <<'PY'
import base64, hashlib, hmac, os, time, uuid
value = f"{os.environ['USER_ID']}:{int(time.time()) + 3600}:{uuid.uuid4().hex}"
payload = base64.urlsafe_b64encode(value.encode()).decode().rstrip("=")
signature = base64.urlsafe_b64encode(
    hmac.new(os.environ["AUTH_TOKEN_SECRET"].encode(), payload.encode(), hashlib.sha256).digest()
).decode().rstrip("=")
print(f"{payload}.{signature}")
PY
)"

api() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local key="${4:-}"
  local args=(-fsS -X "$method" -H "Authorization: Bearer $TOKEN")
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" --data "$body")
  fi
  if [[ -n "$key" ]]; then
    args+=(-H "Idempotency-Key: $key")
  fi
  curl "${args[@]}" "${BASE_URL}${path}"
}

field() {
  local expression="$1"
  python3 -c "import json,sys; data=json.load(sys.stdin); print(${expression})"
}

cleanup() {
  set +e
  mysql_exec <<SQL
set @match_id = ${MATCH_ID:-0};
set @product_id = ${PRODUCT_ID:-0};
set @item_id = ${ITEM_ID:-0};
set @season_id = ${SEASON_ID:-0};
delete from escape_match_settlement_items
 where settlement_detail_id in (
   select id from escape_match_settlement_details
    where settlement_id in (select id from escape_match_settlements where match_id=@match_id)
 );
delete from escape_match_settlement_details
 where settlement_id in (select id from escape_match_settlements where match_id=@match_id);
delete from escape_match_settlements where match_id=@match_id;
delete from escape_inventory_instances where source_type in ('shop','match_settlement','admin_grant')
 and user_id=${ADMIN_ID}
 and (source_id=@product_id or acquired_at>='${STARTED_AT}');
delete from escape_orders where user_id=${ADMIN_ID} and created_at>='${STARTED_AT}';
delete from escape_cash_ledger where user_id=${ADMIN_ID} and created_at>='${STARTED_AT}';
delete from escape_operation_idempotency where user_id=${ADMIN_ID} and created_at>='${STARTED_AT}';
delete from escape_match_participants where match_id=@match_id;
delete from escape_match_items where match_id=@match_id;
delete from escape_matches where id=@match_id;
delete from escape_shop_products where id=@product_id;
delete from escape_items where id=@item_id;
delete from escape_seasons where id=@season_id;
delete from escape_admin_audit_log where actor_user_id=${ADMIN_ID} and created_at>='${STARTED_AT}';
SQL
  if [[ "$ASSET_EXISTED" == "0" ]]; then
    mysql_exec -e "delete from escape_user_assets where user_id=${ADMIN_ID}"
  elif [[ -n "$ASSET_SNAPSHOT" ]]; then
    IFS='|' read -r asset_cash personal_width personal_height buffer_width buffer_height \
      asset_version asset_created_at asset_updated_at <<<"$ASSET_SNAPSHOT"
    mysql_exec -e "
      update escape_user_assets
      set cash_balance=${asset_cash},
          personal_width=${personal_width},
          personal_height=${personal_height},
          buffer_width=${buffer_width},
          buffer_height=${buffer_height},
          version=${asset_version},
          created_at='${asset_created_at}',
          updated_at='${asset_updated_at}'
      where user_id=${ADMIN_ID}"
  fi
}

ITEM_ID=0
PRODUCT_ID=0
SEASON_ID=0
MATCH_ID=0
trap cleanup EXIT

api GET /api/escape/h5/dashboard >/tmp/escape-e2e-dashboard.json
api POST "/api/escape/admin/user-assets/${ADMIN_ID}/adjust" \
  '{"cash_delta":5000,"reason":"自动化E2E临时额度"}' "${RUN_TAG}-adjust" >/dev/null

ITEM_ID="$(api POST /api/escape/admin/items \
  "{\"name\":\"${RUN_TAG}-测试物资\",\"rarity\":\"普通\",\"category\":\"regular\",\"min_price\":100,\"max_price\":100,\"current_price\":100,\"width\":1,\"height\":1,\"stock_quantity\":3,\"enabled\":true}" \
  | field 'data["data"]["id"]')"
PRODUCT_ID="$(api POST /api/escape/admin/shop-products \
  "{\"name\":\"${RUN_TAG}-测试商品\",\"product_type\":\"regular\",\"item_id\":${ITEM_ID},\"price\":100,\"stock\":2,\"enabled\":true}" \
  | field 'data["data"]["id"]')"
SEASON_ID="$(api POST /api/escape/admin/seasons \
  "{\"name\":\"${RUN_TAG}-测试赛季\",\"start_date\":\"2026-07-01\",\"end_date\":\"2026-08-31\",\"kill_reward\":100,\"enabled\":true}" \
  | field 'data["data"]["id"]')"
VENUE_ID="$(mysql_exec -e "select id from venues order by id limit 1")"
MATCH_ID="$(api POST /api/escape/admin/matches \
  "{\"name\":\"${RUN_TAG}-测试战局\",\"venue_id\":${VENUE_ID},\"season_id\":${SEASON_ID},\"team_count\":1,\"team_capacity\":2,\"match_items\":[{\"item_id\":${ITEM_ID},\"quantity\":2}]}" \
  | field 'data["data"]["match"]["id"]')"

api POST "/api/escape/h5/shop/products/${PRODUCT_ID}/purchase" '{"quantity":1}' "${RUN_TAG}-purchase" >/dev/null
INVENTORY_ID="$(api GET /api/escape/h5/warehouses/buffer | field 'data["data"]["items"][0]["inventory_id"]')"
api POST "/api/escape/h5/inventory/${INVENTORY_ID}/move" '{"target_warehouse":"personal"}' "${RUN_TAG}-move" >/dev/null
api POST "/api/escape/h5/inventory/${INVENTORY_ID}/sell" '' "${RUN_TAG}-sell" >/dev/null

api POST "/api/escape/h5/matches/${MATCH_ID}/join" >/dev/null
PROFESSION_ID="$(mysql_exec -e "select id from escape_professions where name='跑刀仔' limit 1")"
WEAPON_ID="$(mysql_exec -e "select id from escape_weapons where weapon_type='knife' and enabled=1 limit 1")"
api PUT "/api/escape/h5/matches/${MATCH_ID}/loadout" \
  "{\"team_no\":1,\"profession_id\":${PROFESSION_ID},\"weapon_id\":${WEAPON_ID},\"special_inventory_id\":null}" >/dev/null
api POST "/api/escape/h5/matches/${MATCH_ID}/loadout/lock" '' "${RUN_TAG}-lock" >/dev/null
api POST "/api/escape/admin/matches/${MATCH_ID}/start" '' "${RUN_TAG}-start" >/dev/null
PARTICIPANT_ID="$(api GET "/api/escape/admin/matches/${MATCH_ID}/settlement" | field 'data["data"]["participants"][0]["id"]')"
api POST "/api/escape/admin/matches/${MATCH_ID}/settle" \
  "{\"note\":\"${RUN_TAG}\",\"participants\":[{\"participant_id\":${PARTICIPANT_ID},\"escaped\":true,\"kills\":1,\"manual_cash\":0,\"items\":[{\"item_id\":${ITEM_ID},\"quantity\":1}]}]}" \
  "${RUN_TAG}-settle" >/dev/null

RECORD_COUNT="$(api GET /api/escape/h5/records | field 'len(data["data"])')"
DETAIL_STATUS="$(api GET "/api/escape/h5/records/${MATCH_ID}" | field 'data["code"]')"
echo "escape-e2e passed: record_count=${RECORD_COUNT}, detail_code=${DETAIL_STATUS}"
