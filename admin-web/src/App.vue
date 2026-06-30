<template>
  <div v-if="!tokenValue" class="login">
    <h2>后管登录</h2>
    <el-form label-position="top">
      <el-form-item label="账号 / 呼号"><el-input v-model="loginForm.account" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password /></el-form-item>
      <el-button type="primary" style="width: 100%" @click="login">登录</el-button>
    </el-form>
  </div>

  <div v-else class="shell">
    <aside class="side">
      <div class="brand">NBYS 后管</div>
      <div v-for="item in menus" :key="item.key">
        <div class="menu" :class="{ active: active === item.key }" @click="active = item.key; load()">
          {{ item.name }}
        </div>
        <div v-if="item.key === 'system' && active === 'system'" class="submenu-list">
          <div v-for="child in systemMenus" :key="child.key" class="menu sub-menu" :class="{ active: systemActive === child.key }" @click.stop="switchSystemTab(child.key)">
            {{ child.name }}
          </div>
        </div>
      </div>
    </aside>

    <main class="main">
      <div class="toolbar">
        <strong>{{ currentMenu?.name }}</strong>
        <span class="muted">当前用户：{{ me.username }} / {{ me.callsign }}</span>
      </div>

      <section v-if="active === 'dashboard'" class="dashboard-page">
        <div class="dashboard-hero">
          <div>
            <h1>数据看板</h1>
            <p>{{ dashboardYear }} 年综合运营概览</p>
          </div>
          <div class="dashboard-filter">
            <el-date-picker v-model="dashboardYear" type="year" value-format="YYYY" placeholder="选择年度" />
            <el-button type="primary" @click="loadDashboard">刷新</el-button>
          </div>
        </div>

        <div class="dashboard-metrics">
          <div v-for="card in dashboardCards" :key="card.key" class="dashboard-metric">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
          </div>
        </div>

        <div class="dashboard-grid">
          <div class="dashboard-panel dashboard-panel-wide">
            <div class="dashboard-panel-head">
              <h2>月度趋势</h2>
              <span>活动 / 报名 / 签到</span>
            </div>
            <div ref="monthlyChart" class="dashboard-chart"></div>
          </div>
          <div class="dashboard-panel">
            <div class="dashboard-panel-head">
              <h2>热门场地</h2>
              <span>按签到人数</span>
            </div>
            <div ref="venueChart" class="dashboard-chart"></div>
          </div>
          <div class="dashboard-panel">
            <div class="dashboard-panel-head">
              <h2>热门模式</h2>
              <span>按活动使用次数</span>
            </div>
            <div ref="modeChart" class="dashboard-chart"></div>
          </div>
        </div>

        <div class="dashboard-grid dashboard-grid-lists">
          <div class="dashboard-panel">
            <div class="dashboard-panel-head">
              <h2>热门活动</h2>
              <span>按报名人数</span>
            </div>
            <ol class="dashboard-rank">
              <li v-for="item in dashboardRankings.popular_activities || []" :key="item.id">
                <span>{{ item.name }}</span>
                <b>{{ item.enroll_count || 0 }} 人</b>
              </li>
            </ol>
            <p v-if="!(dashboardRankings.popular_activities || []).length" class="muted">暂无数据</p>
          </div>
          <div class="dashboard-panel">
            <div class="dashboard-panel-head">
              <h2>活跃队员</h2>
              <span>按签到次数</span>
            </div>
            <ol class="dashboard-rank">
              <li v-for="item in dashboardRankings.active_members || []" :key="item.id">
                <span>{{ item.callsign || item.username }}</span>
                <b>{{ item.count || 0 }} 次</b>
              </li>
            </ol>
            <p v-if="!(dashboardRankings.active_members || []).length" class="muted">暂无数据</p>
          </div>
        </div>

        <div class="dashboard-panel">
          <div class="dashboard-panel-head">
            <h2>近期活动</h2>
            <span>{{ dashboardYear }} 年</span>
          </div>
          <el-table :data="dashboardOverview.recent_activities || []" border>
            <el-table-column prop="name" label="活动名称" min-width="160" />
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.start_at) || '-' }}</template>
            </el-table-column>
            <el-table-column label="地点" min-width="120"><template #default="{ row }">{{ row.location || '-' }}</template></el-table-column>
            <el-table-column prop="enroll_count" label="报名" width="90" />
            <el-table-column prop="checkin_count" label="签到" width="90" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </div>
      </section>

      <section v-if="active === 'activities'" class="card">
        <div class="toolbar">
          <el-input v-model="filters.name" placeholder="活动名称" style="width: 180px" />
          <el-select v-model="filters.recordType" placeholder="类型" clearable style="width: 150px">
            <el-option label="正式活动" value="activity" />
            <el-option label="活动策划" value="plan" />
          </el-select>
          <el-select v-model="filters.status" placeholder="状态" clearable style="width: 150px">
            <el-option v-for="status in activityStatuses" :key="status" :label="status" :value="status" />
          </el-select>
          <el-button @click="loadActivities">查询</el-button>
          <el-button type="primary" v-if="can('activity:create')" @click="openActivity()">新增活动</el-button>
          <el-button type="success" v-if="can('plan:create')" @click="openPlan()">新增活动策划</el-button>
        </div>
        <el-table :data="activities" border>
          <el-table-column prop="display_record_type" label="类型" width="110" />
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="activity_type" label="活动类型" width="110" />
          <el-table-column label="时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.start_at || row.vote_deadline || row.end_at) }}</template>
          </el-table-column>
          <el-table-column label="地点" min-width="120"><template #default="{ row }">{{ row.venue_name || row.location || '-' }}</template></el-table-column>
          <el-table-column label="报名"><template #default="{ row }">{{ row.enroll_count || 0 }} / {{ row.signup_limit || '-' }}</template></el-table-column>
          <el-table-column prop="checkin_count" label="签到" />
          <el-table-column prop="display_status" label="状态" />
          <el-table-column label="操作" width="520">
            <template #default="{ row }">
              <el-button size="small" @click="row.record_type === 'plan' ? openPlan(row) : openActivity(row)">查看/编辑</el-button>
              <template v-if="row.record_type === 'activity'">
                <el-button size="small" v-if="!row.deleted_at && can('activity:cancel')" @click="post(`/api/admin/activities/${row.id}/cancel`, loadActivities)">取消</el-button>
                <el-button size="small" v-if="row.deleted_at && can('activity:restore')" @click="post(`/api/admin/activities/${row.id}/restore`, loadActivities)">恢复</el-button>
                <el-button size="small" @click="downloadFile(`/api/admin/activities/${row.id}/enrollments/export`)">导出报名表</el-button>
                <el-button size="small" @click="downloadFile(`/api/admin/activities/${row.id}/launcher-rentals/export`)">导出租赁表</el-button>
                <el-button size="small" type="danger" v-if="can('activity:delete')" @click="remove(`/api/admin/activities/${row.id}`, loadActivities)">删除</el-button>
              </template>
              <template v-else>
                <el-button size="small" type="primary" v-if="can('activity:create') && !row.converted_activity_id" @click="convertPlan(row)">转为活动</el-button>
                <el-button size="small" type="danger" v-if="can('plan:delete')" @click="remove(`/api/admin/activity-plans/${row.id}`, loadActivities)">删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="active === 'venues'" class="card">
        <div class="toolbar">
          <el-input v-model="filters.name" placeholder="场地名称" style="width: 200px" />
          <el-button @click="loadVenues">查询</el-button>
          <el-button type="primary" v-if="can('venue:create')" @click="editVenue = {}">新增</el-button>
        </div>
        <el-table :data="venues" border>
          <el-table-column label="图片" width="110">
            <template #default="{ row }"><img v-if="row.image_url" class="thumb" :src="row.image_url" /><span v-else class="muted">未上传</span></template>
          </el-table-column>
          <el-table-column prop="name" label="场地名称" />
          <el-table-column prop="address" label="场地地址" />
          <el-table-column prop="created_at" label="创建时间" />
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-button size="small" v-if="can('venue:update')" @click="editVenue = { ...row }">修改</el-button>
              <el-button size="small" type="danger" v-if="can('venue:delete')" @click="remove(`/api/admin/venues/${row.id}`, loadVenues)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="active === 'modes'" class="card">
        <div class="toolbar">
          <el-input v-model="filters.name" placeholder="模式名称" style="width: 200px" />
          <el-button @click="loadModes">查询</el-button>
          <el-button type="primary" v-if="can('gameMode:create')" @click="editMode = {}">新增</el-button>
        </div>
        <el-table :data="modes" border>
          <el-table-column prop="name" label="模式名称" />
          <el-table-column prop="rules" label="模式内容" />
          <el-table-column prop="suitable_people" label="人数" />
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-button size="small" v-if="can('gameMode:update')" @click="editMode = { ...row }">修改</el-button>
              <el-button size="small" type="danger" v-if="can('gameMode:delete')" @click="remove(`/api/admin/game-modes/${row.id}`, loadModes)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="active === 'users'" class="card">
        <div class="toolbar">
          <el-input v-model="filters.keyword" placeholder="用户名/呼号" style="width: 200px" />
          <el-button @click="loadUsers">查询</el-button>
        </div>
        <el-table :data="users" border>
          <el-table-column type="index" label="序号" width="70" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="callsign" label="呼号" />
          <el-table-column label="状态"><template #default="{ row }">{{ row.disabled ? '禁用' : '正常' }}</template></el-table-column>
          <el-table-column label="权限"><template #default="{ row }">{{ userRoleLabel(row) }}</template></el-table-column>
          <el-table-column label="正式队员"><template #default="{ row }">{{ row.is_regular_member ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="邀请人"><template #default="{ row }">{{ row.inviter_callsign || row.inviter_name || '-' }}</template></el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button size="small" v-if="can('user:update')" @click="editUser = { ...row }">修改</el-button>
              <el-button size="small" v-if="can('user:resetPassword')" @click="resetUserPassword(row)">重置密码</el-button>
              <el-button size="small" type="danger" v-if="can('user:delete')" @click="remove(`/api/admin/users/${row.id}`, loadUsers)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="active === 'attendance'" class="attendance-page">
        <div class="attendance-card attendance-hero">
          <div>
            <h1>出勤率统计</h1>
            <p>管理员和出勤率管理可以查看并修改完整出勤记录。</p>
          </div>
          <div class="attendance-filter-card">
            <label>
              <span>自然年度</span>
              <el-date-picker v-model="attendanceYear" type="year" value-format="YYYY" placeholder="全部年度" />
            </label>
            <label>
              <span>活动地区</span>
              <el-select v-model="filters.region" clearable placeholder="全部地区">
                <el-option label="宁波" value="宁波" />
                <el-option label="外地" value="外地" />
              </el-select>
            </label>
            <el-checkbox v-model="filters.formalOnly">只看正式队员</el-checkbox>
            <el-button @click="loadAttendance">筛选</el-button>
          </div>
        </div>

        <div class="attendance-card attendance-history-card">
          <div>
            <h2>历史活动记录</h2>
            <p>用于补录非平台组织的活动；创建后可在下方表格手动点选出勤。</p>
          </div>
          <el-button type="primary" v-if="can('attendance:create')" @click="editEvent = {}">手动增加历史活动</el-button>
        </div>

        <div class="attendance-card">
          <div class="attendance-section-head">
            <div>
              <h2>出勤统计概览</h2>
              <p>{{ attendanceYear || '全部' }}年度</p>
            </div>
          </div>
          <div class="attendance-summary-grid">
            <div class="attendance-summary-item">
              <span>活动总次数</span>
              <strong>{{ summary.activity_total || 0 }}</strong>
            </div>
            <div class="attendance-summary-item">
              <span>前三出勤正式队员</span>
              <ol><li v-for="item in summary.top_formal_members || []" :key="item.id">{{ item.callsign || item.username }} <b>{{ item.count }}</b></li></ol>
              <p v-if="!(summary.top_formal_members || []).length" class="muted">暂无数据</p>
            </div>
            <div class="attendance-summary-item">
              <span>最多组织活动队员</span>
              <ol><li v-for="item in summary.top_organizers || []" :key="item.organizer">{{ item.organizer }} <b>{{ item.count }}</b></li></ol>
              <p v-if="!(summary.top_organizers || []).length" class="muted">暂无数据</p>
            </div>
            <div class="attendance-summary-item">
              <span>最受欢迎场地</span>
              <ol><li v-for="item in summary.popular_venues || []" :key="item.location">{{ item.location }} <b>{{ item.count }}</b></li></ol>
              <p v-if="!(summary.popular_venues || []).length" class="muted">暂无数据</p>
            </div>
          </div>
        </div>

        <div class="attendance-card">
          <div class="attendance-section-head">
            <h2>出勤表</h2>
            <p>实心圆点表示出勤。活动横向显示：名称 / 日期 / 场地 / 组织人。</p>
          </div>
          <div class="attendance-table-scroll">
            <el-table class="attendance-matrix" :style="{ minWidth: attendanceTableMinWidth }" :data="attendanceUsers" border height="560">
              <el-table-column prop="callsign" label="人员" fixed width="160" />
              <el-table-column label="出勤次数" fixed width="100"><template #default="{ row }">{{ countPresent(row.id) }}</template></el-table-column>
              <el-table-column v-for="event in attendanceEvents" :key="event.id" width="190">
                <template #header>
                  <div class="attendance-event-head">
                    <strong>{{ event.name }}</strong>
                    <span>{{ event.event_date }}</span>
                    <span>{{ event.location || '-' }}</span>
                    <span>{{ event.organizer || '-' }}</span>
                    <el-tag size="small" :type="event.activity_region === '外地' ? 'warning' : 'success'">{{ event.activity_region || '-' }}</el-tag>
                    <span>总: {{ eventPresentCount(event.id) }}</span>
                    <span>正式: {{ eventFormalPresentCount(event.id) }}</span>
                    <div class="attendance-event-actions">
                      <el-button size="small" v-if="can('attendance:update')" @click.stop="editEvent = { ...event }">编辑</el-button>
                      <el-button size="small" type="danger" v-if="can('attendance:delete') && event.is_manual" @click.stop="deleteAttendanceEvent(event)">删除</el-button>
                    </div>
                  </div>
                </template>
                <template #default="{ row }">
                  <button class="attendance-dot" :class="{ present: isPresent(event.id, row.id) }" :disabled="!can('attendance:update')" @click="togglePresent(event.id, row.id)">
                    {{ isPresent(event.id, row.id) ? '●' : '' }}
                  </button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </section>

      <section v-if="active === 'launchers'" class="card">
        <div class="toolbar">
          <el-input v-model="launcherManageFilters.name" placeholder="发射器名称" style="width: 200px" />
          <el-input v-model="launcherManageFilters.user" placeholder="拥有人用户名/呼号" style="width: 220px" />
          <el-button @click="loadLaunchers">查询</el-button>
        </div>
        <el-table :data="launchers" border>
          <el-table-column prop="name" label="发射器名称" min-width="160" />
          <el-table-column label="拥有人" min-width="140">
            <template #default="{ row }">{{ row.owner_callsign || row.owner_name || '-' }}</template>
          </el-table-column>
          <el-table-column label="租借价格" width="120">
            <template #default="{ row }">{{ row.rent_fee || 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ row.active ? '上架' : '下架' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" v-if="can('launcher:update')" @click="editLauncher = { ...row, active: !!row.active }">修改</el-button>
              <el-button size="small" type="danger" v-if="can('launcher:delete')" @click="remove(`/api/admin/launchers/${row.id}`, loadLaunchers)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="active === 'extraction'" class="card extraction-admin">
        <el-tabs v-model="extractionTab" @tab-change="loadExtraction">
          <el-tab-pane label="对局管理" name="matches">
            <div class="toolbar">
              <el-button type="primary" v-if="can('extraction:match')" @click="editExtractionMatch = { name: '', venue_name: '', squad_count: 2, squad_limit: 5 }">创建对局</el-button>
              <el-button @click="loadExtraction">刷新</el-button>
            </div>
            <el-table :data="extraction.matches" border>
              <el-table-column prop="name" label="对局名称" min-width="160" />
              <el-table-column prop="venue_name" label="场地" min-width="130" />
              <el-table-column label="状态" width="100"><template #default="{ row }">{{ extractionStatus(row.status) }}</template></el-table-column>
              <el-table-column prop="participant_count" label="人数" width="80" />
              <el-table-column label="操作" width="260">
                <template #default="{ row }">
                  <el-button size="small" @click="openExtractionMatch(row)">详情</el-button>
                  <el-button size="small" v-if="row.status === 'preparing' && can('extraction:match')" @click="editExtractionMatch = { ...row }">编辑</el-button>
                  <el-button size="small" type="success" v-if="row.status === 'preparing' && can('extraction:match')" @click="startExtractionMatch(row)">开局</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="物品管理" name="items">
            <div class="toolbar">
              <el-button type="primary" v-if="can('extraction:item')" @click="editExtractionItem = { level: '普通', item_category: '常规物品', min_price: 1, max_price: 100, width: 1, height: 1, active: true }">新增物品</el-button>
            </div>
            <el-table :data="extraction.items" border>
              <el-table-column prop="name" label="名称" min-width="150" />
              <el-table-column prop="level" label="等级" width="90" />
              <el-table-column prop="item_category" label="分类" width="110" />
              <el-table-column label="价格" width="130"><template #default="{ row }">{{ row.min_price }}-{{ row.max_price }}</template></el-table-column>
              <el-table-column label="体积" width="90"><template #default="{ row }">{{ row.width }}×{{ row.height }}</template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.active ? '启用' : '停用' }}</template></el-table-column>
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button size="small" @click="editExtractionItem = { ...row, active: !!row.active }">编辑</el-button>
                  <el-button size="small" type="danger" @click="remove(`/api/admin/extraction/items/${row.id}`, loadExtraction)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="物品录入" name="entry">
            <el-form class="entry-form" label-width="90px">
              <el-form-item label="用户"><el-select v-model="extractionEntry.user_id" filterable><el-option v-for="u in extraction.users" :key="u.id" :label="`${u.callsign || u.username}（${u.username}）`" :value="u.id" /></el-select></el-form-item>
              <el-form-item label="物品"><el-select v-model="extractionEntry.item_def_id" filterable><el-option v-for="i in extraction.items" :key="i.id" :label="`${i.name} / ${i.level} / ${i.item_category}`" :value="i.id" /></el-select></el-form-item>
              <el-form-item label="数量"><el-input-number v-model="extractionEntry.quantity" :min="1" /></el-form-item>
              <el-form-item><el-button type="primary" @click="submitExtractionEntry">录入到缓冲区</el-button></el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="商店管理" name="shop">
            <div class="toolbar"><el-button type="primary" @click="editExtractionShop = { category: 'storage', price: 0, stock: 1, storage_rows: 8, storage_cols: 12, active: true }">新增商品</el-button></div>
            <el-table :data="extraction.shop" border>
              <el-table-column prop="name" label="商品" min-width="150" />
              <el-table-column label="分类" width="110"><template #default="{ row }">{{ shopCategoryName(row.category) }}</template></el-table-column>
              <el-table-column prop="price" label="价格" width="90" />
              <el-table-column prop="stock" label="库存" width="90" />
              <el-table-column prop="shelf_until" label="下架日期" width="120" />
              <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.active ? '上架' : '下架' }}</template></el-table-column>
              <el-table-column label="操作" width="150"><template #default="{ row }"><el-button size="small" @click="editExtractionShop = { ...row, active: !!row.active }">编辑</el-button><el-button size="small" type="danger" @click="remove(`/api/admin/extraction/shop/${row.id}`, loadExtraction)">删除</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="赛季管理" name="seasons">
            <div class="toolbar">
              <el-button type="primary" @click="editExtractionSeason = { name: '', start_date: '', end_date: '', kill_reward_cash: 0, active: true }">新增赛季</el-button>
              <el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="saveExtractionBanner"><el-button>上传入口图</el-button></el-upload>
            </div>
            <el-table :data="extraction.seasons" border>
              <el-table-column prop="name" label="赛季" min-width="150" />
              <el-table-column prop="start_date" label="开始" width="120" />
              <el-table-column prop="end_date" label="结束" width="120" />
              <el-table-column prop="kill_reward_cash" label="人头奖励" width="110" />
              <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.active ? '启用' : '停用' }}</template></el-table-column>
              <el-table-column label="操作" width="100"><template #default="{ row }"><el-button size="small" @click="editExtractionSeason = { ...row, active: !!row.active }">编辑</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="兵种武器" name="rules">
            <h3>兵种管理</h3>
            <el-table :data="extraction.classes" border>
              <el-table-column prop="name" label="名称"><template #default="{ row }"><el-input v-model="row.name" /></template></el-table-column>
              <el-table-column prop="health" label="血量" width="160"><template #default="{ row }"><el-input-number v-model="row.health" :min="1" /></template></el-table-column>
              <el-table-column prop="maintenance_fee" label="维护费" width="160"><template #default="{ row }"><el-input-number v-model="row.maintenance_fee" :min="0" /></template></el-table-column>
            </el-table>
            <el-button type="primary" class="rule-save" @click="saveExtractionClasses">保存兵种</el-button>
            <h3>武器管理</h3>
            <el-table :data="extraction.weapons" border>
              <el-table-column label="类型" width="120"><template #default="{ row }">{{ weaponTypeName(row.weapon_type) }}</template></el-table-column>
              <el-table-column prop="name" label="名称" />
              <el-table-column prop="usage_fee" label="使用费" width="160"><template #default="{ row }"><el-input-number v-model="row.usage_fee" :min="0" :disabled="row.weapon_type !== 'regular'" /></template></el-table-column>
              <el-table-column prop="durability_cost_percent" label="耐久消耗%" width="180"><template #default="{ row }"><el-input-number v-model="row.durability_cost_percent" :min="0" :max="100" :disabled="row.weapon_type !== 'special'" /></template></el-table-column>
            </el-table>
            <el-button type="primary" class="rule-save" @click="saveExtractionWeapons">保存武器</el-button>
          </el-tab-pane>

          <el-tab-pane label="用户资产" name="assets">
            <div class="toolbar">
              <el-select v-model="assetUserId" filterable placeholder="选择用户" style="width: 260px"><el-option v-for="u in extraction.users" :key="u.id" :label="`${u.callsign || u.username}（${u.username}）`" :value="u.id" /></el-select>
              <el-button @click="loadExtractionAssets">查看</el-button>
            </div>
            <div v-if="extractionAssets.profile" class="asset-summary">甬士币：{{ extractionAssets.profile.cash || 0 }}　仓库：{{ extractionAssets.profile.storage_rows }}×{{ extractionAssets.profile.storage_cols }}</div>
            <el-table :data="[...(extractionAssets.storage || []), ...(extractionAssets.buffer || [])]" border>
              <el-table-column prop="name" label="物品" />
              <el-table-column prop="level" label="等级" width="90" />
              <el-table-column prop="item_category" label="分类" width="110" />
              <el-table-column prop="location" label="位置" width="100" />
              <el-table-column prop="quantity" label="数量" width="90" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="重置记录" name="reset">
            <div class="toolbar">
              <el-input v-model="resetConfirmText" placeholder="输入 资产重置 或 数据重置" style="width: 240px" />
              <el-button type="warning" @click="resetExtraction('assets')">资产重置</el-button>
              <el-button type="danger" @click="resetExtraction('data')">数据重置</el-button>
            </div>
            <el-table :data="extraction.reset_records" border>
              <el-table-column prop="reset_type" label="类型" width="110" />
              <el-table-column prop="requested_by_name" label="申请人" width="120" />
              <el-table-column prop="confirmed_by_name" label="确认人" width="120" />
              <el-table-column prop="status" label="状态" width="110" />
              <el-table-column prop="created_at" label="申请时间" min-width="160" />
              <el-table-column prop="confirmed_at" label="确认时间" min-width="160" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </section>

      <section v-if="active === 'system' && !systemActive" class="system-page">
        <p v-if="!systemMenus.length" class="muted">暂无可访问的系统管理功能</p>
      </section>

      <section v-if="active === 'system' && systemActive === 'permissions'" class="card">
        <div class="toolbar">
          <el-select v-model="permissionRole" @change="loadRolePermissions" style="width: 180px">
            <el-option v-for="role in roles" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
          <el-button type="primary" v-if="can('permission:update')" @click="savePermissions">保存</el-button>
        </div>
        <div class="permission-tree-panel">
          <el-tree
            ref="permissionTree"
            :data="permissionTreeData"
            :props="permissionTreeProps"
            node-key="id"
            show-checkbox
            default-expand-all
            :check-strictly="false"
          />
        </div>
      </section>

      <section v-if="active === 'system' && systemActive === 'systemImages'" class="card">
        <div class="settings-grid">
          <div class="settings-panel">
            <h3>登录页背景图</h3>
            <img v-if="systemImages.login_background_url" class="settings-preview" :src="systemImages.login_background_url" />
            <div v-else class="settings-empty">未设置背景图</div>
            <div class="banner-upload-row">
              <el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="r => systemImages.login_background_url = r.data.url"><el-button>上传背景图</el-button></el-upload>
              <el-button v-if="systemImages.login_background_url" @click="systemImages.login_background_url = ''">清除</el-button>
            </div>
          </div>
          <div class="settings-panel">
            <h3>标题 Logo</h3>
            <img v-if="systemImages.login_logo_url" class="settings-logo-preview" :src="systemImages.login_logo_url" />
            <div v-else class="settings-empty settings-logo-empty">使用默认 Logo</div>
            <div class="banner-upload-row">
              <el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="r => systemImages.login_logo_url = r.data.url"><el-button>上传Logo</el-button></el-upload>
              <el-button v-if="systemImages.login_logo_url" @click="systemImages.login_logo_url = ''">清除</el-button>
            </div>
          </div>
        </div>
        <el-button type="primary" v-if="can('systemImage:update')" @click="saveSystemImages">保存设置</el-button>
      </section>
    </main>
  </div>

  <el-dialog v-model="venueVisible" title="场地" width="560px"><el-form label-width="90px"><el-form-item label="场地图片"><div class="banner-upload-row"><el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="r => editVenue.image_url = r.data.url"><el-button>上传图片</el-button></el-upload><el-button v-if="editVenue.image_url" @click="editVenue.image_url = ''">清除</el-button></div><img v-if="editVenue.image_url" class="venue-preview" :src="editVenue.image_url" /><div v-else class="venue-empty">建议上传场地实景图，可作为活动默认Banner</div></el-form-item><el-form-item label="场地名称"><el-input v-model="editVenue.name" /></el-form-item><el-form-item label="场地地址"><el-input v-model="editVenue.address" /></el-form-item></el-form><template #footer><el-button @click="editVenue = null">取消</el-button><el-button type="primary" @click="saveVenue">保存</el-button></template></el-dialog>
  <el-dialog v-model="modeVisible" title="模式"><el-form label-width="90px"><el-form-item label="模式名称"><el-input v-model="editMode.name" /></el-form-item><el-form-item label="模式内容"><el-input v-model="editMode.rules" type="textarea" /></el-form-item><el-form-item label="人数"><el-input v-model="editMode.suitable_people" /></el-form-item></el-form><template #footer><el-button @click="editMode = null">取消</el-button><el-button type="primary" @click="saveMode">保存</el-button></template></el-dialog>
  <el-dialog v-model="userVisible" title="用户"><el-form label-width="110px"><el-form-item label="呼号"><el-input v-model="editUser.callsign" /></el-form-item><el-form-item label="权限"><el-select v-model="editUser.role"><el-option v-for="role in roles" :key="role.value" :label="role.label" :value="role.value" /></el-select></el-form-item><el-form-item label="账号禁用"><el-switch v-model="editUser.disabled" /></el-form-item><el-form-item label="正式队员"><el-switch v-model="editUser.is_regular_member" /></el-form-item></el-form><template #footer><el-button @click="editUser = null">取消</el-button><el-button type="primary" @click="saveUser">保存</el-button></template></el-dialog>
  <el-dialog v-model="launcherVisible" title="发射器" width="560px">
    <el-form v-if="editLauncher" label-width="90px">
      <el-form-item label="名称"><el-input v-model="editLauncher.name" /></el-form-item>
      <el-form-item label="价格"><el-input-number v-model="editLauncher.rent_fee" :min="0" /></el-form-item>
      <el-form-item label="简介"><el-input v-model="editLauncher.description" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="上架状态"><el-switch v-model="editLauncher.active" active-text="上架" inactive-text="下架" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="editLauncher = null">取消</el-button><el-button type="primary" @click="saveLauncher">保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="extractionItemVisible" title="逃离物品" width="620px">
    <el-form v-if="editExtractionItem" label-width="100px">
      <el-form-item label="图片"><div class="banner-upload-row"><el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="r => editExtractionItem.photo_filename = r.data.url"><el-button>上传图片</el-button></el-upload></div><img v-if="editExtractionItem.photo_filename" class="venue-preview" :src="editExtractionItem.photo_filename" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="editExtractionItem.name" /></el-form-item>
      <el-form-item label="等级"><el-select v-model="editExtractionItem.level"><el-option label="超凡" value="超凡" /><el-option label="史诗" value="史诗" /><el-option label="精品" value="精品" /><el-option label="普通" value="普通" /></el-select></el-form-item>
      <el-form-item label="分类"><el-select v-model="editExtractionItem.item_category"><el-option label="常规物品" value="常规物品" /><el-option label="武器" value="武器" /><el-option label="钱币" value="钱币" /></el-select></el-form-item>
      <el-form-item label="价格区间"><el-input-number v-model="editExtractionItem.min_price" :min="1" /> <span class="range-sep">至</span> <el-input-number v-model="editExtractionItem.max_price" :min="1" /></el-form-item>
      <el-form-item label="体积"><el-input-number v-model="editExtractionItem.width" :min="1" :max="30" /> <span class="range-sep">×</span> <el-input-number v-model="editExtractionItem.height" :min="1" :max="12" /></el-form-item>
      <el-form-item label="启用"><el-switch v-model="editExtractionItem.active" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="editExtractionItem = null">取消</el-button><el-button type="primary" @click="saveExtractionItem">保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="extractionShopVisible" title="逃离商店商品" width="620px">
    <el-form v-if="editExtractionShop" label-width="100px">
      <el-form-item label="分类"><el-select v-model="editExtractionShop.category"><el-option label="仓库扩充" value="storage" /><el-option label="常规物品" value="item" /><el-option label="武器" value="weapon" /><el-option label="钱币" value="money" /></el-select></el-form-item>
      <el-form-item label="名称"><el-input v-model="editExtractionShop.name" /></el-form-item>
      <el-form-item label="绑定物品"><el-select v-model="editExtractionShop.item_def_id" clearable filterable><el-option v-for="i in extraction.items" :key="i.id" :label="i.name" :value="i.id" /></el-select></el-form-item>
      <el-form-item label="价格"><el-input-number v-model="editExtractionShop.price" :min="0" /></el-form-item>
      <el-form-item label="库存"><el-input-number v-model="editExtractionShop.stock" :min="0" /></el-form-item>
      <el-form-item label="下架日期"><el-date-picker v-model="editExtractionShop.shelf_until" value-format="YYYY-MM-DD" /></el-form-item>
      <template v-if="editExtractionShop.category === 'storage'">
        <el-form-item label="仓库尺寸"><el-input-number v-model="editExtractionShop.storage_rows" :min="1" /> <span class="range-sep">×</span> <el-input-number v-model="editExtractionShop.storage_cols" :min="1" /></el-form-item>
      </template>
      <el-form-item label="上架"><el-switch v-model="editExtractionShop.active" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="editExtractionShop = null">取消</el-button><el-button type="primary" @click="saveExtractionShop">保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="extractionSeasonVisible" title="逃离赛季" width="560px">
    <el-form v-if="editExtractionSeason" label-width="100px">
      <el-form-item label="名称"><el-input v-model="editExtractionSeason.name" /></el-form-item>
      <el-form-item label="开始日期"><el-date-picker v-model="editExtractionSeason.start_date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="结束日期"><el-date-picker v-model="editExtractionSeason.end_date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="人头奖励"><el-input-number v-model="editExtractionSeason.kill_reward_cash" :min="0" /></el-form-item>
      <el-form-item label="启用"><el-switch v-model="editExtractionSeason.active" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="editExtractionSeason = null">取消</el-button><el-button type="primary" @click="saveExtractionSeason">保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="extractionMatchVisible" title="逃离对局" width="560px">
    <el-form v-if="editExtractionMatch" label-width="110px">
      <el-form-item label="名称"><el-input v-model="editExtractionMatch.name" /></el-form-item>
      <el-form-item label="场地"><el-select v-model="editExtractionMatch.venue_name" clearable filterable><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.name" /></el-select></el-form-item>
      <el-form-item label="小队数"><el-input-number v-model="editExtractionMatch.squad_count" :min="1" :max="20" /></el-form-item>
      <el-form-item label="每队人数"><el-input-number v-model="editExtractionMatch.squad_limit" :min="1" :max="50" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="editExtractionMatch = null">取消</el-button><el-button type="primary" @click="saveExtractionMatch">保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="extractionMatchDetailVisible" title="对局详情" width="900px">
    <template v-if="extractionMatchDetail.match">
      <div class="toolbar">
        <strong>{{ extractionMatchDetail.match.name }}</strong>
        <el-tag>{{ extractionStatus(extractionMatchDetail.match.status) }}</el-tag>
        <el-button type="danger" v-if="extractionMatchDetail.match.status === 'started'" @click="settleExtractionMatch">终局结算</el-button>
      </div>
      <el-table :data="extractionMatchDetail.participants || []" border>
        <el-table-column prop="callsign" label="用户" width="120" />
        <el-table-column prop="squad_no" label="小队" width="70" />
        <el-table-column prop="class_name" label="兵种" width="120" />
        <el-table-column prop="weapon_name" label="武器" width="140" />
        <el-table-column label="锁定" width="100"><template #default="{ row }">{{ row.locked ? '已锁定' : '未锁定' }}</template></el-table-column>
        <el-table-column label="撤离" width="90"><template #default="{ row }"><el-switch v-model="row.evacuated" /></template></el-table-column>
        <el-table-column label="人头" width="130"><template #default="{ row }"><el-input-number v-model="row.kills" :min="0" /></template></el-table-column>
        <el-table-column label="现金" width="130"><template #default="{ row }"><el-input-number v-model="row.earned_cash" :min="0" /></template></el-table-column>
        <el-table-column label="操作" width="110"><template #default="{ row }"><el-button size="small" v-if="row.locked && extractionMatchDetail.match.status === 'preparing'" @click="unlockExtractionParticipant(row)">解锁</el-button></template></el-table-column>
      </el-table>
    </template>
  </el-dialog>
  <el-dialog v-model="activityVisible" title="活动" width="760px">
    <el-form v-if="activityForm" label-width="130px">
      <el-form-item label="Banner图">
        <img v-if="activityBannerPreview()" class="banner-preview" :src="activityBannerPreview()" />
        <div v-else class="banner-empty">建议上传活动横幅图，将展示在 H5 首页和活动详情页</div>
        <div class="banner-upload-row">
          <el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="setActivityBanner"><el-button>上传Banner</el-button></el-upload>
          <el-button v-if="activityBannerPreview()" @click="clearActivityBanner">清除</el-button>
        </div>
      </el-form-item>
      <el-form-item label="活动名称"><el-input v-model="activityForm.name" /></el-form-item>
      <el-form-item label="活动类型"><el-select v-model="activityForm.activity_type"><el-option label="周常" value="周常" /><el-option label="本地活动" value="本地活动" /><el-option label="外地活动" value="外地活动" /></el-select></el-form-item>
      <el-form-item label="场地">
        <el-select v-model="activityForm.venue_id" clearable filterable placeholder="选择场地" @change="setActivityVenue">
          <el-option v-for="venue in venues" :key="venue.id" :label="venueLabel(venue)" :value="venue.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="地点"><el-input v-model="activityForm.location" placeholder="选择场地后自动填入，也可手动修改" /></el-form-item>
      <el-form-item label="时间">
        <el-date-picker v-model="activityForm.start_at" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        <el-date-picker v-model="activityForm.end_at" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
      </el-form-item>
      <el-form-item label="人数配置">
        <div class="number-config-grid">
          <label><span>开启人数</span><el-input-number v-model="activityForm.open_min" :min="0" /></label>
          <label><span>阵营数</span><el-input-number v-model="activityForm.camp_count" :min="1" /></label>
          <label><span>阵营人数上限</span><el-input-number v-model="activityForm.camp_limit" :min="0" /></label>
          <label><span>每阵营小队数</span><el-input-number v-model="activityForm.squad_count" :min="1" /></label>
          <label><span>小队人数上限</span><el-input-number v-model="activityForm.squad_limit" :min="0" /></label>
        </div>
      </el-form-item>
      <el-form-item label="地区"><el-select v-model="activityForm.activity_region"><el-option label="宁波" value="宁波" /><el-option label="外地" value="外地" /></el-select></el-form-item>
      <el-form-item label="可见范围"><el-select v-model="activityForm.visibility_type"><el-option label="所有人公开" value="all" /><el-option label="仅正式队员" value="official" /><el-option label="正式队员和邀请队员" value="official_plus_invite" /></el-select></el-form-item>
      <el-form-item v-if="activityForm.visibility_type === 'official_plus_invite'" label="邀请人员">
        <div class="invitee-panel">
          <el-button @click="openInvitePicker('activity')">选择邀请人员</el-button>
          <div class="invitee-tags">
            <el-tag v-for="id in normalizedInviteeIds(activityForm)" :key="id" closable @close="removeInvitee(activityForm, id)">{{ inviteeLabel(id) }}</el-tag>
            <span v-if="!normalizedInviteeIds(activityForm).length" class="muted">暂未选择邀请人员</span>
          </div>
        </div>
      </el-form-item>
      <el-form-item label="开放职业"><el-checkbox-group v-model="activityForm.allowed_jobs"><el-checkbox v-for="job in jobs" :key="job" :label="job" /></el-checkbox-group></el-form-item>
      <el-form-item label="游戏模式"><el-checkbox-group v-model="activityForm.game_modes"><el-checkbox v-for="mode in modes" :key="mode.id" :label="mode.name">{{ mode.name }}/{{ mode.suitable_people }}</el-checkbox></el-checkbox-group></el-form-item>
      <el-form-item label="发射器租赁">
        <div class="invitee-panel">
          <el-button @click="openLauncherPicker">发射器租赁设置</el-button>
          <div class="invitee-tags">
            <el-tag v-for="id in normalizedLauncherIds(activityForm)" :key="id" closable @close="removeLauncher(id)">{{ launcherLabel(id) }}</el-tag>
            <span v-if="!normalizedLauncherIds(activityForm).length" class="muted">暂未指定发射器，将展示报名人员上架发射器</span>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer><el-button @click="activityForm = null">取消</el-button><el-button type="primary" @click="saveActivity">保存</el-button></template>
  </el-dialog>

  <el-dialog v-model="planVisible" title="活动策划" width="760px">
    <el-form v-if="planForm" label-width="130px">
      <el-form-item label="Banner图">
        <img v-if="planForm.banner_url" class="banner-preview" :src="planForm.banner_url" />
        <div v-else class="banner-empty">建议上传策划横幅图，将展示在 H5 首页和投票详情页</div>
        <div class="banner-upload-row">
          <el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="r => planForm.banner_url = r.data.url"><el-button>上传Banner</el-button></el-upload>
          <el-button v-if="planForm.banner_url" @click="planForm.banner_url = ''">清除</el-button>
        </div>
      </el-form-item>
      <el-form-item label="活动名称"><el-input v-model="planForm.name" /></el-form-item>
      <el-form-item label="投票截止"><el-date-picker v-model="planForm.vote_deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item>
      <el-form-item label="可选日期">
        <div class="plan-date-list">
          <div v-for="(item, i) in planForm.dates" :key="i" class="plan-date-row">
            <el-date-picker v-model="item.date" value-format="YYYY-MM-DD" />
            <el-input v-model="item.remark" maxlength="200" placeholder="备注" style="width: 220px" />
            <el-button @click="planForm.dates.splice(i, 1)">删除</el-button>
          </div>
          <el-button @click="planForm.dates.push({ date: '', remark: '' })">+</el-button>
        </div>
      </el-form-item>
      <el-form-item label="可选场地"><el-select v-model="planForm.venue_ids" multiple><el-option v-for="venue in venues" :key="venue.id" :label="venue.name" :value="venue.id" /></el-select></el-form-item>
      <el-form-item label="指定游戏模式"><el-select v-model="planForm.game_mode_ids" multiple><el-option v-for="mode in modes" :key="mode.id" :label="mode.name" :value="mode.id" /></el-select></el-form-item>
      <el-form-item label="可见范围"><el-select v-model="planForm.visibility_type"><el-option label="所有人公开" value="all" /><el-option label="仅正式队员" value="official" /><el-option label="正式队员和邀请队员" value="official_plus_invite" /></el-select></el-form-item>
      <el-form-item v-if="planForm.visibility_type === 'official_plus_invite'" label="邀请人员">
        <div class="invitee-panel">
          <el-button @click="openInvitePicker('plan')">选择邀请人员</el-button>
          <div class="invitee-tags">
            <el-tag v-for="id in normalizedInviteeIds(planForm)" :key="id" closable @close="removeInvitee(planForm, id)">{{ inviteeLabel(id) }}</el-tag>
            <span v-if="!normalizedInviteeIds(planForm).length" class="muted">暂未选择邀请人员</span>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer><el-button @click="planForm = null">取消</el-button><el-button type="primary" @click="savePlan">保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="invitePickerVisible" title="选择邀请人员" width="560px">
    <el-checkbox-group v-model="invitePickerSelected" class="invitee-option-list">
      <el-checkbox v-for="user in nonFormalUsers" :key="user.id" :label="String(user.id)">
        {{ user.callsign || user.username }}<span class="muted" v-if="user.username"> / {{ user.username }}</span>
      </el-checkbox>
    </el-checkbox-group>
    <div v-if="!nonFormalUsers.length" class="muted">暂无非正式队员</div>
    <template #footer>
      <el-button @click="invitePickerVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmInvitePicker">确认</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="launcherPickerVisible" title="发射器租赁设置" width="760px">
    <div class="toolbar">
      <el-input v-model="launcherFilters.name" placeholder="发射器名称" style="width: 180px" />
      <el-input v-model="launcherFilters.callsign" placeholder="所有人呼号" style="width: 180px" />
      <el-button @click="loadLauncherOptions">搜索</el-button>
    </div>
    <el-table :data="launcherOptions" border height="420">
      <el-table-column label="选择" width="80">
        <template #default="{ row }">
          <el-checkbox :model-value="launcherPickerSelected.includes(String(row.id))" @change="toggleLauncher(row.id)" />
        </template>
      </el-table-column>
      <el-table-column label="图片" width="110">
        <template #default="{ row }"><img v-if="row.photo_filename" class="thumb" :src="row.photo_filename" /><span v-else class="muted">无图片</span></template>
      </el-table-column>
      <el-table-column prop="name" label="发射器名称" />
      <el-table-column label="所有人"><template #default="{ row }">{{ row.owner_callsign || row.owner_name || '-' }}</template></el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="launcherPickerVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmLauncherPicker">保存</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="convertPickerVisible" :title="convertPickerTitle" width="520px">
    <el-select v-model="convertPickerValue" filterable style="width: 100%">
      <el-option v-for="item in convertPickerOptions" :key="item.value" :label="item.label" :value="item.value" />
    </el-select>
    <template #footer>
      <el-button @click="cancelConvertPicker">取消</el-button>
      <el-button type="primary" @click="confirmConvertPicker">确认</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="eventVisible" title="历史活动"><el-form label-width="90px"><el-form-item label="活动名称"><el-input v-model="editEvent.name" /></el-form-item><el-form-item label="日期"><el-date-picker v-model="editEvent.event_date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="场地"><el-input v-model="editEvent.location" /></el-form-item><el-form-item label="组织人"><el-input v-model="editEvent.organizer" /></el-form-item><el-form-item label="地区"><el-select v-model="editEvent.activity_region"><el-option label="宁波" value="宁波" /><el-option label="外地" value="外地" /></el-select></el-form-item></el-form><template #footer><el-button @click="editEvent = null">取消</el-button><el-button type="primary" @click="saveEvent">保存</el-button></template></el-dialog>
</template>

<script>
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, setToken, token } from './api'

const jobs = ['突击兵', '支援兵', '医疗兵', '狙击手', '弹药兵', '填线兵']
const activityStatuses = ['报名中', '活动开始', '活动结束', '活动取消', '投票中', '已生成活动']

const ActivityForm = {
  props: ['modelValue', 'modes', 'uploadHeaders'],
  emits: ['update:modelValue', 'save', 'cancel'],
  computed: {
    form: {
      get() { return this.modelValue },
      set(value) { this.$emit('update:modelValue', value) }
    }
  },
  data() { return { jobs } },
  template: `<el-form label-width="130px">
    <el-form-item label="Banner图">
      <div class="banner-upload-row">
        <el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="r=>form.banner_url=r.data.url"><el-button>上传Banner</el-button></el-upload>
        <el-button v-if="form.banner_url" @click="form.banner_url=''">清除</el-button>
      </div>
      <img v-if="form.banner_url" class="banner-preview" :src="form.banner_url">
      <div v-else class="banner-empty">建议上传活动横幅图，将展示在 H5 首页和活动详情页</div>
    </el-form-item>
    <el-form-item label="活动名称"><el-input v-model="form.name"/></el-form-item>
    <el-form-item label="活动类型"><el-select v-model="form.activity_type"><el-option label="周常" value="周常"/><el-option label="本地活动" value="本地活动"/><el-option label="外地活动" value="外地活动"/></el-select></el-form-item>
    <el-form-item label="地点"><el-input v-model="form.location"/></el-form-item>
    <el-form-item label="时间"><el-date-picker v-model="form.start_at" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"/><el-date-picker v-model="form.end_at" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"/></el-form-item>
    <el-form-item label="人数配置"><div class="number-config-grid"><label><span>开启人数</span><el-input-number v-model="form.open_min" :min="0"/></label><label><span>阵营数</span><el-input-number v-model="form.camp_count" :min="1"/></label><label><span>阵营人数上限</span><el-input-number v-model="form.camp_limit" :min="0"/></label><label><span>每阵营小队数</span><el-input-number v-model="form.squad_count" :min="1"/></label><label><span>小队人数上限</span><el-input-number v-model="form.squad_limit" :min="0"/></label></div></el-form-item>
    <el-form-item label="地区"><el-select v-model="form.activity_region"><el-option label="宁波" value="宁波"/><el-option label="外地" value="外地"/></el-select></el-form-item>
    <el-form-item label="可见范围"><el-select v-model="form.visibility_type"><el-option label="所有人公开" value="all"/><el-option label="仅正式队员" value="official"/><el-option label="正式队员和邀请队员" value="official_plus_invite"/></el-select></el-form-item>
    <el-form-item label="开放职业"><el-checkbox-group v-model="form.allowed_jobs"><el-checkbox v-for="job in jobs" :key="job" :label="job"/></el-checkbox-group></el-form-item>
    <el-form-item label="游戏模式"><el-checkbox-group v-model="form.game_modes"><el-checkbox v-for="mode in modes" :key="mode.id" :label="mode.name">{{mode.name}}/{{mode.suitable_people}}</el-checkbox></el-checkbox-group></el-form-item>
    <el-form-item><el-button @click="$emit('cancel')">取消</el-button><el-button type="primary" @click="$emit('save')">保存</el-button></el-form-item>
  </el-form>`
}

const PlanForm = {
  props: ['modelValue', 'venues', 'modes', 'uploadHeaders'],
  emits: ['update:modelValue', 'save', 'cancel'],
  computed: {
    form: {
      get() { return this.modelValue },
      set(value) { this.$emit('update:modelValue', value) }
    }
  },
  template: `<el-form label-width="130px">
    <el-form-item label="Banner图">
      <div class="banner-upload-row">
        <el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="r=>form.banner_url=r.data.url"><el-button>上传Banner</el-button></el-upload>
        <el-button v-if="form.banner_url" @click="form.banner_url=''">清除</el-button>
      </div>
      <img v-if="form.banner_url" class="banner-preview" :src="form.banner_url">
      <div v-else class="banner-empty">建议上传策划横幅图，将展示在 H5 首页和投票详情页</div>
    </el-form-item>
    <el-form-item label="活动名称"><el-input v-model="form.name"/></el-form-item>
    <el-form-item label="投票截止"><el-date-picker v-model="form.vote_deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"/></el-form-item>
    <el-form-item label="可选日期"><div><div v-for="(_,i) in form.dates" :key="i"><el-date-picker v-model="form.dates[i]" value-format="YYYY-MM-DD"/><el-button @click="form.dates.splice(i,1)">删除</el-button></div><el-button @click="form.dates.push('')">+</el-button></div></el-form-item>
    <el-form-item label="可选场地"><el-select v-model="form.venue_ids" multiple><el-option v-for="venue in venues" :key="venue.id" :label="venue.name" :value="venue.id"/></el-select></el-form-item>
    <el-form-item label="指定游戏模式"><el-select v-model="form.game_mode_ids" multiple><el-option v-for="mode in modes" :key="mode.id" :label="mode.name" :value="mode.id"/></el-select></el-form-item>
    <el-form-item label="可见范围"><el-select v-model="form.visibility_type"><el-option label="所有人公开" value="all"/><el-option label="仅正式队员" value="official"/><el-option label="正式队员和邀请队员" value="official_plus_invite"/></el-select></el-form-item>
    <el-form-item><el-button @click="$emit('cancel')">取消</el-button><el-button type="primary" @click="$emit('save')">保存</el-button></el-form-item>
  </el-form>`
}

export default {
  components: { ActivityForm, PlanForm },
  data() {
    return {
	      tokenValue: token(),
	      me: {},
	      loginForm: {},
	      jobs,
	      activityStatuses,
	      active: 'dashboard',
      systemActive: 'systemImages',
	      filters: {},
      dashboardYear: String(new Date().getFullYear()),
      dashboardOverview: { cards: {}, monthly: {}, rankings: {}, recent_activities: [] },
      dashboardCharts: {},
	      activities: [],
	      venues: [],
	      modes: [],
	      users: [],
	      nonFormalUsers: [],
	      invitePickerVisible: false,
	      invitePickerTarget: '',
	      invitePickerSelected: [],
	      launcherOptions: [],
	      launcherPickerVisible: false,
	      launcherPickerSelected: [],
	      launcherFilters: {},
      launchers: [],
      launcherManageFilters: {},
      editLauncher: null,
      systemImages: {},
      extractionTab: 'matches',
      extraction: { items: [], shop: [], seasons: [], classes: [], weapons: [], matches: [], users: [], reset_records: [] },
      extractionEntry: { quantity: 1 },
      editExtractionItem: null,
      editExtractionShop: null,
      editExtractionSeason: null,
      editExtractionMatch: null,
      extractionMatchDetail: {},
      extractionAssets: {},
      assetUserId: '',
      resetConfirmText: '',
	      convertPickerVisible: false,
	      convertPickerTitle: '',
	      convertPickerOptions: [],
	      convertPickerValue: '',
	      convertPickerResolve: null,
	      roles: [],
      editVenue: null,
      editMode: null,
      editUser: null,
      activityForm: null,
      planForm: null,
      editEvent: null,
      summary: {},
      attendanceYear: String(new Date().getFullYear()),
      attendanceEvents: [],
      attendanceUsers: [],
      attendanceRecords: [],
      permissionPages: [],
      permissionRole: 'user',
      rolePerms: [],
      allActions: ['view', 'create', 'update', 'delete', 'cancel', 'restore', 'disable', 'resetPassword', 'export'],
      permissionTreeProps: { label: 'label', children: 'children' }
    }
  },
  computed: {
    menus() {
      const topItems = [
        { key: 'dashboard', name: '数据看板' },
        { key: 'activities', name: '活动管理', permission: 'activity:view' },
        { key: 'venues', name: '场地管理', permission: 'venue:view' },
        { key: 'modes', name: '模式管理', permission: 'gameMode:view' },
        { key: 'users', name: '用户管理', permission: 'user:view' },
        { key: 'attendance', name: '出勤统计', permission: 'attendance:view' },
        { key: 'launchers', name: '发射器管理', permission: 'launcher:view' },
        { key: 'extraction', name: '逃离管理', permission: 'extraction:view' }
      ].filter(item => !item.permission || this.can(item.permission))
      if (this.systemMenus.length) topItems.push({ key: 'system', name: '系统管理' })
      return topItems
    },
    systemMenus() {
      return [
        { key: 'systemImages', name: '图片管理', permission: 'systemImage:view' },
        { key: 'permissions', name: '权限管理', permission: 'permission:view' }
      ].filter(item => this.can(item.permission))
    },
    currentMenu() {
      return this.menus.find(item => item.key === this.active) || null
    },
    dashboardCards() {
      const cards = this.dashboardOverview.cards || {}
      return [
        { key: 'user_total', label: '用户总数', value: this.formatNumber(cards.user_total) },
        { key: 'formal_member_total', label: '正式队员', value: this.formatNumber(cards.formal_member_total) },
        { key: 'activity_total', label: '今年活动', value: this.formatNumber(cards.activity_total) },
        { key: 'upcoming_activity_total', label: '待开始活动', value: this.formatNumber(cards.upcoming_activity_total) },
        { key: 'enroll_total', label: '报名总数', value: this.formatNumber(cards.enroll_total) },
        { key: 'checkin_total', label: '签到总数', value: this.formatNumber(cards.checkin_total) },
        { key: 'checkin_rate', label: '签到率', value: `${cards.checkin_rate || 0}%` }
      ]
    },
    dashboardRankings() {
      return this.dashboardOverview.rankings || {}
    },
    attendanceTableMinWidth() {
      return `${260 + (this.attendanceEvents || []).length * 190}px`
    },
    permissionTreeData() {
      const actionNames = {
        view: '查看',
        create: '新增',
        update: '修改',
        delete: '删除',
        cancel: '取消',
        restore: '恢复',
        disable: '禁用',
        resetPassword: '重置密码',
        export: '导出',
        item: '物品',
        shop: '商店',
        season: '赛季',
        match: '对局',
        asset: '资产',
        reset: '重置',
        manage: '管理'
      }
      return this.permissionPages.map(page => ({
        id: page.key,
        label: page.name,
        children: (page.actions || []).map(action => ({
          id: `${page.key}:${action}`,
          label: actionNames[action] || action
        }))
      }))
    },
    uploadHeaders() {
      return { Authorization: `Bearer ${this.tokenValue}` }
    },
    venueVisible: { get() { return !!this.editVenue }, set(v) { if (!v) this.editVenue = null } },
    modeVisible: { get() { return !!this.editMode }, set(v) { if (!v) this.editMode = null } },
    userVisible: { get() { return !!this.editUser }, set(v) { if (!v) this.editUser = null } },
    launcherVisible: { get() { return !!this.editLauncher }, set(v) { if (!v) this.editLauncher = null } },
    extractionItemVisible: { get() { return !!this.editExtractionItem }, set(v) { if (!v) this.editExtractionItem = null } },
    extractionShopVisible: { get() { return !!this.editExtractionShop }, set(v) { if (!v) this.editExtractionShop = null } },
    extractionSeasonVisible: { get() { return !!this.editExtractionSeason }, set(v) { if (!v) this.editExtractionSeason = null } },
    extractionMatchVisible: { get() { return !!this.editExtractionMatch }, set(v) { if (!v) this.editExtractionMatch = null } },
    extractionMatchDetailVisible: { get() { return !!this.extractionMatchDetail.match }, set(v) { if (!v) this.extractionMatchDetail = {} } },
    activityVisible: { get() { return !!this.activityForm }, set(v) { if (!v) this.activityForm = null } },
    planVisible: { get() { return !!this.planForm }, set(v) { if (!v) this.planForm = null } },
    eventVisible: { get() { return !!this.editEvent }, set(v) { if (!v) this.editEvent = null } }
  },
  async mounted() {
    if (this.tokenValue) await this.init()
    window.addEventListener('resize', this.resizeDashboardCharts)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.resizeDashboardCharts)
    Object.values(this.dashboardCharts || {}).forEach(chart => chart && chart.dispose())
  },
  watch: {
    dashboardYear() {
      if (this.tokenValue && this.active === 'dashboard') this.loadDashboard()
    }
  },
  methods: {
    can(permission) {
      return !this.me.permissions || this.me.permissions.includes(permission)
    },
    async login() {
      const data = await api('/api/admin/auth/login', { method: 'POST', body: this.loginForm })
      setToken(data.token)
      this.tokenValue = data.token
      await this.init()
    },
    async init() {
      this.me = await api('/api/admin/auth/me')
      this.roles = await api('/api/admin/roles/options')
      await Promise.all([this.loadModes(), this.loadVenues(), this.load()])
    },
    load() {
      return ({ dashboard: this.loadDashboard, activities: this.loadActivities, venues: this.loadVenues, modes: this.loadModes, users: this.loadUsers, attendance: this.loadAttendance, launchers: this.loadLaunchers, extraction: this.loadExtraction, system: this.loadSystem }[this.active] || this.loadActivities)()
    },
    loadSystem() {
      if (!this.systemMenus.some(item => item.key === this.systemActive)) {
        this.systemActive = this.systemMenus[0]?.key || ''
      }
      if (this.systemActive === 'permissions') return this.loadPermissions()
      if (this.systemActive === 'systemImages') return this.loadSystemImages()
      return Promise.resolve()
    },
    async loadExtraction() {
      this.extraction = await api('/api/admin/extraction/bootstrap')
      if (!this.assetUserId && this.extraction.users?.length) this.assetUserId = this.extraction.users[0].id
      if (this.extractionTab === 'assets' && this.assetUserId) await this.loadExtractionAssets()
    },
    async loadExtractionAssets() {
      if (!this.assetUserId) return
      this.extractionAssets = await api(`/api/admin/extraction/assets?userId=${this.assetUserId}`)
    },
    extractionStatus(status) {
      return ({ preparing: '整备中', started: '进行中', ended: '已结束', cancelled: '已取消' })[status] || status || ''
    },
    shopCategoryName(value) {
      return ({ storage: '仓库扩充', item: '常规物品', weapon: '武器', money: '钱币' })[value] || value || '-'
    },
    weaponTypeName(value) {
      return ({ knife: '刀', regular: '常规武器', special: '特殊武器' })[value] || value || '-'
    },
    switchSystemTab(key) {
      this.systemActive = key
      return this.loadSystem()
    },
    async loadDashboard() {
      const params = new URLSearchParams()
      if (this.dashboardYear) params.set('year', this.dashboardYear)
      this.dashboardOverview = await api(`/api/admin/dashboard/overview?${params.toString()}`)
      this.$nextTick(() => this.renderDashboardCharts())
    },
    async openExtractionMatch(row) {
      this.extractionMatchDetail = await api(`/api/admin/extraction/matches/${row.id}`)
    },
    saveExtractionItem() {
      const method = this.editExtractionItem.id ? 'PUT' : 'POST'
      const url = this.editExtractionItem.id ? `/api/admin/extraction/items/${this.editExtractionItem.id}` : '/api/admin/extraction/items'
      return api(url, { method, body: this.editExtractionItem }).then(() => {
        this.editExtractionItem = null
        this.loadExtraction()
      })
    },
    saveExtractionShop() {
      const method = this.editExtractionShop.id ? 'PUT' : 'POST'
      const url = this.editExtractionShop.id ? `/api/admin/extraction/shop/${this.editExtractionShop.id}` : '/api/admin/extraction/shop'
      return api(url, { method, body: this.editExtractionShop }).then(() => {
        this.editExtractionShop = null
        this.loadExtraction()
      })
    },
    saveExtractionSeason() {
      const method = this.editExtractionSeason.id ? 'PUT' : 'POST'
      const url = this.editExtractionSeason.id ? `/api/admin/extraction/seasons/${this.editExtractionSeason.id}` : '/api/admin/extraction/seasons'
      return api(url, { method, body: this.editExtractionSeason }).then(() => {
        this.editExtractionSeason = null
        this.loadExtraction()
      })
    },
    saveExtractionMatch() {
      const method = this.editExtractionMatch.id ? 'PUT' : 'POST'
      const url = this.editExtractionMatch.id ? `/api/admin/extraction/matches/${this.editExtractionMatch.id}` : '/api/admin/extraction/matches'
      return api(url, { method, body: this.editExtractionMatch }).then(() => {
        this.editExtractionMatch = null
        this.loadExtraction()
      })
    },
    saveExtractionClasses() {
      return api('/api/admin/extraction/classes', { method: 'PUT', body: { rules: this.extraction.classes } }).then(() => this.loadExtraction())
    },
    saveExtractionWeapons() {
      return api('/api/admin/extraction/weapons', { method: 'PUT', body: { rules: this.extraction.weapons } }).then(() => this.loadExtraction())
    },
    submitExtractionEntry() {
      return api('/api/admin/extraction/entry', { method: 'POST', body: this.extractionEntry }).then(() => {
        this.extractionEntry.quantity = 1
        ElMessage.success('已录入')
      })
    },
    saveExtractionBanner(response) {
      return api('/api/admin/extraction/banner', { method: 'PUT', body: { banner_filename: response.data.url } }).then(() => this.loadExtraction())
    },
    async startExtractionMatch(row) {
      await api(`/api/admin/extraction/matches/${row.id}/start`, { method: 'POST', body: {} })
      await this.loadExtraction()
    },
    async unlockExtractionParticipant(row) {
      await api(`/api/admin/extraction/matches/${this.extractionMatchDetail.match.id}/unlock/${row.id}`, { method: 'POST', body: {} })
      await this.openExtractionMatch(this.extractionMatchDetail.match)
    },
    async settleExtractionMatch() {
      const body = {
        participants: (this.extractionMatchDetail.participants || []).map(row => ({
          id: row.id,
          evacuated: !!row.evacuated,
          kills: row.kills || 0,
          earned_cash: row.earned_cash || 0,
          rewards: []
        }))
      }
      await api(`/api/admin/extraction/matches/${this.extractionMatchDetail.match.id}/settle`, { method: 'POST', body })
      await this.loadExtraction()
      await this.openExtractionMatch(this.extractionMatchDetail.match)
    },
    async resetExtraction(type) {
      await api(`/api/admin/extraction/reset/${type}`, { method: 'POST', body: { confirm_text: this.resetConfirmText } })
      await this.loadExtraction()
    },
    renderDashboardCharts() {
      if (this.active !== 'dashboard') return
      this.renderChart('monthly', this.$refs.monthlyChart, this.monthlyChartOption())
      this.renderChart('venue', this.$refs.venueChart, this.rankChartOption(this.dashboardRankings.popular_venues || [], 'name', 'count'))
      this.renderChart('mode', this.$refs.modeChart, this.rankChartOption(this.dashboardRankings.popular_game_modes || [], 'name', 'count'))
    },
    renderChart(key, el, option) {
      if (!el) return
      if (this.dashboardCharts[key]) this.dashboardCharts[key].dispose()
      const chart = echarts.init(el)
      chart.setOption(option)
      this.dashboardCharts[key] = chart
    },
    monthlyChartOption() {
      const monthly = this.dashboardOverview.monthly || {}
      const months = (monthly.months || []).map(month => `${month}月`)
      return {
        color: ['#2563eb', '#16a34a', '#f59e0b'],
        tooltip: { trigger: 'axis' },
        legend: { top: 0, data: ['活动', '报名', '签到'] },
        grid: { left: 36, right: 20, top: 44, bottom: 28 },
        xAxis: { type: 'category', data: months },
        yAxis: { type: 'value', minInterval: 1 },
        series: [
          { name: '活动', type: 'bar', data: monthly.activities || [] },
          { name: '报名', type: 'line', smooth: true, data: monthly.enrollments || [] },
          { name: '签到', type: 'line', smooth: true, data: monthly.checkins || [] }
        ]
      }
    },
    rankChartOption(rows, labelKey, valueKey) {
      const labels = rows.map(item => item[labelKey] || '-').reverse()
      const values = rows.map(item => Number(item[valueKey] || 0)).reverse()
      return {
        color: ['#2563eb'],
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 80, right: 20, top: 16, bottom: 24 },
        xAxis: { type: 'value', minInterval: 1 },
        yAxis: { type: 'category', data: labels },
        series: [{ type: 'bar', data: values, barWidth: 14 }]
      }
    },
    resizeDashboardCharts() {
      Object.values(this.dashboardCharts || {}).forEach(chart => chart && chart.resize())
    },
	    loadActivities() {
	      const params = new URLSearchParams({
	        name: this.filters.name || '',
	        recordType: this.filters.recordType || '',
	        status: this.filters.status || ''
	      })
	      return api(`/api/admin/activities?${params.toString()}`).then(d => { this.activities = d })
	    },
    loadVenues() { return api(`/api/admin/venues?name=${this.filters.name || ''}`).then(d => { this.venues = d }) },
    loadModes() { return api(`/api/admin/game-modes?name=${this.filters.name || ''}`).then(d => { this.modes = d }) },
    loadUsers() { return api(`/api/admin/users?keyword=${this.filters.keyword || ''}`).then(d => { this.users = d }) },
    loadLaunchers() {
      const params = new URLSearchParams({
        name: this.launcherManageFilters.name || '',
        user: this.launcherManageFilters.user || ''
      })
      return api(`/api/admin/launchers?${params.toString()}`).then(d => { this.launchers = d })
    },
    loadSystemImages() {
      return api('/api/admin/system/images').then(d => { this.systemImages = d || {} })
    },
    userRoleLabel(row) {
      if (row.role === 'superadmin') return '超级管理员'
      if (row.role === 'admin') return '超级管理员'
      if (row.role === 'activity_admin') return '活动管理员'
      if (row.role === 'attendance_admin') return '出勤管理员'
      if (row.role === 'guest') return '游客'
      if (row.role === 'user') return row.is_regular_member ? '普通用户' : '游客'
      return row.role || '-'
    },
	    async loadAttendance() {
	      const params = new URLSearchParams({
	        region: this.filters.region || '',
	        formalOnly: String(!!this.filters.formalOnly)
	      })
	      if (this.attendanceYear) params.set('year', this.attendanceYear)
	      this.summary = await api(`/api/admin/attendance/summary?${params.toString()}`)
	      const matrix = await api(`/api/admin/attendance/matrix?${params.toString()}`)
	      this.attendanceEvents = matrix.events
	      this.attendanceUsers = matrix.users
	      this.attendanceRecords = matrix.records
	    },
    async loadPermissions() {
      this.permissionPages = await api('/api/admin/permissions/pages')
      await this.loadRolePermissions()
    },
    async loadRolePermissions() {
      this.rolePerms = [...await api(`/api/admin/roles/${this.permissionRole}/permissions`)]
      this.$nextTick(() => {
        if (this.$refs.permissionTree) this.$refs.permissionTree.setCheckedKeys(this.rolePerms)
      })
    },
    savePermissions() {
      const tree = this.$refs.permissionTree
      const checked = tree ? [...tree.getCheckedKeys(), ...tree.getHalfCheckedKeys()] : this.rolePerms
      const permissions = checked.filter(key => String(key).includes(':'))
      return api(`/api/admin/roles/${this.permissionRole}/permissions`, { method: 'PUT', body: { permissions } }).then(() => {
        this.rolePerms = permissions
        ElMessage.success('已保存')
      })
    },
	    async openActivity(row) {
	      if (!row) {
	        this.activityForm = { banner_url: '', banner_source: 'venue', venue_id: null, activity_type: '周常', camp_count: 2, squad_count: 1, activity_region: '宁波', visibility_type: 'all', invitee_ids: [], launcher_ids: [], allowed_jobs: [...jobs], game_modes: [] }
	        return
	      }
	      const detail = await api(`/api/admin/activities/${row.id}`)
	      this.activityForm = {
	        ...detail,
	        banner_source: detail.banner_source || 'venue',
	        invitee_ids: this.parseIds(detail.invitee_ids),
	        launcher_ids: this.parseIds(detail.launcher_ids),
	        allowed_jobs: (detail.allowed_jobs || '').split(',').filter(Boolean),
	        game_modes: (detail.game_modes || '').split(',').filter(Boolean)
	      }
	      if (this.activityForm.invitee_ids.length) await this.loadNonFormalUsers()
	      if (this.activityForm.launcher_ids.length) await this.loadLauncherOptions()
	    },
    activityBannerPreview() {
      if (!this.activityForm) return ''
      if (this.activityForm.banner_source === 'custom' && this.activityForm.banner_url) return this.activityForm.banner_url
      const venue = this.venues.find(item => Number(item.id) === Number(this.activityForm.venue_id))
      return (venue && venue.image_url) || this.activityForm.banner_url || ''
    },
    setActivityBanner(response) {
      this.activityForm.banner_url = response.data.url
      this.activityForm.banner_source = 'custom'
    },
    clearActivityBanner() {
      this.activityForm.banner_url = ''
      this.activityForm.banner_source = 'venue'
    },
    venueLabel(venue) {
      return `${venue.name || ''}${venue.address ? ` / ${venue.address}` : ''}`
    },
    setActivityVenue(id) {
      if (!this.activityForm) return
      const venue = this.venues.find(item => Number(item.id) === Number(id))
      if (!venue) return
      this.activityForm.location = venue.address || venue.name
      if (this.activityForm.banner_source !== 'custom') this.activityForm.banner_source = 'venue'
    },
	    async openPlan(row) {
	      if (!row) {
	        this.planForm = { banner_url: '', visibility_type: 'all', invitee_ids: [], dates: [{ date: '', remark: '' }], venue_ids: [], game_mode_ids: [] }
	        return
	      }
	      const detail = await api(`/api/admin/activity-plans/${row.id}`)
	      this.planForm = {
	        ...detail,
	        invitee_ids: this.parseIds(detail.invitee_ids),
	        dates: (detail.dates || []).map(item => ({ date: item.date || '', remark: item.remark || '' })),
	        venue_ids: (detail.venues || []).map(item => item.id),
	        game_mode_ids: (detail.game_modes || []).map(item => item.id)
	      }
	      if (this.planForm.invitee_ids.length) await this.loadNonFormalUsers()
	    },
	    saveActivity() {
	      this.activityForm.invitee_ids = this.normalizedInviteeIds(this.activityForm)
	      this.activityForm.launcher_ids = this.normalizedLauncherIds(this.activityForm)
	      const method = this.activityForm.id ? 'PUT' : 'POST'
	      const url = `/api/admin/activities${this.activityForm.id ? `/${this.activityForm.id}` : ''}`
	      return api(url, { method, body: this.activityForm }).then(() => { this.activityForm = null; this.loadActivities() })
	    },
	    savePlan() {
	      this.planForm.invitee_ids = this.normalizedInviteeIds(this.planForm)
	      this.planForm.dates = (this.planForm.dates || []).map(item => typeof item === 'object' ? { date: item.date || '', remark: item.remark || '' } : { date: item, remark: '' }).filter(item => item.date)
	      const method = this.planForm.id ? 'PUT' : 'POST'
	      const url = `/api/admin/activity-plans${this.planForm.id ? `/${this.planForm.id}` : ''}`
	      return api(url, { method, body: this.planForm }).then(() => { this.planForm = null; this.loadActivities() })
	    },
	    parseIds(value) {
	      if (Array.isArray(value)) return value.map(item => String(item)).filter(Boolean)
	      return String(value || '').split(',').map(item => item.trim()).filter(Boolean)
	    },
	    normalizedInviteeIds(form) {
	      if (!form) return []
	      return this.parseIds(form.invitee_ids)
	    },
	    async loadNonFormalUsers() {
	      if (this.nonFormalUsers.length) return
	      this.nonFormalUsers = await api('/api/admin/users/non-formal/options')
	    },
	    async openInvitePicker(target) {
	      await this.loadNonFormalUsers()
	      this.invitePickerTarget = target
	      const form = target === 'activity' ? this.activityForm : this.planForm
	      this.invitePickerSelected = this.normalizedInviteeIds(form)
	      this.invitePickerVisible = true
	    },
	    confirmInvitePicker() {
	      const form = this.invitePickerTarget === 'activity' ? this.activityForm : this.planForm
	      if (form) form.invitee_ids = [...this.invitePickerSelected]
	      this.invitePickerVisible = false
	    },
	    removeInvitee(form, id) {
	      if (!form) return
	      form.invitee_ids = this.normalizedInviteeIds(form).filter(item => item !== String(id))
	    },
	    inviteeLabel(id) {
	      const user = this.nonFormalUsers.find(item => String(item.id) === String(id))
	      return user ? (user.callsign || user.username || `ID ${id}`) : `ID ${id}`
	    },
    normalizedLauncherIds(form) {
      if (!form) return []
      return this.parseIds(form.launcher_ids)
    },
    async loadLauncherOptions() {
      const params = new URLSearchParams({
        name: this.launcherFilters.name || '',
        callsign: this.launcherFilters.callsign || ''
      })
      const rows = await api(`/api/admin/launcher-rentals/options?${params.toString()}`)
      const known = new Map(this.launcherOptions.map(item => [String(item.id), item]))
      rows.forEach(item => known.set(String(item.id), item))
      this.launcherOptions = [...known.values()]
    },
    async openLauncherPicker() {
      await this.loadLauncherOptions()
      this.launcherPickerSelected = this.normalizedLauncherIds(this.activityForm)
      this.launcherPickerVisible = true
    },
    toggleLauncher(id) {
      const value = String(id)
      if (this.launcherPickerSelected.includes(value)) {
        this.launcherPickerSelected = this.launcherPickerSelected.filter(item => item !== value)
      } else {
        this.launcherPickerSelected.push(value)
      }
    },
    confirmLauncherPicker() {
      if (this.activityForm) this.activityForm.launcher_ids = [...this.launcherPickerSelected]
      this.launcherPickerVisible = false
    },
    removeLauncher(id) {
      if (!this.activityForm) return
      this.activityForm.launcher_ids = this.normalizedLauncherIds(this.activityForm).filter(item => item !== String(id))
    },
    launcherLabel(id) {
      const item = this.launcherOptions.find(row => String(row.id) === String(id))
      return item ? `${item.name || `ID ${id}`} / ${item.owner_callsign || item.owner_name || '-'}` : `ID ${id}`
    },
    async convertPlan(row) {
      const preview = await api(`/api/admin/activity-plans/${row.id}/convert-preview`)
      let dateOption = null
      let venueOption = null
      try {
        dateOption = await this.pickConvertOption('日期', preview.top_dates || [], option => {
          const remark = option.remark ? ` / ${option.remark}` : ''
          return `${option.date}${remark}（${option.vote_count || 0}票）`
        })
        venueOption = await this.pickConvertOption('场地', preview.top_venues || [], option => {
          const address = option.address ? ` / ${option.address}` : ''
          return `${option.name}${address}（${option.vote_count || 0}票）`
        })
      } catch (e) {
        return
      }
      const plan = preview.plan || row
      const date = (dateOption && dateOption.date) || this.datePart(plan.vote_deadline)
      const modeNames = (preview.game_modes || []).map(mode => mode.name).filter(Boolean)
      this.activityForm = {
        source_plan_id: plan.id,
        banner_url: '',
        banner_source: 'venue',
        venue_id: venueOption ? venueOption.id : null,
        name: plan.name,
        activity_type: '周常',
        start_at: date ? `${date} 10:00:00` : '',
        end_at: date ? `${date} 17:00:00` : '',
        location: venueOption ? (venueOption.address || venueOption.name || '') : '',
        open_min: 0,
        camp_count: 2,
        camp_limit: 0,
        squad_count: 1,
        squad_limit: 0,
        activity_region: '宁波',
        visibility_type: plan.visibility_type || 'all',
        invitee_ids: this.parseIds(plan.invitee_ids),
        launcher_ids: [],
        allowed_jobs: [...jobs],
        game_modes: modeNames
      }
      if (this.activityForm.invitee_ids.length) await this.loadNonFormalUsers()
    },
    async pickConvertOption(name, options, labeler) {
      if (!options.length) return null
      if (options.length === 1) return options[0]
      const value = await new Promise((resolve, reject) => {
        this.convertPickerTitle = `选择${name}`
        this.convertPickerOptions = options.map((item, index) => ({ value: String(index), label: labeler(item) }))
        this.convertPickerValue = '0'
        this.convertPickerResolve = { resolve, reject }
        this.convertPickerVisible = true
      })
      return options[Number(value)]
    },
    confirmConvertPicker() {
      if (this.convertPickerResolve) this.convertPickerResolve.resolve(this.convertPickerValue)
      this.convertPickerVisible = false
      this.convertPickerResolve = null
    },
    cancelConvertPicker() {
      if (this.convertPickerResolve) this.convertPickerResolve.reject(new Error('cancelled'))
      this.convertPickerVisible = false
      this.convertPickerResolve = null
    },
    datePart(value) {
      return value ? String(value).replace('T', ' ').slice(0, 10) : ''
    },
    formatDateTime(value) {
      if (!value) return ''
      const text = String(value).replace('T', ' ')
      return text.length >= 19 ? text.slice(0, 19) : text
    },
    formatNumber(value) {
      return Number(value || 0).toLocaleString()
    },
    async downloadFile(url) {
      const response = await fetch(url, { headers: { Authorization: `Bearer ${token()}` } })
      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || '导出失败')
      }
      const blob = await response.blob()
      const disposition = response.headers.get('Content-Disposition') || ''
      const match = disposition.match(/filename\\*=UTF-8''([^;]+)/)
      const filename = match ? decodeURIComponent(match[1]) : '导出.xlsx'
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = filename
      link.click()
      URL.revokeObjectURL(link.href)
    },
    saveVenue() {
      return api(`/api/admin/venues${this.editVenue.id ? `/${this.editVenue.id}` : ''}`, { method: this.editVenue.id ? 'PUT' : 'POST', body: this.editVenue }).then(() => { this.editVenue = null; this.loadVenues() })
    },
    saveMode() {
      return api(`/api/admin/game-modes${this.editMode.id ? `/${this.editMode.id}` : ''}`, { method: this.editMode.id ? 'PUT' : 'POST', body: this.editMode }).then(() => { this.editMode = null; this.loadModes() })
    },
    saveUser() {
      return api(`/api/admin/users/${this.editUser.id}`, { method: 'PUT', body: this.editUser }).then(() => { this.editUser = null; this.loadUsers() })
    },
    saveLauncher() {
      return api(`/api/admin/launchers/${this.editLauncher.id}`, { method: 'PUT', body: this.editLauncher }).then(() => {
        this.editLauncher = null
        this.loadLaunchers()
      })
    },
    saveSystemImages() {
      return api('/api/admin/system/images', { method: 'PUT', body: this.systemImages }).then(() => {
        ElMessage.success('已保存')
      })
    },
    async saveExtractionItemImage(response) {
      if (this.editExtractionItem) this.editExtractionItem.photo_filename = response.data.url
    },
    async saveExtractionMatchItemImage(response) {
      if (this.editExtractionShop) this.editExtractionShop.photo_filename = response.data.url
    },
    async loadExtractionMatchDetail() {
      if (!this.extractionMatchDetail.match) return
      await this.openExtractionMatch(this.extractionMatchDetail.match)
    },
    weaponTypeName(value) {
      return ({ knife: '刀', regular: '常规武器', special: '特殊武器' })[value] || value || '-'
    },
    shopCategoryName(value) {
      return ({ storage: '仓库扩充', item: '常规物品', weapon: '武器', money: '钱币' })[value] || value || '-'
    },
    extractionStatus(status) {
      return ({ preparing: '整备中', started: '进行中', ended: '已结束', cancelled: '已取消' })[status] || status || ''
    },
    async saveExtractionItem() {
      const method = this.editExtractionItem.id ? 'PUT' : 'POST'
      const url = this.editExtractionItem.id ? `/api/admin/extraction/items/${this.editExtractionItem.id}` : '/api/admin/extraction/items'
      await api(url, { method, body: this.editExtractionItem })
      this.editExtractionItem = null
      await this.loadExtraction()
    },
    async saveExtractionShop() {
      const method = this.editExtractionShop.id ? 'PUT' : 'POST'
      const url = this.editExtractionShop.id ? `/api/admin/extraction/shop/${this.editExtractionShop.id}` : '/api/admin/extraction/shop'
      await api(url, { method, body: this.editExtractionShop })
      this.editExtractionShop = null
      await this.loadExtraction()
    },
    async saveExtractionSeason() {
      const method = this.editExtractionSeason.id ? 'PUT' : 'POST'
      const url = this.editExtractionSeason.id ? `/api/admin/extraction/seasons/${this.editExtractionSeason.id}` : '/api/admin/extraction/seasons'
      await api(url, { method, body: this.editExtractionSeason })
      this.editExtractionSeason = null
      await this.loadExtraction()
    },
    async saveExtractionMatch() {
      const method = this.editExtractionMatch.id ? 'PUT' : 'POST'
      const url = this.editExtractionMatch.id ? `/api/admin/extraction/matches/${this.editExtractionMatch.id}` : '/api/admin/extraction/matches'
      await api(url, { method, body: this.editExtractionMatch })
      this.editExtractionMatch = null
      await this.loadExtraction()
    },
    async saveExtractionClasses() {
      await api('/api/admin/extraction/classes', { method: 'PUT', body: { rules: this.extraction.classes } })
      await this.loadExtraction()
    },
    async saveExtractionWeapons() {
      await api('/api/admin/extraction/weapons', { method: 'PUT', body: { rules: this.extraction.weapons } })
      await this.loadExtraction()
    },
    async submitExtractionEntry() {
      await api('/api/admin/extraction/entry', { method: 'POST', body: this.extractionEntry })
      this.extractionEntry = { quantity: 1 }
      ElMessage.success('已录入')
    },
    async saveExtractionBanner(response) {
      await api('/api/admin/extraction/banner', { method: 'PUT', body: { banner_filename: response.data.url } })
      await this.loadExtraction()
    },
    async startExtractionMatch(row) {
      await api(`/api/admin/extraction/matches/${row.id}/start`, { method: 'POST', body: {} })
      await this.loadExtraction()
    },
    async unlockExtractionParticipant(row) {
      await api(`/api/admin/extraction/matches/${this.extractionMatchDetail.match.id}/unlock/${row.id}`, { method: 'POST', body: {} })
      await this.loadExtractionMatchDetail()
    },
    async settleExtractionMatch() {
      const body = {
        participants: (this.extractionMatchDetail.participants || []).map(row => ({
          id: row.id,
          evacuated: !!row.evacuated,
          kills: row.kills || 0,
          earned_cash: row.earned_cash || 0,
          rewards: []
        }))
      }
      await api(`/api/admin/extraction/matches/${this.extractionMatchDetail.match.id}/settle`, { method: 'POST', body })
      await this.loadExtraction()
      await this.loadExtractionMatchDetail()
    },
    async resetExtraction(type) {
      await api(`/api/admin/extraction/reset/${type}`, { method: 'POST', body: { confirm_text: this.resetConfirmText } })
      await this.loadExtraction()
    },
    resetUserPassword(row) {
      return api(`/api/admin/users/${row.id}/reset-password`, { method: 'PUT', body: {} }).then(() => {
        ElMessage.success('已经将密码置为初始密码 nb123456')
        this.loadUsers()
      })
    },
    saveEvent() {
      return api(`/api/admin/attendance/history-activities${this.editEvent.id ? `/${this.editEvent.id}` : ''}`, { method: this.editEvent.id ? 'PUT' : 'POST', body: this.editEvent }).then(() => { this.editEvent = null; this.loadAttendance() })
    },
    deleteAttendanceEvent(event) {
      return this.remove(`/api/admin/attendance/history-activities/${event.id}`, this.loadAttendance)
    },
    post(url, callback) { return api(url, { method: 'POST', body: {} }).then(() => callback && callback()) },
    remove(url, callback) {
      return ElMessageBox.confirm('确认删除？').then(() => api(url, { method: 'DELETE' }).then(() => callback && callback()))
    },
    isPresent(eventId, userId) {
      return this.attendanceRecords.some(r => r.event_id === eventId && r.user_id === userId && r.present)
    },
    countPresent(userId) {
      return this.attendanceRecords.filter(r => r.user_id === userId && r.present).length
    },
    eventPresentCount(eventId) {
      return this.attendanceRecords.filter(r => r.event_id === eventId && r.present).length
    },
    eventFormalPresentCount(eventId) {
      const formalIds = new Set(this.attendanceUsers.filter(u => !!u.is_regular_member).map(u => Number(u.id)))
      return this.attendanceRecords.filter(r => r.event_id === eventId && r.present && formalIds.has(Number(r.user_id))).length
    },
    togglePresent(eventId, userId) {
      if (!this.can('attendance:update')) return
      return api('/api/admin/attendance/records', { method: 'PUT', body: { event_id: eventId, user_id: userId, present: !this.isPresent(eventId, userId) } }).then(this.loadAttendance)
    }
  }
}
</script>
