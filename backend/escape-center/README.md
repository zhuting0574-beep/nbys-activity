# escape-center API（第一版）

独立 Spring Boot 2.7 / Java 8 微服务，复用 `nbys-common` 的 Bearer Token 鉴权。
模块负责逃离西撇镇玩法领域、H5 接口与后台管理接口。

## 路由

统一前缀：`/api/escape/h5`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/dashboard` | 现金、仓库容量、赛季与个人统计 |
| GET | `/matches` | 整备中、进行中对局 |
| GET | `/matches/{id}` | 对局详情；按阶段、小队和管理员身份裁剪隐私字段 |
| POST | `/matches/{id}/join` | 幂等加入对局（数据库唯一键兜底） |
| PUT | `/matches/{id}/loadout` | 保存小队、兵种、武器与特殊武器实例 |
| POST | `/matches/{id}/loadout/lock` | 锁定配装与特殊武器 |
| GET | `/warehouses/personal` | 个人仓库 |
| GET | `/warehouses/buffer` | 缓冲区仓库 |
| POST | `/inventory/{id}/move` | 移动物品，可指定 `pos_x/pos_y` 或自动寻找空位 |
| POST | `/inventory/{id}/sell` | 按当日价格出售单件物品 |
| POST | `/inventory/sell-all` | 出售指定仓库全部可用物品 |
| GET | `/shop/products` | 有库存且未下架商品 |
| POST | `/shop/products/{id}/purchase` | 购买商品或仓库扩充 |
| GET | `/records?only_mine=true` | 已结束对局记录 |
| GET | `/records/{id}` | 对局记录详情与结算物品快照 |

除 GET、加入对局和保存草稿配装外，写接口要求 HTTP 头：

```text
Idempotency-Key: 客户端生成的 UUID（最多 80 字符）
```

所有响应沿用平台统一结构：

```json
{"code": 0, "message": "ok", "data": {}}
```

## 关键请求

保存配装：

```json
{
  "team_no": 1,
  "profession_id": 2,
  "weapon_id": 5,
  "special_inventory_id": null
}
```

移动物品：

```json
{
  "target_warehouse": "personal",
  "pos_x": 0,
  "pos_y": 2
}
```

不传坐标时服务端采用从上到下、从左到右的稳定首次适配算法。

购买商品：

```json
{"quantity": 1}
```

## 领域约束

- 普通用户必须存在启用的 `escape_user_access`；平台管理员和逃离西撇镇管理员直接放行。
- 余额、库存、仓库布局、对局与参与者均在事务中加行锁校验。
- 仓库物品是独立实例；不同尺寸不能重叠或越界。
- 配装锁定后不能修改；特殊武器实例原子切换为 `loadout_locked`，不能移动或出售。
- 跑刀仔只允许刀；特殊武器必须由 `escape_weapons.item_id` 显式绑定个人仓库中的武器物品。
- 对局开始前只暴露其他用户呼号；开始后只向普通用户展示本小队配装；管理员可见全部。
- 商店购买写现金流水和订单，库存与余额在同一事务内变更。
- 每日 `Asia/Shanghai` 05:00 先刷新物品价格，再逐用户自动出售缓冲区可用物品。
- 终局以 `escape_match_settlements`、明细和物品快照作为不可变凭证；participant 字段仅是查询投影。

## 与后台管理接口的边界

后台管理服务可复用本模块表结构，但不应直接复制 H5 写业务：

- 创建/编辑/开始/结算对局应调用独立后台领域服务。
- 开始对局必须重新检查全部配装锁定状态和余额，在一个事务内扣除维护费/使用费并将特殊武器置为 `in_match`。
- 结算必须先写不可变结算凭证，再更新余额、缓冲区物品、特殊武器结果和 participant 投影。
- 物品软停用使用 `enabled/deleted_at`；若业务选择彻底删除模板，应先处理所有库存实例和结算快照引用。

## 后台管理接口

统一前缀：`/api/escape/admin`。网关将此前缀独立路由到 `escape-center`。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/overview` | `escape:view` | 运营、经济与当前战局概览 |
| GET/POST | `/matches` | `escape:match:view/create` | 战局列表/创建 |
| GET/PUT/DELETE | `/matches/{id}` | `escape:match:view/update/delete` | 详情、编辑、软取消 |
| POST | `/matches/{id}/start` | `escape:match:start` | 全员锁定校验、原子扣费并开始 |
| GET | `/matches/{id}/settlement` | `escape:match:settle` | 结算详情或未结算参与者预览 |
| POST | `/matches/{id}/settle` | `escape:match:settle` | 一次性最终结算 |
| CRUD | `/items` | `escape:item:*` | 物品配置；DELETE 为软停用 |
| CRUD | `/shop-products` | `escape:shop:*` | 商店商品 |
| CRUD | `/seasons` | `escape:season:*` | 赛季配置 |
| POST | `/seasons/{id}/enable` | `escape:season:update` | 原子切换唯一启用赛季 |
| CRUD | `/classes` | `escape:class:*` | 职业配置（数据库表为 professions） |
| CRUD | `/weapons` | `escape:weapon:*` | 武器配置 |
| GET | `/user-assets` | `escape:userAsset:view` | 按用户名/呼号查询用户资产 |
| POST | `/user-assets/{userId}/adjust` | `escape:userAsset:adjust` | 调整现金及个人仓库尺寸 |
| GET/POST | `/item-grants` | `escape:itemGrant:create` | 入库流水/向缓冲区发放物品 |
| GET | `/audit` | `escape:audit` | 后台变更审计流水 |

前端兼容字段：战局接受 `squad_count/squad_capacity`；商店接受
`offline_at`；赛季接受 `start_at/end_at`；职业接受
`maintenance_cost`；武器接受 `cost/max_durability`。服务内部统一转换为领域字段。

开始、结算、资产调整和物品入库建议发送 `Idempotency-Key`。第一版管理端未发送时，
服务会生成请求级 key 以保持兼容；战局唯一约束仍确保开始和最终结算不可重复。

最终结算请求示例：

```json
{
  "note": "现场裁判确认",
  "participants": [
    {
      "participant_id": 101,
      "escaped": true,
      "kills": 2,
      "manual_cash": 100,
      "items": [
        {"item_id": 8, "quantity": 1}
      ]
    }
  ]
}
```

结算必须包含该战局的全部参与者，不能重复。整个结算在一个数据库事务中完成：

- 锁定战局、参与者、现金账户和相关库存行；
- 快照赛季击杀奖励、用户配装、结算前后余额及物品名称/品质/价格；
- 现金永不允许为负；
- 特殊武器撤离成功时扣耐久并归还，撤离失败或耐久归零时销毁；
- 收益、缓冲区物品、参与者查询投影和战局状态同时提交；
- `escape_match_settlements` 的战局唯一键与幂等键共同防止重复结算。

所有后台写操作都会写入 `escape_admin_audit_log`，资产变化另外写入不可修改的
`escape_cash_ledger`，物品入库写入 `escape_item_grants`。

数据库增量脚本：
`public-center/src/main/resources/db/migration/V20260723__escape_from_xp_domain.sql`
