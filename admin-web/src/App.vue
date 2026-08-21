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
      <div class="menu home-menu" @click="goSiteHome">← 回到首页</div>
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
      <div class="side-spacer"></div>
      <div class="menu logout-menu" @click="logout">退出登录</div>
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

      <EscapeAdmin v-if="active === 'escape'" :can="can" />
      <BatchAdmin v-if="active === 'batches'" :can="can" />

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
          <el-button v-if="fromH5Activity" @click="goH5ActivityDetail">返回活动详情页面</el-button>
        </div>
        <el-table :data="activities" border>
          <el-table-column prop="display_record_type" label="类型" width="110" />
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="activity_type" label="活动类型" width="110" />
          <el-table-column label="时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.start_at || row.vote_deadline || row.end_at) }}</template>
          </el-table-column>
          <el-table-column label="地点" min-width="120"><template #default="{ row }">{{ row.venue_name || row.location || '-' }}</template></el-table-column>
          <el-table-column label="报名">
            <template #default="{ row }">
              <span v-if="row.record_type === 'plan'">{{ row.voter_count || row.enroll_count || 0 }}</span>
              <span v-else>{{ row.enroll_count || 0 }} / {{ row.signup_limit || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="checkin_count" label="签到" />
          <el-table-column prop="display_status" label="状态" />
          <el-table-column label="操作" width="520">
            <template #default="{ row }">
              <el-button size="small" @click="row.record_type === 'plan' ? openPlan(row) : openActivity(row)">查看/编辑</el-button>
              <template v-if="row.record_type === 'activity'">
                <el-button size="small" v-if="!row.deleted_at && can('activity:cancel')" @click="put(`/api/admin/activities/${row.id}/cancel`, loadActivities)">取消</el-button>
                <el-button size="small" v-if="row.deleted_at && can('activity:restore')" @click="put(`/api/admin/activities/${row.id}/restore`, loadActivities)">恢复</el-button>
                <el-button size="small" @click="downloadFile(`/api/admin/activities/${row.id}/enrollments/export`)">导出报名表</el-button>
                <el-button size="small" @click="downloadFile(`/api/admin/activities/${row.id}/launcher-rentals/export`)">导出租赁表</el-button>
                <el-button size="small" type="danger" v-if="can('activity:delete')" @click="remove(`/api/admin/activities/${row.id}`, loadActivities)">删除</el-button>
              </template>
              <template v-else>
                <el-button size="small" v-if="canViewPlanVotes(row)" @click="openPlanVotes(row)">投票详情</el-button>
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
          <el-button type="primary" v-if="can('attendance:create')" @click="openAttendanceEvent()">手动增加历史活动</el-button>
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
            <p>实心绿点表示已签到，空心绿点表示已报名但未签到。活动横向显示：名称 / 日期 / 场地 / 组织人。</p>
          </div>
          <div class="attendance-table-scroll">
            <el-table class="attendance-matrix" :data="attendanceUsers" border height="560">
              <el-table-column prop="callsign" label="人员" fixed="left" width="112" />
              <el-table-column label="出勤次数" fixed="left" width="50"><template #default="{ row }">{{ countPresent(row.id) }}</template></el-table-column>
              <el-table-column v-for="event in attendanceEvents" :key="event.id" width="105">
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
                      <el-button size="small" v-if="can('attendance:update')" @click.stop="openAttendanceEvent(event)">编辑</el-button>
                      <el-button size="small" type="danger" v-if="event.is_manual ? can('attendance:delete') : can('activity:delete')" @click.stop="deleteAttendanceEvent(event)">删除</el-button>
                    </div>
                  </div>
                </template>
                <template #default="{ row }">
                  <button class="attendance-dot" :class="{ present: isPresent(event.id, row.id), enrolled: isEnrolled(event.id, row.id) && !isPresent(event.id, row.id) }" :disabled="!can('attendance:update')" @click="togglePresent(event.id, row.id)">
                    {{ isPresent(event.id, row.id) ? '●' : isEnrolled(event.id, row.id) ? '○' : '' }}
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
              <el-upload action="/api/admin/files/upload" accept="image/*" :http-request="uploadAdminFile" :show-file-list="false" :on-success="r => systemImages.login_background_url = r.data.url" :on-error="handleUploadError"><el-button>上传背景图</el-button></el-upload>
              <el-button v-if="systemImages.login_background_url" @click="systemImages.login_background_url = ''">清除</el-button>
            </div>
          </div>
          <div class="settings-panel">
            <h3>标题 Logo</h3>
            <img v-if="systemImages.login_logo_url" class="settings-logo-preview" :src="systemImages.login_logo_url" />
            <div v-else class="settings-empty settings-logo-empty">使用默认 Logo</div>
            <div class="banner-upload-row">
              <el-upload action="/api/admin/files/upload" accept="image/*" :http-request="uploadAdminFile" :show-file-list="false" :on-success="r => systemImages.login_logo_url = r.data.url" :on-error="handleUploadError"><el-button>上传Logo</el-button></el-upload>
              <el-button v-if="systemImages.login_logo_url" @click="systemImages.login_logo_url = ''">清除</el-button>
            </div>
          </div>
        </div>
        <el-button type="primary" v-if="can('systemImage:update')" @click="saveSystemImages">保存设置</el-button>

        <div class="homepage-carousel-admin">
          <div class="homepage-carousel-heading">
            <div>
              <h2>官网首页轮播图</h2>
              <p class="muted">每个板块至少保留一张启用图片；官网每 5 秒切换一次。</p>
            </div>
          </div>
          <div class="homepage-carousel-sections">
            <section v-for="section in homepageSections" :key="section.key" class="homepage-carousel-section">
              <div class="homepage-carousel-section-head">
                <div>
                  <h3>{{ section.name }}</h3>
                  <span>{{ section.description }}</span>
                </div>
                <el-upload
                  v-if="can('systemImage:update')"
                  action="/api/admin/files/upload"
                  accept="image/*"
                  :http-request="uploadAdminFile"
                  :show-file-list="false"
                  :on-success="response => uploadHomepageCarousel(section.key, response)"
                  :on-error="handleUploadError"
                >
                  <el-button type="primary" size="small">上传图片</el-button>
                </el-upload>
              </div>
              <div v-if="homepageImagesFor(section.key).length" class="homepage-carousel-list">
                <article v-for="(image, index) in homepageImagesFor(section.key)" :key="image.id" class="homepage-carousel-item">
                  <img :src="image.image_url" :alt="`${section.name}轮播图${index + 1}`" />
                  <div class="homepage-carousel-meta">
                    <strong>顺序 {{ index + 1 }}</strong>
                    <el-tag size="small" :type="image.active ? 'success' : 'info'">{{ image.active ? '启用' : '停用' }}</el-tag>
                  </div>
                  <div v-if="can('systemImage:update')" class="homepage-carousel-actions">
                    <el-button size="small" :disabled="index === 0" @click="moveHomepageCarousel(section.key, index, -1)">上移</el-button>
                    <el-button size="small" :disabled="index === homepageImagesFor(section.key).length - 1" @click="moveHomepageCarousel(section.key, index, 1)">下移</el-button>
                    <el-switch :model-value="image.active" active-text="启用" inactive-text="停用" @change="active => setHomepageCarouselActive(image, active)" />
                    <el-button size="small" type="danger" @click="deleteHomepageCarousel(image)">删除</el-button>
                  </div>
                </article>
              </div>
              <div v-else class="settings-empty homepage-carousel-empty">尚未配置，官网将使用内置图片</div>
            </section>
          </div>
        </div>
      </section>
    </main>
  </div>

  <el-dialog v-model="venueVisible" title="场地" width="720px" @opened="initVenueMap"><el-form v-if="editVenue" label-width="90px"><el-form-item label="场地图片"><div class="banner-upload-row"><el-upload action="/api/admin/files/upload" accept="image/*" :http-request="uploadVenueFile" :show-file-list="false" :on-success="r => editVenue.image_url = r.data.url" :on-error="handleUploadError"><el-button>上传图片</el-button></el-upload><el-button v-if="editVenue.image_url" @click="editVenue.image_url = ''">清除</el-button></div><p class="muted venue-upload-hint">超过300KB的图片会自动压缩后保存</p><img v-if="editVenue.image_url" class="venue-preview" :src="editVenue.image_url" /><div v-else class="venue-empty">建议上传场地实景图，可作为活动默认Banner</div></el-form-item><el-form-item label="场地名称"><el-input v-model="editVenue.name" /></el-form-item><el-form-item label="场地地址"><el-input v-model="editVenue.address"><template #append><el-button @click="locateVenueAddress">按地址定位</el-button></template></el-input></el-form-item><el-form-item label="地图选点"><div class="venue-map-wrap"><div id="venue-map" class="venue-map"></div><p class="muted">点击地图设置签到中心点；签到范围为该点周围 1 公里。</p></div></el-form-item><el-form-item label="经纬度"><div class="venue-coordinate-row"><el-input-number v-model="editVenue.longitude" :precision="7" :step="0.000001" placeholder="经度" /><el-input-number v-model="editVenue.latitude" :precision="7" :step="0.000001" placeholder="纬度" /></div></el-form-item></el-form><template #footer><el-button @click="editVenue = null">取消</el-button><el-button type="primary" @click="saveVenue">保存</el-button></template></el-dialog>
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
  <el-dialog v-model="activityVisible" title="活动" width="760px">
    <el-form v-if="activityForm" label-width="130px" :disabled="!!activityForm.id && !can('activity:update')">
      <el-form-item label="Banner图">
        <img v-if="activityBannerPreview()" class="banner-preview" :src="activityBannerPreview()" />
        <div v-else class="banner-empty">建议上传活动横幅图，将展示在 H5 首页和活动详情页</div>
        <div class="banner-upload-row">
          <el-upload action="/api/admin/files/upload" accept="image/*" :http-request="uploadAdminFile" :show-file-list="false" :on-success="setActivityBanner" :on-error="handleUploadError"><el-button>上传Banner</el-button></el-upload>
          <el-button v-if="activityBannerPreview()" @click="clearActivityBanner">清除</el-button>
        </div>
      </el-form-item>
      <el-form-item label="趣动小程序二维码">
        <img v-if="activityForm.external_miniapp_qr_url" class="banner-preview payment-qr-preview" :src="activityForm.external_miniapp_qr_url" alt="第三方小程序活动二维码" />
        <div class="banner-upload-row">
          <el-upload action="/api/admin/files/upload" accept="image/*" :http-request="uploadAdminFile" :show-file-list="false" :on-success="response => activityForm.external_miniapp_qr_url = response.data.url" :on-error="handleUploadError">
            <el-button>上传二维码</el-button>
          </el-upload>
          <el-button v-if="activityForm.external_miniapp_qr_url" @click="activityForm.external_miniapp_qr_url = ''">清除</el-button>
        </div>
        <p class="muted">配置后，H5 报名成功可选择前往趣动小程序，已报名用户也可通过“跳转趣动”按钮再次查看二维码。</p>
      </el-form-item>
      <el-form-item label="活动名称"><el-input v-model="activityForm.name" /></el-form-item>
      <el-form-item label="组织者">
        <el-select v-model="activityForm.organizer_ids" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择正式队员" style="width: 100%">
          <el-option v-for="user in formalUsers" :key="user.id" :label="user.callsign || user.username" :value="Number(user.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="活动类型"><el-select v-model="activityForm.activity_type"><el-option label="周常" value="周常" /><el-option label="接龙" value="接龙" /><el-option label="本地活动" value="本地活动" /><el-option label="外地活动" value="外地活动" /></el-select></el-form-item>
      <el-form-item label="场地">
        <el-select v-model="activityForm.venue_id" clearable filterable placeholder="选择场地" @change="setActivityVenue">
          <el-option v-for="venue in venues" :key="venue.id" :label="venueLabel(venue)" :value="venue.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="地点"><el-input v-model="activityForm.location" placeholder="选择场地后自动填入，也可手动修改" /></el-form-item>
      <el-form-item label="签到方式">
        <el-checkbox-group v-model="activityForm.checkin_methods">
          <el-checkbox label="location">定位签到</el-checkbox>
          <el-checkbox label="qr">二维码签到</el-checkbox>
        </el-checkbox-group>
        <p class="muted">至少选择一种；H5 签到入口会按这里的配置展示。</p>
      </el-form-item>
      <el-form-item label="提前签到">
        <div class="checkin-open-row">
          <el-input-number v-model="activityForm.checkin_open_value" :min="0" :step="1" step-strictly />
          <el-select v-model="activityForm.checkin_open_unit" style="width: 110px">
            <el-option label="小时" value="hour" />
            <el-option label="天" value="day" />
          </el-select>
          <span class="muted">0 表示活动开始时开放签到</span>
        </div>
      </el-form-item>
      <el-form-item label="时间" required>
        <el-date-picker v-model="activityForm.start_at" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :teleported="false" placement="bottom-start" />
        <el-date-picker v-model="activityForm.end_at" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :teleported="false" placement="bottom-start" />
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
            <span v-if="!normalizedLauncherIds(activityForm).length" class="muted">暂未指定发射器，H5 将不展示可租赁发射器</span>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer><el-button @click="activityForm = null">取消</el-button><el-button v-if="activityForm && (activityForm.id ? can('activity:update') : can('activity:create'))" type="primary" @click="saveActivity">保存</el-button></template>
  </el-dialog>

  <el-dialog v-model="planVisible" title="活动策划" width="760px">
    <el-form v-if="planForm" label-width="130px">
      <el-form-item label="Banner图">
        <img v-if="planForm.banner_url" class="banner-preview" :src="planForm.banner_url" />
        <div v-else class="banner-empty">建议上传策划横幅图，将展示在 H5 首页和投票详情页</div>
        <div class="banner-upload-row">
          <el-upload action="/api/admin/files/upload" accept="image/*" :http-request="uploadAdminFile" :show-file-list="false" :on-success="r => planForm.banner_url = r.data.url" :on-error="handleUploadError"><el-button>上传Banner</el-button></el-upload>
          <el-button v-if="planForm.banner_url" @click="planForm.banner_url = ''">清除</el-button>
        </div>
      </el-form-item>
      <el-form-item label="活动名称"><el-input v-model="planForm.name" /></el-form-item>
      <el-form-item label="组织者">
        <el-select v-model="planForm.organizer_ids" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择正式队员" style="width: 100%">
          <el-option v-for="user in formalUsers" :key="user.id" :label="user.callsign || user.username" :value="Number(user.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="投票截止"><el-date-picker v-model="planForm.vote_deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :teleported="false" placement="bottom-start" /></el-form-item>
      <el-form-item label="可选日期">
        <div class="plan-date-list">
          <div v-for="(item, i) in planForm.dates" :key="i" class="plan-date-row">
            <el-date-picker v-model="item.date" value-format="YYYY-MM-DD" :teleported="false" placement="bottom-start" />
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
  <el-dialog v-model="planVotesVisible" title="投票详情" width="920px">
    <div v-loading="planVotesLoading" class="plan-votes-dialog">
      <div class="plan-votes-head">
        <div>
          <h3>{{ planVotes.plan?.name || '-' }}</h3>
          <p class="muted">总投票人数：{{ planVotes.total_voters || 0 }}</p>
        </div>
        <el-tag type="success">正式队员已标注</el-tag>
      </div>
      <div v-for="section in planVotes.sections || []" :key="section.type" class="plan-vote-section">
        <h4>{{ section.title }}</h4>
        <div class="plan-vote-options">
          <div v-for="option in section.options || []" :key="`${section.type}-${option.id}`" class="plan-vote-option">
            <div class="plan-vote-option-head">
              <div>
                <strong>{{ option.label }}</strong>
                <p v-if="option.subtitle" class="muted">{{ option.subtitle }}</p>
              </div>
              <div class="plan-vote-counts">
                <el-tag>{{ option.vote_count || 0 }} 票</el-tag>
                <el-tag type="success">{{ option.formal_vote_count || 0 }} 正式</el-tag>
              </div>
            </div>
            <div v-if="(option.voters || []).length" class="plan-voter-list">
              <span v-for="voter in option.voters" :key="`${section.type}-${option.id}-${voter.id}`" class="plan-voter-pill" :class="{ formal: !!voter.is_regular_member }">
                {{ voterLabel(voter) }}
                <b v-if="!!voter.is_regular_member">正式</b>
              </span>
            </div>
            <p v-else class="muted">暂无投票</p>
          </div>
        </div>
      </div>
    </div>
    <template #footer><el-button @click="planVotesVisible = false">关闭</el-button></template>
  </el-dialog>
  <el-dialog v-model="invitePickerVisible" title="选择邀请人员" width="560px">
    <div class="invitee-search-row">
      <el-input v-model="invitePickerKeyword" clearable placeholder="搜索呼号、用户名或用户 ID" />
      <span class="muted">已选择 {{ invitePickerSelected.length }} 人</span>
    </div>
    <el-checkbox-group v-model="invitePickerSelected" class="invitee-option-list">
      <el-checkbox v-for="user in filteredNonFormalUsers" :key="user.id" :label="String(user.id)">
        {{ user.callsign || user.username }}<span class="muted" v-if="user.username"> / {{ user.username }}</span>
      </el-checkbox>
    </el-checkbox-group>
    <div v-if="!nonFormalUsers.length" class="muted">暂无非正式队员</div>
    <div v-else-if="!filteredNonFormalUsers.length" class="muted invitee-search-empty">未找到匹配人员</div>
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
  <el-dialog v-model="eventVisible" title="历史活动">
    <el-form v-if="editEvent" label-width="90px">
      <el-form-item label="活动名称"><el-input v-model="editEvent.name" /></el-form-item>
      <el-form-item label="日期"><el-date-picker v-model="editEvent.event_date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="场地"><el-input v-model="editEvent.location" /></el-form-item>
      <el-form-item label="组织人">
        <el-select v-model="editEvent.organizer_ids" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择正式队员" style="width: 100%">
          <el-option v-for="user in formalUsers" :key="user.id" :label="user.callsign || user.username" :value="Number(user.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="地区"><el-select v-model="editEvent.activity_region"><el-option label="宁波" value="宁波" /><el-option label="外地" value="外地" /></el-select></el-form-item>
    </el-form>
    <template #footer><el-button @click="editEvent = null">取消</el-button><el-button type="primary" @click="saveEvent">保存</el-button></template>
  </el-dialog>
</template>

<script>
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, setToken, token } from './api'
import defaultActivityBanner from './assets/activity-default.jpg'
import { compressImageFile, DEFAULT_MAX_BYTES } from './imageCompression'
import EscapeAdmin from './escape/EscapeAdmin.vue'
import BatchAdmin from './BatchAdmin.vue'

const jobs = ['突击兵', '支援兵', '医疗兵', '狙击手', '弹药兵', '填线兵']
const activityStatuses = ['报名中', '活动进行中', '活动结束', '活动取消', '投票中', '已生成活动']
const homepageSections = [
  { key: 'top', name: '首屏 Top', description: '包含首页主视觉和指标横条' },
  { key: 'about', name: '关于 About', description: '活动介绍与新玩家说明' },
  { key: 'records', name: '记录 Record', description: '历史活动与公开记录' },
  { key: 'fields', name: '场地 Fields', description: '场地类型与任务区域' },
  { key: 'activities', name: '活动 Ops', description: '周常、剧本与远征' },
  { key: 'safe', name: '保障 Safe', description: '安全规则与训练流程' },
  { key: 'media', name: '影像 Media', description: '照片、视频与公开来源' },
  { key: 'cooperate', name: '合作 Join', description: '合作方式与报名入口' }
]

async function uploadAdminFileRequest(options) {
  const body = new FormData()
  body.append(options.filename || 'file', options.file)
  const data = await api('/api/admin/files/upload', { method: 'POST', body })
  return { data }
}

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
  methods: { uploadAdminFile: options => uploadAdminFileRequest(options) },
  template: `<el-form label-width="130px">
    <el-form-item label="Banner图">
      <div class="banner-upload-row">
        <el-upload action="/api/admin/files/upload" accept="image/*" :http-request="uploadAdminFile" :show-file-list="false" :on-success="r=>form.banner_url=r.data.url"><el-button>上传Banner</el-button></el-upload>
        <el-button v-if="form.banner_url" @click="form.banner_url=''">清除</el-button>
      </div>
      <img v-if="form.banner_url" class="banner-preview" :src="form.banner_url">
      <div v-else class="banner-empty">建议上传活动横幅图，将展示在 H5 首页和活动详情页</div>
    </el-form-item>
    <el-form-item label="活动名称"><el-input v-model="form.name"/></el-form-item>
    <el-form-item label="活动类型"><el-select v-model="form.activity_type"><el-option label="周常" value="周常"/><el-option label="接龙" value="接龙"/><el-option label="本地活动" value="本地活动"/><el-option label="外地活动" value="外地活动"/></el-select></el-form-item>
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
  methods: { uploadAdminFile: options => uploadAdminFileRequest(options) },
  template: `<el-form label-width="130px">
    <el-form-item label="Banner图">
      <div class="banner-upload-row">
        <el-upload action="/api/admin/files/upload" accept="image/*" :http-request="uploadAdminFile" :show-file-list="false" :on-success="r=>form.banner_url=r.data.url"><el-button>上传Banner</el-button></el-upload>
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
  components: { ActivityForm, PlanForm, EscapeAdmin, BatchAdmin },
  data() {
    const query = new URLSearchParams(location.search)
    return {
	      tokenValue: token(),
	      me: {},
	      loginForm: {},
	      jobs,
	      activityStatuses,
      homepageSections,
      active: query.get('view') || 'dashboard',
      sourceActivityId: Number(query.get('activityId')) || null,
      enteredFromH5: query.get('from') === 'h5',
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
	      formalUsers: [],
	      invitePickerVisible: false,
	      invitePickerTarget: '',
	      invitePickerSelected: [],
	      invitePickerKeyword: '',
	      launcherOptions: [],
	      launcherPickerVisible: false,
	      launcherPickerSelected: [],
	      launcherFilters: {},
      launchers: [],
      launcherManageFilters: {},
      editLauncher: null,
      systemImages: {},
      homepageCarouselImages: [],
	      convertPickerVisible: false,
	      convertPickerTitle: '',
      convertPickerOptions: [],
      convertPickerValue: '',
      convertPickerResolve: null,
      planVotesVisible: false,
      planVotesLoading: false,
      planVotes: { plan: null, total_voters: 0, sections: [] },
	      roles: [],
      editVenue: null,
      venueMap: null,
      venueMarker: null,
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
      attendanceEnrollments: [],
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
        { key: 'escape', name: '逃离西撇镇', permission: 'escape:view' },
        { key: 'batches', name: '跑批管理', permission: 'escape:view' },
        { key: 'activities', name: '活动管理', permission: 'activity:view' },
        { key: 'venues', name: '场地管理', permission: 'venue:view' },
        { key: 'modes', name: '模式管理', permission: 'gameMode:view' },
        { key: 'users', name: '用户管理', permission: 'user:view' },
        { key: 'attendance', name: '出勤统计', permission: 'attendance:view' },
        { key: 'launchers', name: '发射器管理', permission: 'launcher:view' }
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
        export: '导出'
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
    filteredNonFormalUsers() {
      const keyword = String(this.invitePickerKeyword || '').trim().toLowerCase()
      if (!keyword) return this.nonFormalUsers
      return this.nonFormalUsers.filter(user => [user.id, user.callsign, user.username]
        .some(value => String(value || '').toLowerCase().includes(keyword)))
    },
    uploadHeaders() {
      return { Authorization: `Bearer ${this.tokenValue}` }
    },
    fromH5Activity() {
      return this.enteredFromH5 && !!this.sourceActivityId
    },
    venueVisible: { get() { return !!this.editVenue }, set(v) { if (!v) this.editVenue = null } },
    modeVisible: { get() { return !!this.editMode }, set(v) { if (!v) this.editMode = null } },
    userVisible: { get() { return !!this.editUser }, set(v) { if (!v) this.editUser = null } },
    launcherVisible: { get() { return !!this.editLauncher }, set(v) { if (!v) this.editLauncher = null } },
    activityVisible: { get() { return !!this.activityForm }, set(v) { if (!v) this.activityForm = null } },
    planVisible: { get() { return !!this.planForm }, set(v) { if (!v) this.planForm = null } },
    eventVisible: { get() { return !!this.editEvent }, set(v) { if (!v) this.editEvent = null } }
  },
  async mounted() {
    try {
      await this.init()
    } catch (error) {
      this.tokenValue = ''
      if (new URLSearchParams(location.search).get('view') === 'activities') this.redirectToH5Login()
    }
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
    uploadAdminFile(options) {
      return uploadAdminFileRequest(options)
    },
    async uploadVenueFile(options) {
      const originalSize = options.file.size
      const compressedFile = await compressImageFile(options.file)
      if (originalSize > DEFAULT_MAX_BYTES) {
        ElMessage.success(`图片已压缩至${Math.ceil(compressedFile.size / 1024)}KB`)
      }
      return uploadAdminFileRequest({ ...options, file: compressedFile })
    },
    handleUploadError(error) {
      ElMessage.error(error?.message || '图片上传失败，请稍后重试')
    },
    can(permission) {
      return !this.me.permissions || this.me.permissions.includes(permission)
    },
    isAdminRole() {
      return ['superadmin', 'admin', 'activity_admin'].includes(String(this.me.role || ''))
    },
    canViewPlanVotes(row) {
      if (!row || row.record_type !== 'plan') return false
      if (this.isAdminRole()) return true
      if (String(row.created_by_id || '') === String(this.me.id || '')) return true
      return this.parseIds(row.organizer_ids).includes(String(this.me.id || ''))
    },
    voterLabel(voter) {
      return voter.callsign || voter.username || `ID ${voter.id}`
    },
    async openPlanVotes(row) {
      this.planVotesVisible = true
      this.planVotesLoading = true
      this.planVotes = { plan: row, total_voters: 0, sections: [] }
      try {
        this.planVotes = await api(`/api/admin/activity-plans/${row.id}/votes`)
      } finally {
        this.planVotesLoading = false
      }
    },
    async login() {
      const data = await api('/api/admin/auth/login', { method: 'POST', body: this.loginForm })
      setToken(data.token)
      this.tokenValue = data.token
      await this.init()
    },
    async logout() {
      try {
        await ElMessageBox.confirm('确认退出后管系统？', '退出登录', {
          confirmButtonText: '退出',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch {
        return
      }
      try {
        await api('/api/auth/logout', { method: 'POST', body: {} })
      } finally {
        setToken('')
        this.tokenValue = ''
        this.me = {}
        this.loginForm = {}
        history.replaceState(null, '', location.pathname)
      }
    },
    async init() {
      this.me = await api('/api/admin/auth/me')
      this.tokenValue = token()
      if (!this.menus.some(item => item.key === this.active)) this.active = 'dashboard'
      this.roles = await api('/api/admin/roles/options')
      await Promise.all([this.loadModes(), this.loadVenues(), this.load()])
      if (this.active === 'activities' && this.sourceActivityId && this.can('activity:view')) {
        await this.openActivity({ id: this.sourceActivityId })
      }
    },
    h5Base() {
      return location.port === '5173' ? `${location.protocol}//${location.hostname}:5174/activity/` : '/activity/'
    },
    goSiteHome() {
      window.location.href = this.h5Base()
    },
    goH5ActivityDetail() {
      if (!this.fromH5Activity) return
      window.location.href = `${this.h5Base()}?activityId=${encodeURIComponent(this.sourceActivityId)}#/app`
    },
    redirectToH5Login() {
      const current = location.href
      window.location.href = `${this.h5Base()}?returnTo=${encodeURIComponent(current)}#/app`
    },
    load() {
      return ({ dashboard: this.loadDashboard, escape: () => Promise.resolve(), batches: () => Promise.resolve(), activities: this.loadActivities, venues: this.loadVenues, modes: this.loadModes, users: this.loadUsers, attendance: this.loadAttendance, launchers: this.loadLaunchers, system: this.loadSystem }[this.active] || this.loadActivities)()
    },
    loadSystem() {
      if (!this.systemMenus.some(item => item.key === this.systemActive)) {
        this.systemActive = this.systemMenus[0]?.key || ''
      }
      if (this.systemActive === 'permissions') return this.loadPermissions()
      if (this.systemActive === 'systemImages') return this.loadSystemImages()
      return Promise.resolve()
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
    async loadSystemImages() {
      const [images, carousels] = await Promise.all([
        api('/api/admin/system/images'),
        api('/api/admin/system/homepage-carousels')
      ])
      this.systemImages = images || {}
      this.homepageCarouselImages = (carousels || []).map(item => ({ ...item, active: !!item.active }))
    },
    homepageImagesFor(sectionKey) {
      return this.homepageCarouselImages
        .filter(item => item.section_key === sectionKey)
        .sort((a, b) => Number(a.sort_order) - Number(b.sort_order) || Number(a.id) - Number(b.id))
    },
    uploadHomepageCarousel(sectionKey, response) {
      const imageUrl = response?.data?.url
      if (!imageUrl) return ElMessage.error('图片上传失败')
      return api('/api/admin/system/homepage-carousels', {
        method: 'POST',
        body: { section_key: sectionKey, image_url: imageUrl, active: true }
      }).then(() => {
        ElMessage.success('轮播图片已添加')
        return this.loadSystemImages()
      })
    },
    async moveHomepageCarousel(sectionKey, index, direction) {
      const images = this.homepageImagesFor(sectionKey)
      const targetIndex = index + direction
      if (!images[index] || !images[targetIndex]) return
      const current = images[index]
      const target = images[targetIndex]
      const currentOrder = Number(current.sort_order) || (index + 1) * 10
      const targetOrder = Number(target.sort_order) || (targetIndex + 1) * 10
      await Promise.all([
        api(`/api/admin/system/homepage-carousels/${current.id}`, { method: 'PUT', body: { sort_order: targetOrder } }),
        api(`/api/admin/system/homepage-carousels/${target.id}`, { method: 'PUT', body: { sort_order: currentOrder } })
      ])
      await this.loadSystemImages()
    },
    setHomepageCarouselActive(image, active) {
      return api(`/api/admin/system/homepage-carousels/${image.id}`, {
        method: 'PUT',
        body: { active }
      }).then(() => this.loadSystemImages()).catch(() => this.loadSystemImages())
    },
    deleteHomepageCarousel(image) {
      return ElMessageBox.confirm('确认删除这张首页轮播图？').then(() =>
        api(`/api/admin/system/homepage-carousels/${image.id}`, { method: 'DELETE' }).then(() => {
          ElMessage.success('已删除')
          return this.loadSystemImages()
        })
      )
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
	      this.attendanceUsers = [...(matrix.users || [])].sort((a, b) => {
	        const membershipOrder = Number(!!b.is_regular_member) - Number(!!a.is_regular_member)
	        if (membershipOrder) return membershipOrder
	        return String(a.callsign || a.username || '').localeCompare(String(b.callsign || b.username || ''), 'zh-CN')
	      })
	      this.attendanceRecords = matrix.records
	      this.attendanceEnrollments = matrix.enrollments || []
	    },
    async loadFormalUsers() {
      if (!this.formalUsers.length) this.formalUsers = await api('/api/admin/users/formal/options')
      return this.formalUsers
    },
    async openAttendanceEvent(event = null) {
      await this.loadFormalUsers()
      let organizerIds = this.parseIds(event?.organizer_ids).map(Number).filter(Number.isFinite)
      if (!organizerIds.length && event?.organizer) {
        const legacyNames = String(event.organizer).split(/[、,，]/).map(value => value.trim()).filter(Boolean)
        organizerIds = this.formalUsers
          .filter(user => legacyNames.includes(user.callsign || user.username))
          .map(user => Number(user.id))
      }
      this.editEvent = { ...(event || {}), organizer_ids: organizerIds }
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
	      await this.loadFormalUsers()
	      if (!row) {
        this.activityForm = { banner_url: '', external_miniapp_qr_url: '', banner_source: 'venue', venue_id: null, checkin_methods: ['location', 'qr'], checkin_open_value: 3, checkin_open_unit: 'hour', organizer_ids: [Number(this.me.id)], activity_type: '周常', camp_count: 2, squad_count: 1, activity_region: '宁波', visibility_type: 'all', invitee_ids: [], launcher_ids: [], allowed_jobs: [...jobs], game_modes: [] }
	        return
	      }
	      const detail = await api(`/api/admin/activities/${row.id}`)
	      this.activityForm = {
	        ...detail,
	        organizer_ids: this.parseIds(detail.organizer_ids).map(Number),
	        checkin_methods: this.parseCheckinMethods(detail.checkin_methods),
	        checkin_open_value: this.normalizeCheckinOpenValue(detail.checkin_open_value),
	        checkin_open_unit: this.normalizeCheckinOpenUnit(detail.checkin_open_unit),
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
      return (venue && venue.image_url) || this.activityForm.banner_url || defaultActivityBanner
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
      return venue.name || ''
    },
    setActivityVenue(id) {
      if (!this.activityForm) return
      const venue = this.venues.find(item => Number(item.id) === Number(id))
      if (!venue) return
      this.activityForm.location = venue.address || venue.name
      if (this.activityForm.banner_source !== 'custom') this.activityForm.banner_source = 'venue'
    },
	    async openPlan(row) {
	      await this.loadFormalUsers()
	      if (!row) {
	        this.planForm = { banner_url: '', organizer_ids: [Number(this.me.id)], visibility_type: 'all', invitee_ids: [], dates: [{ date: '', remark: '' }], venue_ids: [], game_mode_ids: [] }
	        return
	      }
	      const detail = await api(`/api/admin/activity-plans/${row.id}`)
	      this.planForm = {
	        ...detail,
	        organizer_ids: this.parseIds(detail.organizer_ids).map(Number),
	        invitee_ids: this.parseIds(detail.invitee_ids),
	        dates: (detail.dates || []).map(item => ({ id: item.id, date: item.date || '', remark: item.remark || '' })),
	        venue_ids: (detail.venues || []).map(item => item.id),
	        game_mode_ids: (detail.game_modes || []).map(item => item.id)
	      }
	      if (this.planForm.invitee_ids.length) await this.loadNonFormalUsers()
	    },
	    saveActivity() {
	      this.activityForm.invitee_ids = this.normalizedInviteeIds(this.activityForm)
	      this.activityForm.launcher_ids = this.normalizedLauncherIds(this.activityForm)
	      this.activityForm.checkin_methods = this.parseCheckinMethods(this.activityForm.checkin_methods)
	      this.activityForm.checkin_open_value = this.normalizeCheckinOpenValue(this.activityForm.checkin_open_value)
	      this.activityForm.checkin_open_unit = this.normalizeCheckinOpenUnit(this.activityForm.checkin_open_unit)
	      if (!this.activityForm.checkin_methods.length) return ElMessage.warning('请至少选择一种签到方式')
	      if (!this.activityForm.start_at || !this.activityForm.end_at) return ElMessage.warning('请选择活动开始时间和结束时间')
	      if (new Date(this.activityForm.end_at.replace(' ', 'T')) <= new Date(this.activityForm.start_at.replace(' ', 'T'))) return ElMessage.warning('活动结束时间必须晚于开始时间')
	      const method = this.activityForm.id ? 'PUT' : 'POST'
	      const url = `/api/admin/activities${this.activityForm.id ? `/${this.activityForm.id}` : ''}`
	      return api(url, { method, body: this.activityForm }).then(() => { this.activityForm = null; this.loadActivities() })
	    },
	    savePlan() {
	      this.planForm.invitee_ids = this.normalizedInviteeIds(this.planForm)
	      this.planForm.dates = (this.planForm.dates || []).map(item => typeof item === 'object'
	        ? { id: item.id, date: item.date || '', remark: item.remark || '' }
	        : { date: item, remark: '' }).filter(item => item.date)
	      const method = this.planForm.id ? 'PUT' : 'POST'
	      const url = `/api/admin/activity-plans${this.planForm.id ? `/${this.planForm.id}` : ''}`
	      return api(url, { method, body: this.planForm }).then(() => { this.planForm = null; this.loadActivities() })
	    },
	    parseIds(value) {
	      if (Array.isArray(value)) return value.map(item => String(item)).filter(Boolean)
	      return String(value || '').split(',').map(item => item.trim()).filter(Boolean)
	    },
	    parseCheckinMethods(value) {
	      const methods = this.parseIds(value).filter(item => ['location', 'qr'].includes(item))
	      return methods.length ? [...new Set(methods)] : ['location', 'qr']
	    },
	    normalizeCheckinOpenValue(value) {
	      const number = Number(value)
	      return Number.isFinite(number) ? Math.max(0, Math.floor(number)) : 3
	    },
	    normalizeCheckinOpenUnit(value) {
	      return value === 'day' ? 'day' : 'hour'
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
	      this.invitePickerKeyword = ''
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
      await this.loadFormalUsers()
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
      const organizerIds = this.planOrganizerIds(plan)
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
        checkin_methods: ['location', 'qr'],
        checkin_open_value: 3,
        checkin_open_unit: 'hour',
        open_min: 0,
        camp_count: 2,
        camp_limit: 0,
        squad_count: 1,
        squad_limit: 0,
        activity_region: '宁波',
        organizer_ids: organizerIds,
        visibility_type: plan.visibility_type || 'all',
        invitee_ids: this.parseIds(plan.invitee_ids),
        launcher_ids: [],
        allowed_jobs: [...jobs],
        game_modes: modeNames
      }
      if (this.activityForm.invitee_ids.length) await this.loadNonFormalUsers()
    },
    planOrganizerIds(plan) {
      const organizerIds = this.parseIds(plan?.organizer_ids).map(Number).filter(Number.isFinite)
      if (organizerIds.length) return organizerIds
      const creatorId = Number(plan?.created_by_id)
      return Number.isFinite(creatorId) ? [creatorId] : []
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
    async loadAmap() {
      if (window.AMap) return window.AMap
      const key = import.meta.env.VITE_AMAP_KEY
      if (!key) throw new Error('未配置 VITE_AMAP_KEY，可先手工填写经纬度')
      const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_CODE
      if (securityJsCode) window._AMapSecurityConfig = { securityJsCode }
      if (!window.__amapLoader) {
        window.__amapLoader = new Promise((resolve, reject) => {
          const script = document.createElement('script')
          script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&plugin=AMap.Geocoder`
          script.onload = () => resolve(window.AMap)
          script.onerror = () => reject(new Error('高德地图加载失败'))
          document.head.appendChild(script)
        })
      }
      return window.__amapLoader
    },
    async initVenueMap() {
      try {
        const AMap = await this.loadAmap()
        this.venueMap?.destroy()
        this.venueMarker = null
        const hasPoint = Number.isFinite(Number(this.editVenue.longitude)) && Number.isFinite(Number(this.editVenue.latitude))
        const center = hasPoint ? [Number(this.editVenue.longitude), Number(this.editVenue.latitude)] : [121.55027, 29.87386]
        this.venueMap = new AMap.Map('venue-map', { zoom: hasPoint ? 16 : 11, center })
        if (hasPoint) this.setVenueMapPoint(center[0], center[1])
        this.venueMap.on('click', event => this.setVenueMapPoint(event.lnglat.getLng(), event.lnglat.getLat()))
      } catch (error) {
        ElMessage.warning(error.message)
      }
    },
    setVenueMapPoint(longitude, latitude) {
      if (!this.editVenue) return
      this.editVenue.longitude = Number(Number(longitude).toFixed(7))
      this.editVenue.latitude = Number(Number(latitude).toFixed(7))
      if (!this.venueMap || !window.AMap) return
      if (!this.venueMarker) this.venueMarker = new window.AMap.Marker({ map: this.venueMap })
      this.venueMarker.setPosition([longitude, latitude])
    },
    async locateVenueAddress() {
      if (!String(this.editVenue?.address || '').trim()) return ElMessage.warning('请先填写场地地址')
      try {
        const AMap = await this.loadAmap()
        const geocoder = new AMap.Geocoder({ city: '全国' })
        geocoder.getLocation(this.editVenue.address, (status, result) => {
          const point = result?.geocodes?.[0]?.location
          if (status !== 'complete' || !point) return ElMessage.warning('未找到该地址，请在地图中手工选点')
          this.setVenueMapPoint(point.getLng(), point.getLat())
          this.venueMap?.setZoomAndCenter(16, point)
        })
      } catch (error) {
        ElMessage.warning(error.message)
      }
    },
    saveVenue() {
      if (this.editVenue.longitude == null || this.editVenue.latitude == null) return ElMessage.warning('请在地图选择签到中心点')
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
    resetUserPassword(row) {
      return ElMessageBox.confirm(`确认重置 ${row.callsign || row.username} 的密码为 nb123456？临时密码将在24小时后过期。`, '重置密码').then(() => api(`/api/admin/users/${row.id}/reset-password`, { method: 'PUT', body: {} })).then(data => {
        return ElMessageBox.alert(`临时密码：${data.temporary_password}\n\n请立即安全地告知用户。关闭后将无法再次查看。`, '密码重置成功', {
          confirmButtonText: '我已保存',
          dangerouslyUseHTMLString: false
        })
      }).then(() => {
        this.loadUsers()
      })
    },
	    saveEvent() {
	      return api(`/api/admin/attendance/history-activities${this.editEvent.id ? `/${this.editEvent.id}` : ''}`, { method: this.editEvent.id ? 'PUT' : 'POST', body: this.editEvent }).then(() => { this.editEvent = null; this.loadAttendance() })
	    },
	    deleteAttendanceEvent(event) {
	      if (event.is_manual) return this.remove(`/api/admin/attendance/history-activities/${event.id}`, this.loadAttendance)
	      if (!event.source_activity_id) return ElMessage.warning('该出勤记录未关联正式活动，无法从活动列表同步删除')
	      return ElMessageBox.confirm(
	        `确认删除正式活动“${event.name}”？报名、签到、阵营、小队和租赁记录也会同步删除。`,
	        '删除正式活动',
	        { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
	      ).then(() => api(`/api/admin/activities/${event.source_activity_id}`, { method: 'DELETE' }))
	        .then(() => Promise.all([this.loadAttendance(), this.loadActivities()]))
	    },
	    post(url, callback) { return api(url, { method: 'POST', body: {} }).then(() => callback && callback()) },
	    put(url, callback) { return api(url, { method: 'PUT', body: {} }).then(() => callback && callback()) },
	    remove(url, callback) {
	      return ElMessageBox.confirm('确认删除？').then(() => api(url, { method: 'DELETE' }).then(() => callback && callback()))
	    },
	    isPresent(eventId, userId) {
      return this.attendanceRecords.some(r => Number(r.event_id) === Number(eventId) && Number(r.user_id) === Number(userId) && r.present)
    },
	    isEnrolled(eventId, userId) {
      return this.attendanceEnrollments.some(r => Number(r.event_id) === Number(eventId) && Number(r.user_id) === Number(userId))
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
