# V79 对局管理修复版

基于 V78 修复点击“对局管理”出现 Internal Server Error 的问题。

## 修复内容

- 修复对局管理页面中预设场地查询排序引用错误。
- 原错误：使用了不存在的 `VenuePreset.name`。
- 修复后：使用现有模型 `Venue.name`。

## 部署提醒

更新时继续保留：

```text
data/app.db
static/uploads/
```

部署命令：

```bash
docker compose down
docker compose build
docker compose up -d
```
