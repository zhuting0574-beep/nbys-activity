# V83 - 修复对局模型启动错误

修复 V82 中 SQLAlchemy 无法判断 `ExtractionMatchParticipant.weapon_inventory_item` 关系的外键路径问题。

## 修复内容

- 为 `ExtractionMatchParticipant.weapon_inventory_item` 显式指定 `foreign_keys=[weapon_inventory_item_id]`。
- 解决启动时报错：
  `Could not determine join condition ... multiple foreign key paths`。

## 部署

继续保留：

```text
data/app.db
static/uploads/
```

命令：

```bash
docker compose down
docker compose build
docker compose up -d
```
