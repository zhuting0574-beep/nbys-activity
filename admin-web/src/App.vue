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
      <div v-for="item in menus" :key="item.key" class="menu" :class="{ active: active === item.key }" @click="active = item.key; load()">
        {{ item.name }}
      </div>
    </aside>

    <main class="main">
      <div class="toolbar">
        <strong>{{ currentMenu?.name }}</strong>
        <span class="muted">当前用户：{{ me.username }} / {{ me.callsign }}</span>
      </div>

      <section v-if="active === 'activities'" class="card">
        <div class="toolbar">
          <el-input v-model="filters.name" placeholder="活动名称" style="width: 180px" />
          <el-select v-model="filters.recordType" placeholder="类型" clearable style="width: 150px">
            <el-option label="正式活动" value="activity" />
            <el-option label="活动策划" value="plan" />
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
            <template #default="{ row }">{{ row.start_at || row.vote_deadline || row.end_at }}</template>
          </el-table-column>
          <el-table-column prop="location" label="地点" min-width="120" />
          <el-table-column label="报名"><template #default="{ row }">{{ row.enroll_count || 0 }} / {{ row.signup_limit || '-' }}</template></el-table-column>
          <el-table-column prop="checkin_count" label="签到" />
          <el-table-column prop="display_status" label="状态" />
          <el-table-column label="Banner"><template #default="{ row }"><img v-if="row.banner_url" class="thumb" :src="row.banner_url" /></template></el-table-column>
          <el-table-column label="操作" width="360">
            <template #default="{ row }">
              <el-button size="small" @click="row.record_type === 'plan' ? openPlan(row) : openActivity(row)">查看/编辑</el-button>
              <template v-if="row.record_type === 'activity'">
                <el-button size="small" v-if="!row.deleted_at && can('activity:cancel')" @click="post(`/api/admin/activities/${row.id}/cancel`, loadActivities)">取消</el-button>
                <el-button size="small" v-if="row.deleted_at && can('activity:restore')" @click="post(`/api/admin/activities/${row.id}/restore`, loadActivities)">恢复</el-button>
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
              <el-button size="small" v-if="can('user:resetPassword')" @click="post(`/api/admin/users/${row.id}/reset-password`, loadUsers)">重置密码</el-button>
              <el-button size="small" type="danger" v-if="can('user:delete')" @click="remove(`/api/admin/users/${row.id}`, loadUsers)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="active === 'attendance'" class="card">
        <div class="toolbar">
          <el-date-picker v-model="attendanceYear" type="year" value-format="YYYY" />
          <el-select v-model="filters.region" clearable placeholder="地区" style="width: 120px">
            <el-option label="宁波" value="宁波" />
            <el-option label="外地" value="外地" />
          </el-select>
          <el-checkbox v-model="filters.formalOnly">只看正式队员</el-checkbox>
          <el-button @click="loadAttendance">查询</el-button>
          <el-button type="primary" v-if="can('attendance:create')" @click="editEvent = {}">手动增加历史活动</el-button>
        </div>
        <el-row :gutter="12" style="margin-bottom: 12px">
          <el-col :span="6"><el-statistic title="活动总次数" :value="summary.activity_total || 0" /></el-col>
          <el-col :span="6">前三出勤正式队员<br />{{ (summary.top_formal_members || []).map(x => x.callsign).join('、') || '-' }}</el-col>
          <el-col :span="6">最多组织活动成员<br />{{ summary.top_organizer?.organizer || '-' }}</el-col>
          <el-col :span="6">最受欢迎场地<br />{{ summary.popular_venue?.location || '-' }}</el-col>
        </el-row>
        <el-table :data="attendanceUsers" border height="560">
          <el-table-column prop="callsign" label="人员名称" fixed width="150" />
          <el-table-column label="出勤次数" fixed width="90"><template #default="{ row }">{{ countPresent(row.id) }}</template></el-table-column>
          <el-table-column v-for="event in attendanceEvents" :key="event.id" width="190">
            <template #header>{{ event.name }}<br />{{ event.event_date }}/{{ event.location }}<br />{{ event.organizer }}/{{ event.activity_region }}</template>
            <template #default="{ row }"><el-button link @click="togglePresent(event.id, row.id)">{{ isPresent(event.id, row.id) ? '●' : '' }}</el-button></template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="active === 'permissions'" class="card">
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
    </main>
  </div>

  <el-dialog v-model="venueVisible" title="场地" width="560px"><el-form label-width="90px"><el-form-item label="场地图片"><div class="banner-upload-row"><el-upload action="/api/admin/files/upload" accept="image/*" :headers="uploadHeaders" :show-file-list="false" :on-success="r => editVenue.image_url = r.data.url"><el-button>上传图片</el-button></el-upload><el-button v-if="editVenue.image_url" @click="editVenue.image_url = ''">清除</el-button></div><img v-if="editVenue.image_url" class="venue-preview" :src="editVenue.image_url" /><div v-else class="venue-empty">建议上传场地实景图，可作为活动默认Banner</div></el-form-item><el-form-item label="场地名称"><el-input v-model="editVenue.name" /></el-form-item><el-form-item label="场地地址"><el-input v-model="editVenue.address" /></el-form-item></el-form><template #footer><el-button @click="editVenue = null">取消</el-button><el-button type="primary" @click="saveVenue">保存</el-button></template></el-dialog>
  <el-dialog v-model="modeVisible" title="模式"><el-form label-width="90px"><el-form-item label="模式名称"><el-input v-model="editMode.name" /></el-form-item><el-form-item label="模式内容"><el-input v-model="editMode.rules" type="textarea" /></el-form-item><el-form-item label="人数"><el-input v-model="editMode.suitable_people" /></el-form-item></el-form><template #footer><el-button @click="editMode = null">取消</el-button><el-button type="primary" @click="saveMode">保存</el-button></template></el-dialog>
  <el-dialog v-model="userVisible" title="用户"><el-form label-width="110px"><el-form-item label="呼号"><el-input v-model="editUser.callsign" /></el-form-item><el-form-item label="权限"><el-select v-model="editUser.role"><el-option v-for="role in roles" :key="role.value" :label="role.label" :value="role.value" /></el-select></el-form-item><el-form-item label="账号禁用"><el-switch v-model="editUser.disabled" /></el-form-item><el-form-item label="正式队员"><el-switch v-model="editUser.is_regular_member" /></el-form-item></el-form><template #footer><el-button @click="editUser = null">取消</el-button><el-button type="primary" @click="saveUser">保存</el-button></template></el-dialog>
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
      <el-form-item label="开放职业"><el-checkbox-group v-model="activityForm.allowed_jobs"><el-checkbox v-for="job in jobs" :key="job" :label="job" /></el-checkbox-group></el-form-item>
      <el-form-item label="游戏模式"><el-checkbox-group v-model="activityForm.game_modes"><el-checkbox v-for="mode in modes" :key="mode.id" :label="mode.name">{{ mode.name }}/{{ mode.suitable_people }}</el-checkbox></el-checkbox-group></el-form-item>
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
      <el-form-item label="可选日期"><div><div v-for="(_, i) in planForm.dates" :key="i"><el-date-picker v-model="planForm.dates[i]" value-format="YYYY-MM-DD" /><el-button @click="planForm.dates.splice(i, 1)">删除</el-button></div><el-button @click="planForm.dates.push('')">+</el-button></div></el-form-item>
      <el-form-item label="可选场地"><el-select v-model="planForm.venue_ids" multiple><el-option v-for="venue in venues" :key="venue.id" :label="venue.name" :value="venue.id" /></el-select></el-form-item>
      <el-form-item label="指定游戏模式"><el-select v-model="planForm.game_mode_ids" multiple><el-option v-for="mode in modes" :key="mode.id" :label="mode.name" :value="mode.id" /></el-select></el-form-item>
      <el-form-item label="可见范围"><el-select v-model="planForm.visibility_type"><el-option label="所有人公开" value="all" /><el-option label="仅正式队员" value="official" /><el-option label="正式队员和邀请队员" value="official_plus_invite" /></el-select></el-form-item>
    </el-form>
    <template #footer><el-button @click="planForm = null">取消</el-button><el-button type="primary" @click="savePlan">保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="eventVisible" title="历史活动"><el-form label-width="90px"><el-form-item label="活动名称"><el-input v-model="editEvent.name" /></el-form-item><el-form-item label="日期"><el-date-picker v-model="editEvent.event_date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="场地"><el-input v-model="editEvent.location" /></el-form-item><el-form-item label="组织人"><el-input v-model="editEvent.organizer" /></el-form-item><el-form-item label="地区"><el-select v-model="editEvent.activity_region"><el-option label="宁波" value="宁波" /><el-option label="外地" value="外地" /></el-select></el-form-item></el-form><template #footer><el-button @click="editEvent = null">取消</el-button><el-button type="primary" @click="saveEvent">保存</el-button></template></el-dialog>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, setToken, token } from './api'

const jobs = ['突击兵', '支援兵', '医疗兵', '狙击手', '弹药兵', '填线兵']

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
      active: 'activities',
      filters: {},
      activities: [],
      venues: [],
      modes: [],
      users: [],
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
      const items = [
        ['activities', '活动管理', 'activity:view'],
        ['venues', '场地管理', 'venue:view'],
        ['modes', '模式管理', 'gameMode:view'],
        ['users', '用户管理', 'user:view'],
        ['attendance', '出勤统计', 'attendance:view'],
        ['permissions', '权限管理', 'permission:view']
      ]
      return items.filter(([, , p]) => this.can(p)).map(([key, name]) => ({ key, name }))
    },
    currentMenu() {
      return this.menus.find(item => item.key === this.active)
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
    uploadHeaders() {
      return { Authorization: `Bearer ${this.tokenValue}` }
    },
    venueVisible: { get() { return !!this.editVenue }, set(v) { if (!v) this.editVenue = null } },
    modeVisible: { get() { return !!this.editMode }, set(v) { if (!v) this.editMode = null } },
    userVisible: { get() { return !!this.editUser }, set(v) { if (!v) this.editUser = null } },
    activityVisible: { get() { return !!this.activityForm }, set(v) { if (!v) this.activityForm = null } },
    planVisible: { get() { return !!this.planForm }, set(v) { if (!v) this.planForm = null } },
    eventVisible: { get() { return !!this.editEvent }, set(v) { if (!v) this.editEvent = null } }
  },
  async mounted() {
    if (this.tokenValue) await this.init()
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
      return ({ activities: this.loadActivities, venues: this.loadVenues, modes: this.loadModes, users: this.loadUsers, attendance: this.loadAttendance, permissions: this.loadPermissions }[this.active] || this.loadActivities)()
    },
    loadActivities() { return api(`/api/admin/activities?name=${this.filters.name || ''}&recordType=${this.filters.recordType || ''}`).then(d => { this.activities = d }) },
    loadVenues() { return api(`/api/admin/venues?name=${this.filters.name || ''}`).then(d => { this.venues = d }) },
    loadModes() { return api(`/api/admin/game-modes?name=${this.filters.name || ''}`).then(d => { this.modes = d }) },
    loadUsers() { return api(`/api/admin/users?keyword=${this.filters.keyword || ''}`).then(d => { this.users = d }) },
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
      this.summary = await api(`/api/admin/attendance/summary?year=${this.attendanceYear}&region=${this.filters.region || ''}&formalOnly=${!!this.filters.formalOnly}`)
      const matrix = await api(`/api/admin/attendance/matrix?year=${this.attendanceYear}&region=${this.filters.region || ''}&formalOnly=${!!this.filters.formalOnly}`)
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
        this.activityForm = { banner_url: '', banner_source: 'venue', venue_id: null, activity_type: '周常', camp_count: 2, squad_count: 1, activity_region: '宁波', visibility_type: 'all', allowed_jobs: [...jobs], game_modes: [] }
        return
      }
      const detail = await api(`/api/admin/activities/${row.id}`)
      this.activityForm = {
        ...detail,
        banner_source: detail.banner_source || 'venue',
        allowed_jobs: (detail.allowed_jobs || '').split(',').filter(Boolean),
        game_modes: (detail.game_modes || '').split(',').filter(Boolean)
      }
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
    },
    async openPlan(row) {
      if (!row) {
        this.planForm = { banner_url: '', visibility_type: 'all', dates: [''], venue_ids: [], game_mode_ids: [] }
        return
      }
      const detail = await api(`/api/admin/activity-plans/${row.id}`)
      this.planForm = {
        ...detail,
        dates: (detail.dates || []).map(item => item.date),
        venue_ids: (detail.venues || []).map(item => item.id),
        game_mode_ids: (detail.game_modes || []).map(item => item.id)
      }
    },
    saveActivity() {
      const method = this.activityForm.id ? 'PUT' : 'POST'
      const url = `/api/admin/activities${this.activityForm.id ? `/${this.activityForm.id}` : ''}`
      return api(url, { method, body: this.activityForm }).then(() => { this.activityForm = null; this.loadActivities() })
    },
    savePlan() {
      const method = this.planForm.id ? 'PUT' : 'POST'
      const url = `/api/admin/activity-plans${this.planForm.id ? `/${this.planForm.id}` : ''}`
      return api(url, { method, body: this.planForm }).then(() => { this.planForm = null; this.loadActivities() })
    },
    convertPlan(row) {
      return ElMessageBox.confirm('确认将该活动策划转为正式活动？系统会根据投票最高的日期、场地生成活动，提交后不可撤销。').then(() =>
        api(`/api/admin/activity-plans/${row.id}/convert`, { method: 'POST', body: {} }).then(() => {
          ElMessage.success('已转为正式活动')
          this.loadActivities()
        })
      )
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
    saveEvent() {
      return api(`/api/admin/attendance/history-activities${this.editEvent.id ? `/${this.editEvent.id}` : ''}`, { method: this.editEvent.id ? 'PUT' : 'POST', body: this.editEvent }).then(() => { this.editEvent = null; this.loadAttendance() })
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
    togglePresent(eventId, userId) {
      if (!this.can('attendance:update')) return
      return api('/api/admin/attendance/records', { method: 'PUT', body: { event_id: eventId, user_id: userId, present: !this.isPresent(eventId, userId) } }).then(this.loadAttendance)
    }
  }
}
</script>
