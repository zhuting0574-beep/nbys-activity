# V82 - 逃离西撇镇对局统计与击杀奖励

基于 V81 修改：

1. 个人主页统计从“参加逃离西撇镇次数”改为“参加对局数”。
2. 个人主页新增：
   - 成功撤离次数
   - 撤离成功率
   - 平均每局获得现金
   - 平均每局物品价值
3. 赛季设置新增“每个人头现金奖励”。
4. 终局结算新增“击杀现金”列，根据人头数和赛季设置自动计算。
5. 击杀现金会和手动录入的获得现金一起加入用户现金。
6. 结算获得的物品会关联到对应对局参与记录，用于计算平均每局物品价值。
7. 数据库自动升级：
   - extraction_seasons.kill_reward_cash
   - extraction_match_participants.kill_reward_cash
   - extraction_inventory_items.match_participant_id

部署时继续保留：

```text
data/app.db
static/uploads/
```
