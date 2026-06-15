# V77 资源保留修正版

这个版本基于 V76，已把用户上传的 `data/app.db` 和 `static/uploads/` 合并进完整部署包。

重要说明：
- 更新程序时，只能保留 / 复制：
  - `data/app.db`
  - `static/uploads/`
- 不要把旧的整个 `static/` 文件夹覆盖回项目，否则会把新版的 `static/style.css` 和 `static/app.js` 覆盖成旧版本，导致：
  - Banner 仍然发暗
  - 活动策划新增日期没有备注框
  - 投票 checkbox 和文字无法对齐

部署建议：
```bash
docker compose down
docker compose build
docker compose up -d
```

如果部署后样式没变化，请强制刷新浏览器：
- 电脑：Ctrl + F5
- 手机：清理浏览器缓存或换无痕窗口测试
