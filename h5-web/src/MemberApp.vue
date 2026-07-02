<template>
  <div>
    <header class="topbar">
      <a class="brand" href="#" @click.prevent="goHome">
        <img class="brand-logo" :src="displayLogoUrl" alt="甬士 Logo" />
        <span>宁波甬士活动管理系统</span>
      </a>
      <button v-if="showHeaderBack" class="header-back" @click="goHome">返回首页</button>
    </header>

    <div v-if="view === 'login'" class="page auth-page" :style="authPageStyle">
      <form class="auth-card" @submit.prevent="login()">
        <h2>登录</h2>
        <input v-model="loginForm.account" placeholder="名字 / 呼号" required />
        <input v-model="loginForm.password" placeholder="密码" type="password" required />
        <button class="btn" style="width: 100%" type="submit">登录</button>
        <p class="muted">没有账号？赶紧 <a @click="view = 'register'">注册</a>。</p>
      </form>
    </div>

    <div v-else-if="view === 'register'" class="page auth-page" :style="authPageStyle">
      <form class="auth-card" @submit.prevent="register()">
        <h2>注册</h2>
        <input v-model="registerForm.username" placeholder="用户名" required />
        <input v-model="registerForm.callsign" placeholder="呼号（选填）" />
        <input v-model="registerForm.password" placeholder="密码" type="password" required />
        <input v-model="registerForm.confirm_password" placeholder="确认密码" type="password" />
        <input v-model="registerForm.invite_code" placeholder="邀请码（选填）" />
        <p class="muted">用户名、密码为必填项</p>
        <label class="upload-field">
          <span>头像</span>
          <input type="file" accept="image/*" @change="uploadRegisterAvatar" />
        </label>
        <img v-if="registerForm.avatar_url" class="avatar-preview" :src="registerForm.avatar_url" alt="头像预览" />
        <button class="btn" style="width: 100%" type="submit">注册</button>
        <p class="muted"><a @click="view = 'login'">返回登录</a></p>
      </form>
    </div>

    <template v-else>
    <div v-if="tab === 'activities' && !selectedActivity && !selectedPlan">
      <div class="top">
        <div class="home-identity mine-identity">
          <h2>{{ me.username }}</h2>
          <p>
            <span>{{ me.callsign || '未设置呼号' }}</span>
            <b>{{ attendanceSummary.present_count || 0 }}/{{ attendanceSummary.activity_total || 0 }}</b>
          </p>
        </div>
        <img v-if="me.avatar_url" class="user-avatar" :src="me.avatar_url" alt="用户头像" />
        <div v-else class="user-avatar fallback">{{ avatarText }}</div>
      </div>
      <div class="page">
        <div class="activity-list">
          <div v-for="activity in activities" :key="`${activity.record_kind}-${activity.id}`" class="card" :class="{ 'plan-card': activity.record_kind === 'plan' }" @click="openHomeCard(activity)">
            <span class="status" :class="statusClass(activity.display_status)">{{ statusLabel(activity.display_status) }}</span>
            <img v-if="activity.banner_url" class="banner" :src="activity.banner_url" loading="lazy" decoding="async" />
            <div v-else class="banner banner-placeholder">{{ activity.record_kind === 'plan' ? '活动策划' : '正式活动' }}</div>
            <h3>{{ activity.name }}</h3>
            <div v-if="activity.record_kind === 'activity'" class="card-meta">
              <p>{{ formatTimeRange(activity.start_at, activity.end_at) }}</p>
              <p>{{ displayVenueName(activity) }}</p>
              <p>发起人：{{ activity.creator_name || '未设置' }}</p>
              <p>报名：{{ activity.enroll_count || 0 }} / {{ activity.signup_limit || '-' }}</p>
              <p :class="{ ready: (activity.enroll_count || 0) >= activity.open_min }">
                {{ (activity.enroll_count || 0) >= activity.open_min ? '已满足开启人数' : '未满足开启人数' }}
              </p>
            </div>
            <div v-else class="card-meta">
              <p>投票截止：{{ formatDateTime(activity.vote_deadline) }}</p>
              <p>发起人：{{ activity.creator_name || '未设置' }}</p>
              <p>可选日期：{{ planDateNames(activity.dates) || '待配置' }}</p>
              <p>可选场地：{{ planNames(activity.venues, 'name') || '待配置' }}</p>
              <p>游戏模式：{{ planNames(activity.game_modes, 'name') || '待配置' }}</p>
            </div>
          </div>
          <div v-if="activities.length === 0" class="empty-state">暂无报名中、进行中或策划中的活动</div>
        </div>
      </div>
    </div>

    <div v-if="tab === 'activities' && selectedPlan" class="page plan-detail">
      <img v-if="selectedPlan.banner_url" class="banner" :src="selectedPlan.banner_url" loading="lazy" decoding="async" />
      <div v-else class="banner banner-placeholder">活动策划</div>
      <div class="plan-head">
        <div>
          <h2>{{ selectedPlan.name }}</h2>
          <p class="muted">投票截止：{{ formatDateTime(selectedPlan.vote_deadline) }}</p>
          <p class="muted">发起人：{{ selectedPlan.creator_name || '未设置' }}</p>
        </div>
        <span class="status inline planning">策划中</span>
      </div>

      <section class="vote-section">
        <h3>可选日期</h3>
        <label v-for="option in selectedPlan.dates" :key="option.id" class="option-tile">
          <input v-model="planVoteForm.date_option_ids" type="checkbox" :value="option.id" :disabled="selectedPlan.voted" />
          <span>
            <strong>{{ option.date }}</strong>
            <small v-if="option.remark">{{ option.remark }}</small>
            <small v-if="selectedPlan.voted">{{ option.vote_count || 0 }} 票</small>
          </span>
        </label>
        <p v-if="!selectedPlan.dates?.length" class="muted">暂无日期选项</p>
      </section>

      <section class="vote-section">
        <h3>可选场地</h3>
        <label v-for="venue in selectedPlan.venues" :key="venue.id" class="option-tile">
          <input v-model="planVoteForm.venue_ids" type="checkbox" :value="venue.id" :disabled="selectedPlan.voted" />
          <span>
            <strong>{{ venue.name }}</strong>
            <small><a :href="amapUrl(venue.address || venue.name)" target="_blank" rel="noopener" @click.stop>{{ venue.address || '地址待配置' }}</a></small>
            <small v-if="selectedPlan.voted">{{ venue.vote_count || 0 }} 票</small>
          </span>
        </label>
        <p v-if="!selectedPlan.venues?.length" class="muted">暂无场地选项</p>
      </section>

      <section class="vote-section">
        <h3>指定游戏模式</h3>
        <label v-for="mode in selectedPlan.game_modes" :key="mode.id" class="option-tile">
          <input v-model="planVoteForm.game_mode_ids" type="checkbox" :value="mode.id" :disabled="selectedPlan.voted" />
          <span>
            <strong>{{ mode.name }}</strong>
            <small>{{ mode.suitable_people ? `${mode.suitable_people}人` : '人数待配置' }}</small>
            <small v-if="selectedPlan.voted">{{ mode.vote_count || 0 }} 票</small>
          </span>
        </label>
        <p v-if="!selectedPlan.game_modes?.length" class="muted">暂无游戏模式选项</p>
      </section>

      <button class="btn plan-vote-btn" :disabled="selectedPlan.voted" @click="submitPlanVote">
        {{ selectedPlan.voted ? '已投票' : '投票' }}
      </button>
    </div>

    <div v-if="tab === 'activities' && selectedActivity" class="page detail-page">
      <section class="detail-card">
        <img v-if="detail.banner_url" class="detail-banner" :src="detail.banner_url" loading="lazy" decoding="async" />
        <div v-else class="detail-banner banner-placeholder">正式活动</div>
        <h2>{{ detail.name }}</h2>
        <p class="detail-time">{{ formatTimeRange(detail.start_at, detail.end_at) }} · {{ displayVenueName(detail) }}</p>
        <p class="detail-copy">发起人：{{ detail.creator_name || '未设置' }}</p>
        <p class="detail-copy">
          场地地址：
          <a :href="amapUrl(displayVenueAddress(detail) || displayVenueName(detail))" target="_blank" rel="noopener">
            {{ displayVenueAddress(detail) || '地址待配置' }}
          </a>
        </p>
        <div class="detail-status-row">
          <span class="status inline" :class="statusClass(detail.display_status)">{{ statusLabel(detail.display_status) }}</span>
          <span>报名人数：{{ detail.enroll_count || 0 }} / {{ detail.signup_limit }}</span>
        </div>
        <p class="detail-copy">开放职业：{{ detail.allowed_jobs || '未配置' }}</p>
        <p class="detail-copy">人员列表：{{ detail.my_enrollment ? '已加入' : '未加入' }}　游戏模式：{{ detail.game_modes || '未配置' }}</p>
        <p class="detail-copy">我的状态：{{ detail.checkin?.present ? '已签到' : detail.my_enrollment ? '已报名' : '未报名' }}</p>

        <div class="detail-actions">
          <button v-if="detail.display_status === '报名中' && !detail.my_enrollment" class="btn detail-main-action" @click="enroll">报名</button>
          <button v-if="detail.display_status === '活动开始' && detail.my_enrollment && !detail.checkin?.present" class="btn detail-main-action" @click="checkin">签到</button>
          <button v-if="detail.checkin?.present" class="btn secondary detail-main-action" disabled>已签到</button>
          <button v-if="detail.my_enrollment" class="btn secondary detail-main-action" @click="openActivityRentals">发射器租赁</button>
          <button v-if="detail.my_enrollment && !detail.checkin?.present" class="btn danger detail-main-action" @click="cancelEnroll">取消报名</button>
        </div>
      </section>

      <section v-if="detail.my_enrollment || detail.is_activity_creator" class="squad-panel">
        <h3>阵营 / 小队 / 人员列表</h3>
        <p class="muted">点击某个小队即可自动加入对应阵营和小队。职业会自动保存。</p>
        <div v-for="camp in camps" :key="camp" class="camp-block">
          <div class="camp-head">
            <span>阵营 {{ camp }}</span>
            <small>{{ membersByCamp(camp).length }}/{{ squadsByCamp(camp).reduce((sum, squad) => sum + (squad.member_count || 0), 0) || '-' }}</small>
          </div>
          <div class="detail-grid">
            <div v-for="squad in squadsByCamp(camp)" :key="squad.id" class="detail-squad">
              <div class="squad-head">
                <strong>{{ squad.name }}</strong>
                <span>{{ squad.member_count }}人</span>
              </div>
              <label class="radio-field">
                <span>对讲频率</span>
                <input :value="squad.radio_channel || defaultRadioChannel(squad)" readonly />
              </label>
              <div class="squad-lock-row">
                <span class="lock-state" :class="{ locked: isSquadLocked(squad) }">{{ isSquadLocked(squad) ? '已锁定' : '未锁定' }}</span>
                <button class="btn secondary lock-btn" @click="toggleSquadLock(squad)">
                  {{ isSquadLocked(squad) ? '解除锁定' : '锁定小队' }}
                </button>
              </div>
              <select
                :value="joinJobs[squad.id] || ''"
                :class="{ 'select-placeholder': !joinJobs[squad.id] }"
                aria-label="选择职业"
                @change="joinJobs[squad.id] = $event.target.value"
              >
                <option value="">请选择职业</option>
                <option v-for="job in jobs" :key="job" :value="job">{{ job }}</option>
              </select>
              <button class="btn detail-sub-action" :class="{ secondary: isSquadLocked(squad) && !isMySquad(squad) }" @click="joinSquad(squad, joinJobs[squad.id])">
                {{ isSquadLocked(squad) && !isMySquad(squad) ? '小队已锁定' : '加入小队' }}
              </button>
              <button v-if="Number(squad.leader_user_id) === Number(me.id)" class="btn secondary detail-sub-action" @click="openLeaderDialog(squad)">转让队长</button>
              <div class="member-list">
                <div v-for="member in membersBySquad(camp, squad.squad_no)" :key="member.id" class="member">
                  {{ member.callsign }} / {{ member.job || '未选职业' }} <span v-if="Number(squad.leader_user_id) === Number(member.user_id)">队长</span>
                </div>
                <div v-if="membersBySquad(camp, squad.squad_no).length === 0" class="muted">暂无队员</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="detail.my_enrollment && unassignedMembers.length" class="squad-panel">
        <h3>未分配阵营 / 小队</h3>
        <p class="muted">这些人员已报名，但还没有进入阵营和小队。</p>
        <div class="unassigned-list">
          <div v-for="member in unassignedMembers" :key="member.id" class="unassigned-member">
            <div class="unassigned-name">
              <strong>{{ member.callsign || member.username }}</strong>
              <span>报名</span>
            </div>
            <template v-if="detail.is_activity_creator">
              <select v-model="memberAssignments[member.user_id].squadKey" class="compact-select">
                <option value="">未分配小队</option>
                <option v-for="squad in detail.squads" :key="squad.id" :value="`${squad.camp_no}:${squad.squad_no}`" :disabled="isSquadLocked(squad)">
                  阵营{{ squad.camp_no }} / {{ squad.name }}{{ isSquadLocked(squad) ? '（已锁定）' : '' }}
                </option>
              </select>
              <select v-model="memberAssignments[member.user_id].job" class="compact-select">
                <option value="">未选择职业</option>
                <option v-for="job in jobs" :key="job" :value="job">{{ job }}</option>
              </select>
              <button class="btn assign-btn" @click="assignMember(member)">分配</button>
            </template>
            <template v-else>
              <span class="unassigned-pill">未分配小队</span>
              <span class="unassigned-pill">未选择职业</span>
            </template>
          </div>
        </div>
      </section>

      <section v-if="detail.is_activity_creator" class="squad-panel activity-stats-panel">
        <h3>活动统计</h3>
        <p class="muted">按当前人员列表自动统计。</p>
        <div class="stat-box">
          <h4>职业数量</h4>
          <div class="job-stat-grid">
            <span v-for="item in jobStats" :key="item.name" class="job-stat-pill">{{ item.name }} <strong>{{ item.count }}</strong></span>
            <span class="job-stat-pill">报名总人数 <strong>{{ detail.enroll_count || 0 }}</strong></span>
          </div>
        </div>
      </section>
    </div>

    <div v-if="tab === 'activityRentals'" class="page">
      <h2>活动发射器租借</h2>
      <div class="activity-rental-grid">
        <div v-for="item in activityRentalItems" :key="item.id" class="rental-card activity-rental-card">
          <div class="rental-photo">
            <img v-if="item.photo_filename" :src="item.photo_filename" alt="发射器图片" />
            <span v-else>无照片</span>
          </div>
          <div class="rental-body">
            <h3>{{ item.name }}</h3>
            <p class="rental-meta">所有人：{{ item.owner_callsign || item.owner_name }} · 租金：{{ item.rent_fee }}</p>
            <button class="btn activity-rental-btn" :disabled="rentalButtonDisabled(item)" @click="rentLauncher(item)">
              {{ rentalButtonText(item) }}
            </button>
          </div>
        </div>
      </div>
      <div v-if="activityRentalItems.length === 0" class="empty-state">暂无可租借发射器</div>
    </div>

    <div v-if="tab === 'rentals'" class="page">
      <div class="section-title">
        <h2>我的发射器租赁</h2>
        <button class="btn" @click="openRentalDialog">新增</button>
      </div>
      <div class="rental-grid">
        <div v-for="item in rentalItems" :key="item.id" class="rental-card">
          <div class="rental-photo">
            <img v-if="item.photo_filename" :src="item.photo_filename" alt="发射器图片" />
            <span v-else>无照片</span>
          </div>
          <div class="rental-body">
            <h3>{{ item.name }}</h3>
            <p class="rental-meta">所有人：{{ me.callsign || me.username }} · 租金：{{ item.rent_fee }}</p>
            <p v-if="item.description" class="rental-desc">{{ item.description }}</p>
            <div class="rental-actions">
              <button class="rental-status" :class="{ off: !item.active }" @click="toggleRentalActive(item)">
                {{ item.active ? '下架' : '上架' }}
              </button>
              <button class="rental-delete" @click="deleteRental(item.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="rentalItems.length === 0" class="empty-state">暂无发射器出租信息</div>
    </div>

    <div v-if="tab === 'notifications'" class="page">
      <h2>通知</h2>
      <div v-for="notice in notifications" :key="notice.id" class="card notice-card" :class="{ unread: !notice.read_at }">
        <span v-if="!notice.read_at" class="notice-dot"></span>
        <h3>{{ notice.title }}</h3>
        <p>{{ notice.content }}</p>
        <p class="muted">{{ formatDateTime(notice.created_at) }}</p>
        <button v-if="notice.type === 'launcher_rental' && notice.rental_status === 'pending'" class="btn notice-action" @click="confirmRentalNotice(notice)">确认租借</button>
        <p v-else-if="notice.type === 'launcher_rental' && notice.rental_status === 'confirmed'" class="notice-status">已确认租借</p>
      </div>
      <div v-if="notifications.length === 0" class="empty-state">暂无通知</div>
    </div>

    <div v-if="tab === 'mine'" class="page">
      <div class="mine-page">
        <section class="mine-hero">
          <img v-if="me.avatar_url" class="profile-avatar" :src="me.avatar_url" alt="用户头像" />
          <div v-else class="profile-avatar fallback">{{ avatarText }}</div>
          <div class="mine-identity">
            <h2>{{ me.username }}</h2>
            <p>{{ me.callsign }}</p>
          </div>
        </section>

        <section class="mine-panel">
          <div class="panel-head">
            <div>
              <h3>账号设置</h3>
              <p class="muted">管理个人资料和登录密码</p>
            </div>
          </div>
          <div class="mine-action-grid">
            <button class="btn" @click="openProfileDialog">修改资料</button>
            <button class="btn" @click="openPasswordDialog">修改密码</button>
            <button class="btn" @click="logout">退出登录</button>
          </div>
        </section>

        <section class="mine-panel">
          <div class="panel-head">
            <div>
              <h3>邀请好友</h3>
              <p class="muted">复制注册链接或展示二维码给新用户注册</p>
            </div>
          </div>
          <div class="invite-code-box">
            <span>{{ me.invite_code }}</span>
            <button class="btn" @click="copyInvite">复制</button>
            <button class="btn" @click="toggleInviteQr">{{ showQr ? '收起二维码' : '展示二维码' }}</button>
          </div>
          <div v-if="showQr" class="qr-card">
            <img v-if="qrDataUrl" :src="qrDataUrl" alt="invite qr" />
            <span v-else class="muted">二维码生成中</span>
          </div>
        </section>
        </div>
    </div>

    <footer class="icp-footer">
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">浙ICP 备2026046394</a>
    </footer>

    <div class="tabs">
      <div class="tab" :class="{ active: tab === 'activities' }" @click="tab = 'activities'; selectedActivity = null; selectedPlan = null; loadActivities()">活动</div>
      <div class="tab" :class="{ active: tab === 'rentals' }" @click="tab = 'rentals'; loadRentals()">发射器租赁</div>
      <div class="tab" :class="{ active: tab === 'notifications' }" @click="openNotifications">
        通知<span v-if="unreadCount" class="tab-dot"></span>
      </div>
      <div class="tab" :class="{ active: tab === 'mine' }" @click="tab = 'mine'; loadMine()">我的</div>
    </div>

    <div v-if="showRentalDialog" class="modal">
      <div class="modal-backdrop" @click="showRentalDialog = false"></div>
      <div class="modal-panel">
        <div class="modal-head">
          <h2>新增发射器</h2>
          <button class="btn secondary" @click="showRentalDialog = false">关闭</button>
        </div>
        <input v-model="rentalForm.name" placeholder="名称" />
        <textarea v-model="rentalForm.description" maxlength="50" placeholder="发射器说明，50字以内" />
        <input v-model="rentalForm.rent_fee" placeholder="租金" />
        <input type="file" accept="image/*" @change="uploadRentalPhoto" />
        <img v-if="rentalForm.photo_filename" class="avatar-preview wide" :src="rentalForm.photo_filename" alt="发射器图片预览" />
        <button class="btn" style="width: 100%" @click="createRental">确认新增</button>
      </div>
    </div>

    <div v-if="showProfileDialog" class="modal">
      <div class="modal-backdrop" @click="showProfileDialog = false"></div>
      <div class="modal-panel">
        <div class="modal-head">
          <h2>修改资料</h2>
          <button class="btn secondary" @click="showProfileDialog = false">关闭</button>
        </div>
        <input v-model="profileForm.username" placeholder="用户名" />
        <input v-model="profileForm.callsign" placeholder="呼号" />
        <input type="file" accept="image/*" @change="uploadProfileAvatar" />
        <img v-if="profileForm.avatar_url" class="avatar-preview" :src="profileForm.avatar_url" alt="头像预览" />
        <button class="btn" style="width: 100%" @click="saveProfile">确认修改</button>
      </div>
    </div>

    <div v-if="showPasswordDialog" class="modal">
      <div class="modal-backdrop" @click="closePasswordDialog"></div>
      <div class="modal-panel">
        <div class="modal-head">
          <h2>{{ me.must_change_password ? '请先修改临时密码' : '修改密码' }}</h2>
          <button v-if="!me.must_change_password" class="btn secondary" @click="showPasswordDialog = false">关闭</button>
        </div>
        <p v-if="me.must_change_password" class="muted">当前使用的是管理员生成的临时密码。设置至少8位的新密码后才能继续使用。</p>
        <input v-model="passwordForm.password" type="password" placeholder="新密码" />
        <input v-model="passwordForm.confirm_password" type="password" placeholder="确认密码" />
        <button class="btn" style="width: 100%" @click="savePassword">确认修改</button>
      </div>
    </div>

    <div v-if="leaderDialog.show" class="modal">
      <div class="modal-backdrop" @click="leaderDialog.show = false"></div>
      <div class="modal-panel">
        <div class="modal-head">
          <h2>转让队长</h2>
          <button class="btn secondary" @click="leaderDialog.show = false">取消</button>
        </div>
        <select v-model="leaderDialog.selectedUserId" class="leader-select">
          <option v-for="member in transferLeaderCandidates(leaderDialog.squad)" :key="member.user_id" :value="String(member.user_id)">
            {{ member.callsign || member.username }} / {{ member.job || '未选职业' }}
          </option>
        </select>
        <button class="btn" style="width: 100%" @click="confirmTransferLeader">确认转让</button>
      </div>
    </div>

    <div v-if="confirmDialog.show" class="modal">
      <div class="modal-backdrop" @click="resolveConfirm(false)"></div>
      <div class="modal-panel confirm-panel">
        <h2>{{ confirmDialog.title }}</h2>
        <p>{{ confirmDialog.message }}</p>
        <div class="confirm-actions">
          <button class="btn secondary" @click="resolveConfirm(false)">取消</button>
          <button class="btn" @click="resolveConfirm(true)">确认</button>
        </div>
      </div>
    </div>

    </template>

    <div v-if="toast.show" class="toast" role="alert" aria-live="assertive">{{ toast.message }}</div>
  </div>
</template>

<script>
import { api, setErrorHandler, setToken, token } from './api'
import logoUrl from './assets/nbys-logo.png'
import QRCode from 'qrcode'

export default {
  emits: ['loading-start', 'ready'],
  data() {
    return {
      view: token() ? 'app' : new URLSearchParams(location.search).get('invite') ? 'register' : 'login',
      tab: 'activities',
      me: {},
      systemImages: {},
      loginForm: {},
      logoUrl,
      registerForm: { invite_code: new URLSearchParams(location.search).get('invite') || '' },
      activities: [],
      attendanceSummary: { present_count: 0, activity_total: 0 },
      selectedActivity: null,
      selectedPlan: null,
      planVoteForm: { date_option_ids: [], venue_ids: [], game_mode_ids: [] },
      detail: {},
      jobs: ['突击兵', '支援兵', '医疗兵', '狙击手', '弹药兵', '填线兵'],
      joinJobs: {},
      memberAssignments: {},
      rentalItems: [],
      activityRentalItems: [],
      rentalForm: {},
      profileForm: {},
      passwordForm: {},
      showRentalDialog: false,
      showProfileDialog: false,
      showPasswordDialog: false,
      showQr: false,
      qrDataUrl: '',
      notifications: [],
      toastTimer: null,
      toast: { show: false, message: '' },
      confirmDialog: { show: false, title: '确认操作', message: '', resolve: null },
      leaderDialog: { show: false, squad: null, selectedUserId: '' }
    }
  },
  computed: {
    camps() {
      return [...new Set((this.detail.squads || []).map(squad => squad.camp_no))]
    },
    unassignedMembers() {
      return (this.detail.members || []).filter(member => member.camp_no == null || member.squad_no == null)
    },
    jobStats() {
      const stats = this.jobs.map(name => ({ name, count: 0 }))
      const other = { name: '未选择职业', count: 0 }
      const map = new Map(stats.map(item => [item.name, item]))
      for (const member of this.detail.members || []) {
        const job = member.job || ''
        if (map.has(job)) map.get(job).count += 1
        else other.count += 1
      }
      return [...stats, other]
    },
    registerUrl() {
      return `${location.origin}${location.pathname}?invite=${this.me.invite_code || ''}`
    },
    avatarText() {
      return String(this.me.callsign || this.me.username || '甬').slice(0, 2)
    },
    unreadCount() {
      return this.notifications.filter(notice => !notice.read_at).length
    },
    showHeaderBack() {
      if (this.view !== 'app') return false
      return this.tab !== 'activities' || !!this.selectedActivity || !!this.selectedPlan
    },
    displayLogoUrl() {
      return this.systemImages.login_logo_url || this.logoUrl
    },
    authPageStyle() {
      const url = this.systemImages.login_background_url
      return url ? { backgroundImage: `linear-gradient(rgba(2, 6, 23, .34), rgba(2, 6, 23, .7)), url("${url}")` } : {}
    }
  },
  async mounted() {
    setErrorHandler(this.showToast)
    try {
      if (token()) {
        await Promise.all([this.loadSystemImages(), this.init()])
      } else {
        await this.loadSystemImages()
        await this.preloadImages([this.displayLogoUrl, this.systemImages.login_background_url])
      }
    } finally {
      this.$emit('ready')
    }
  },
  methods: {
    async loadSystemImages() {
      try {
        this.systemImages = await api('/api/public/system-settings/images')
      } catch (error) {
        this.systemImages = {}
      }
    },
    showToast(message) {
      if (!message) return
      clearTimeout(this.toastTimer)
      this.toast = { show: true, message }
      this.toastTimer = setTimeout(() => {
        this.toast.show = false
      }, 2200)
    },
    askConfirm(message, title = '确认操作') {
      return new Promise(resolve => {
        this.confirmDialog = { show: true, title, message, resolve }
      })
    },
    resolveConfirm(value) {
      const resolve = this.confirmDialog.resolve
      this.confirmDialog = { show: false, title: '确认操作', message: '', resolve: null }
      if (resolve) resolve(value)
    },
    async login() {
      let activityLoadingStarted = false
      try {
        const data = await api('/api/h5/auth/login', { method: 'POST', body: this.loginForm })
        setToken(data.token)
        this.view = 'app'
        if (data.must_change_password) {
          this.me = data
          this.showPasswordDialog = true
          this.showToast('请先修改临时密码')
          return
        }
        this.$emit('loading-start')
        activityLoadingStarted = true
        await this.init()
      } catch (error) {
        this.showToast(error.message || '登录失败')
      } finally {
        if (activityLoadingStarted) this.$emit('ready')
      }
    },
    async register() {
      try {
        if (!this.registerForm.username.trim()) return this.showToast('请输入用户名')
        if (!this.registerForm.password) return this.showToast('请输入密码')
        if (!this.registerForm.confirm_password) return this.showToast('请再次输入密码')
        if (this.registerForm.password !== this.registerForm.confirm_password) return this.showToast('两次密码不一致')
        await api('/api/h5/auth/register', { method: 'POST', body: this.registerForm })
        this.view = 'login'
        this.showToast('注册成功，请登录。')
      } catch (error) {
        this.showToast(error.message || '注册失败')
      }
    },
    async init() {
      const [me, dashboard] = await Promise.all([
        api('/api/h5/me'),
        api('/api/h5/activities/bootstrap')
      ])
      this.me = me
      this.profileForm = { username: this.me.username, callsign: this.me.callsign, avatar_url: this.me.avatar_url || '' }
      this.view = 'app'
      this.applyDashboard(dashboard)
      this.preloadImages([this.displayLogoUrl, this.me.avatar_url])
    },
    applyDashboard(dashboard = {}) {
      const activeActivities = (dashboard.activities || [])
        .filter(activity => ['报名中', '活动开始', '进行中'].includes(activity.display_status))
        .map(activity => ({ ...activity, record_kind: 'activity' }))
      const planning = (dashboard.plans || [])
        .map(plan => ({ ...plan, record_kind: 'plan', display_status: '策划中' }))
      this.activities = [...activeActivities, ...planning].sort((a, b) => this.sortTime(b.created_at) - this.sortTime(a.created_at))
      this.attendanceSummary = dashboard.attendance_summary || { present_count: 0, activity_total: 0 }
      this.notifications = dashboard.notifications || []
    },
    preloadImages(urls) {
      const uniqueUrls = [...new Set(urls.filter(Boolean))]
      return Promise.all(uniqueUrls.map(url => new Promise(resolve => {
        const image = new Image()
        let completed = false
        const finish = () => {
          if (completed) return
          completed = true
          window.clearTimeout(timeout)
          resolve()
        }
        const timeout = window.setTimeout(finish, 6000)
        image.onload = finish
        image.onerror = finish
        image.src = url
        if (image.complete) finish()
      })))
    },
    loadActivities() {
      return Promise.all([api('/api/h5/activities'), api('/api/h5/activity-plans')]).then(([activities, plans]) => {
        const activeActivities = activities
          .filter(activity => ['报名中', '活动开始', '进行中'].includes(activity.display_status))
          .map(activity => ({ ...activity, record_kind: 'activity' }))
        const planning = plans.map(plan => ({ ...plan, record_kind: 'plan', display_status: '策划中' }))
        this.activities = [...activeActivities, ...planning].sort((a, b) => this.sortTime(b.created_at) - this.sortTime(a.created_at))
      })
    },
    loadAttendanceSummary() {
      return api('/api/h5/attendance/my-summary').then(data => { this.attendanceSummary = data || { present_count: 0, activity_total: 0 } })
    },
    sortTime(value) {
      return value ? new Date(String(value).replace(' ', 'T')).getTime() || 0 : 0
    },
    goHome() {
      if (this.view === 'app') {
        this.tab = 'activities'
        this.selectedActivity = null
        this.selectedPlan = null
        this.loadActivities()
      }
    },
    statusLabel(status) {
      return status === '活动开始' ? '进行中' : status
    },
    statusClass(status) {
      if (status === '报名中') return 'signup'
      if (status === '活动开始' || status === '进行中') return 'running'
      if (status === '策划中') return 'planning'
      return ''
    },
    openHomeCard(item) {
      if (item.record_kind === 'activity') return this.openActivity(item.id)
      this.selectedActivity = null
      this.selectedPlan = item
      this.planVoteForm = this.planVoteFormFromPlan(item)
    },
    planVoteFormFromPlan(plan) {
      return {
        date_option_ids: [...(plan.my_date_option_ids || [])],
        venue_ids: [...(plan.my_venue_ids || [])],
        game_mode_ids: [...(plan.my_game_mode_ids || [])]
      }
    },
	    planNames(items, key) {
	      return (items || []).map(item => String(item[key] || '')).filter(Boolean).join('、')
	    },
    planDateNames(items) {
	      return (items || []).map(item => {
	        const date = String(item.date || '').trim()
	        const remark = String(item.remark || '').trim()
	        if (!date) return ''
	        return remark ? `${date}（${remark}）` : date
	      }).filter(Boolean).join('、')
	    },
    displayVenueName(item) {
      return item?.venue_name || item?.location || '场地待配置'
    },
    displayVenueAddress(item) {
      return item?.venue_address || item?.location || ''
    },
    amapUrl(keyword) {
      return `https://uri.amap.com/search?keyword=${encodeURIComponent(keyword || '')}`
    },
    formatTimeRange(start, end) {
      if (!start && !end) return ''
      return `${this.formatDateTime(start)} - ${this.formatDateTime(end)}`
    },
    formatDateTime(value) {
      if (!value) return ''
      return String(value).replace('T', ' ').slice(0, 16)
    },
    async openActivity(id) {
      this.selectedPlan = null
      this.selectedActivity = id
      this.detail = await api(`/api/h5/activities/${id}`)
      this.prepareJoinJobs()
      this.prepareMemberAssignments()
    },
    async submitPlanVote() {
      const body = this.planVoteForm
      if (!body.date_option_ids.length && !body.venue_ids.length && !body.game_mode_ids.length) {
        return this.showToast('请至少选择一个投票选项')
      }
      if (!(await this.askConfirm('确认提交投票吗？提交后不得修改。'))) return
      await api(`/api/h5/activity-plans/${this.selectedPlan.id}/vote`, { method: 'POST', body })
      await this.loadActivities()
      this.selectedPlan = this.activities.find(item => item.record_kind === 'plan' && item.id === this.selectedPlan.id) || { ...this.selectedPlan, voted: true }
      this.planVoteForm = this.planVoteFormFromPlan(this.selectedPlan)
      this.showToast('已投票')
    },
    enroll() {
      return api(`/api/h5/activities/${this.selectedActivity}/enroll`, { method: 'POST' }).then(() => this.openActivity(this.selectedActivity))
    },
    cancelEnroll() {
      return api(`/api/h5/activities/${this.selectedActivity}/enroll`, { method: 'DELETE' }).then(() => this.openActivity(this.selectedActivity))
    },
    checkin() {
      return api(`/api/h5/activities/${this.selectedActivity}/checkin`, { method: 'POST' }).then(() => this.openActivity(this.selectedActivity))
    },
    squadsByCamp(camp) {
      return (this.detail.squads || []).filter(squad => squad.camp_no === camp)
    },
    membersBySquad(camp, squadNo) {
      return (this.detail.members || []).filter(member => member.camp_no === camp && member.squad_no === squadNo)
    },
    membersByCamp(camp) {
      return (this.detail.members || []).filter(member => member.camp_no === camp)
    },
    isSquadLocked(squad) {
      return squad?.locked === true || squad?.locked === 1 || String(squad?.locked) === '1' || String(squad?.locked).toLowerCase() === 'true'
    },
    isSquadLeader(squad) {
      return Number(squad?.leader_user_id) === Number(this.me.id)
    },
    isMySquad(squad) {
      const mine = this.detail.my_enrollment || {}
      return Number(mine.camp_no) === Number(squad?.camp_no) && Number(mine.squad_no) === Number(squad?.squad_no)
    },
    defaultRadioChannel(squad) {
      const campNo = Number(squad?.camp_no || 1)
      const squadNo = Number(squad?.squad_no || 1)
      return `${434 + campNo}.${String(squadNo * 100).padStart(3, '0')}`
    },
    prepareMemberAssignments() {
      const assignments = {}
      for (const member of this.detail.members || []) {
        assignments[member.user_id] = {
          squadKey: member.camp_no != null && member.squad_no != null ? `${member.camp_no}:${member.squad_no}` : '',
          job: member.job || ''
        }
      }
      this.memberAssignments = assignments
    },
    prepareJoinJobs() {
      const previous = this.joinJobs
      this.joinJobs = Object.fromEntries(
        (this.detail.squads || []).map(squad => [squad.id, previous[squad.id] || ''])
      )
    },
    async assignMember(member) {
      const assignment = this.memberAssignments[member.user_id] || {}
      if (!assignment.squadKey) return this.showToast('请先选择小队')
      if (!assignment.job) return this.showToast('请先选择职业')
      const [campNo, squadNo] = assignment.squadKey.split(':').map(value => Number(value))
      await api(`/api/h5/activities/${this.selectedActivity}/members/${member.user_id}/squad`, {
        method: 'PUT',
        body: { camp_no: campNo, squad_no: squadNo, job: assignment.job }
      })
      await this.openActivity(this.selectedActivity)
      this.showToast('已分配')
    },
    joinSquad(squad, job) {
      if (this.isSquadLocked(squad) && !this.isMySquad(squad)) {
        this.showToast('小队已锁定，无法加入')
        return
      }
      if (!job) {
        this.showToast('请先选择职业')
        return
      }
      return api(`/api/h5/activities/${this.selectedActivity}/squad`, { method: 'PUT', body: { camp_no: squad.camp_no, squad_no: squad.squad_no, job } }).then(() => this.openActivity(this.selectedActivity))
    },
    async toggleSquadLock(squad) {
      if (!this.isSquadLeader(squad)) {
        this.showToast('只有队长可以锁定小队')
        return
      }
      const locked = !this.isSquadLocked(squad)
      await api(`/api/h5/activities/${this.selectedActivity}/squads/${squad.id}/lock`, { method: 'PUT', body: { locked } })
      await this.openActivity(this.selectedActivity)
      this.showToast(locked ? '小队已锁定' : '小队已解除锁定')
    },
    openLeaderDialog(squad) {
      const candidates = this.transferLeaderCandidates(squad)
      if (!candidates.length) {
        this.showToast('暂无可转让队员')
        return
      }
      this.leaderDialog = { show: true, squad, selectedUserId: String(candidates[0].user_id) }
    },
    transferLeaderCandidates(squad) {
      if (!squad) return []
      return this.membersBySquad(squad.camp_no, squad.squad_no)
        .filter(member => Number(member.user_id) !== Number(this.me.id))
    },
    async confirmTransferLeader() {
      const userId = this.leaderDialog.selectedUserId
      if (!userId) return this.showToast('请选择转让对象')
      await api(`/api/h5/activities/${this.selectedActivity}/squad/leader`, { method: 'PUT', body: { user_id: userId } })
      this.leaderDialog = { show: false, squad: null, selectedUserId: '' }
      await this.openActivity(this.selectedActivity)
      this.showToast('已转让队长')
    },
    openActivityRentals() {
      this.tab = 'activityRentals'
      return api(`/api/h5/activities/${this.selectedActivity}/launcher-rentals`).then(data => { this.activityRentalItems = data })
    },
    rentalButtonDisabled(item) {
      return Number(item.created_by_id) === Number(this.me.id) || !!item.rental_status
    },
    rentalButtonText(item) {
      if (Number(item.created_by_id) === Number(this.me.id)) return '不可租借自己'
      if (item.rental_status) return '已租赁'
      return '租借'
    },
    rentLauncher(item) {
      if (Number(item.created_by_id) === Number(this.me.id)) return this.showToast('不可租借自己的发射器')
      return api(`/api/h5/activities/${this.selectedActivity}/launcher-rentals/${item.id}`, { method: 'POST' }).then(() => this.openActivityRentals())
    },
    loadRentals() {
      return api('/api/h5/launcher-rentals/my-items').then(data => { this.rentalItems = data })
    },
    openRentalDialog() {
      this.rentalForm = {}
      this.showRentalDialog = true
    },
    async uploadRegisterAvatar(event) {
      try {
        const data = await this.uploadImage(event)
        if (data) this.registerForm.avatar_url = data.url
      } catch (error) {
        this.showToast(error.message || '头像上传失败')
      }
    },
    async uploadProfileAvatar(event) {
      try {
        const data = await this.uploadImage(event)
        if (data) this.profileForm.avatar_url = data.url
      } catch (error) {
        this.showToast(error.message || '头像上传失败')
      }
    },
    async uploadRentalPhoto(event) {
      try {
        const data = await this.uploadImage(event)
        if (data) this.rentalForm.photo_filename = data.url
      } catch (error) {
        this.showToast(error.message || '图片上传失败')
      }
    },
    async uploadImage(event) {
      let file = event.target.files[0]
      if (!file) return
      file = await this.compressImage(file, 300 * 1024)
      const body = new FormData()
      body.append('file', file)
      return api('/api/h5/files/upload', { method: 'POST', body })
    },
    compressImage(file, maxSize) {
      return new Promise(resolve => {
        if (file.size <= maxSize || !file.type.startsWith('image/')) return resolve(file)
        const image = new Image()
        image.onload = () => {
          const canvas = document.createElement('canvas')
          const scale = Math.min(1, 1200 / Math.max(image.width, image.height))
          canvas.width = Math.floor(image.width * scale)
          canvas.height = Math.floor(image.height * scale)
          canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height)
          let quality = .85
          const next = () => canvas.toBlob(blob => {
            if (!blob) return resolve(file)
            if (blob.size <= maxSize || quality <= .35) return resolve(new File([blob], file.name, { type: 'image/jpeg' }))
            quality -= .1
            next()
          }, 'image/jpeg', quality)
          next()
        }
        image.src = URL.createObjectURL(file)
      })
    },
    createRental() {
      return api('/api/h5/launcher-rentals/my-items', { method: 'POST', body: this.rentalForm }).then(() => {
        this.rentalForm = {}
        this.showRentalDialog = false
        this.loadRentals()
      })
    },
    offRental(id) {
      return api(`/api/h5/launcher-rentals/my-items/${id}/off`, { method: 'PUT' }).then(() => this.loadRentals())
    },
    onRental(id) {
      return api(`/api/h5/launcher-rentals/my-items/${id}/on`, { method: 'PUT' }).then(() => this.loadRentals())
    },
    async toggleRentalActive(item) {
      const message = item.active ? '确认下架该发射器？' : '确认上架该发射器？'
      if (!(await this.askConfirm(message))) return
      return item.active ? this.offRental(item.id) : this.onRental(item.id)
    },
    async deleteRental(id) {
      if (!(await this.askConfirm('确认删除该发射器出租信息？'))) return
      return api(`/api/h5/launcher-rentals/my-items/${id}`, { method: 'DELETE' }).then(() => this.loadRentals())
    },
    loadMine() {
      return api('/api/h5/notifications').then(data => { this.notifications = data })
    },
    async openNotifications() {
      this.tab = 'notifications'
      await this.loadMine()
      if (this.unreadCount) {
        await api('/api/h5/notifications/read-all', { method: 'PUT' })
        await this.loadMine()
      }
    },
    confirmRentalNotice(notice) {
      return api(`/api/h5/launcher-rentals/${notice.rental_action_id || notice.related_id}/confirm`, { method: 'PUT' }).then(async () => {
        this.showToast('已确认租借')
        await this.loadMine()
      })
    },
    openProfileDialog() {
      this.profileForm = { username: this.me.username, callsign: this.me.callsign, avatar_url: this.me.avatar_url || '' }
      this.showProfileDialog = true
    },
    saveProfile() {
      return api('/api/h5/me/profile', { method: 'PUT', body: this.profileForm }).then(async () => {
        this.showProfileDialog = false
        await this.init()
        this.showToast('已修改')
      })
    },
    openPasswordDialog() {
      this.passwordForm = {}
      this.showPasswordDialog = true
    },
    closePasswordDialog() {
      if (!this.me.must_change_password) this.showPasswordDialog = false
    },
    savePassword() {
      if (String(this.passwordForm.password || '').length < 8) return this.showToast('密码至少需要8位')
      if (this.passwordForm.password !== this.passwordForm.confirm_password) return this.showToast('两次密码不一致')
      return api('/api/h5/me/password', { method: 'PUT', body: this.passwordForm }).then(() => {
        this.showPasswordDialog = false
        this.passwordForm = {}
        this.me.must_change_password = false
        this.showToast('密码已修改')
        return this.init()
      })
    },
    copyInvite() {
      navigator.clipboard.writeText(this.registerUrl)
      this.showToast('已复制注册链接')
    },
    async toggleInviteQr() {
      this.showQr = !this.showQr
      if (this.showQr && !this.qrDataUrl) {
        this.qrDataUrl = await QRCode.toDataURL(this.registerUrl, {
          width: 180,
          margin: 1,
          color: { dark: '#020617', light: '#ffffff' }
        })
      }
    },
    logout() {
      setToken('')
      this.view = 'login'
      this.tab = 'activities'
      this.selectedActivity = null
      this.selectedPlan = null
      this.me = {}
    }
  }
}
</script>
