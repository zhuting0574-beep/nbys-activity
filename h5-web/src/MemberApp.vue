<template>
  <div class="member-app">
    <header v-if="view === 'app'" class="topbar">
      <a class="brand" href="#" @click.prevent="goHome">
        <img class="brand-logo" :src="displayLogoUrl" alt="甬士 Logo" />
        <span>宁波甬士活动管理系统</span>
      </a>
      <div class="header-actions">
        <button v-if="showHeaderBack" class="header-back" @click="goHome">返回首页</button>
        <button v-else-if="showSiteHome" class="header-back" @click="goSiteHome">返回首页</button>
      </div>
    </header>

    <div v-if="view === 'login'" class="page auth-page" :style="authPageStyle">
      <form class="auth-card" @submit.prevent="login()">
        <span class="page-eyebrow">NBYS MEMBER ACCESS</span>
        <h2>队员登录</h2>
        <p class="auth-intro">活动、出勤、租赁与个人资料统一入口。</p>
        <label class="member-field"><span>名字 / 呼号</span><input v-model="loginForm.account" required /></label>
        <label class="member-field"><span>密码</span><input v-model="loginForm.password" type="password" required /></label>
        <button class="btn" style="width: 100%" type="submit" :disabled="loginSubmitting">{{ loginSubmitting ? '登录中…' : '登录' }}</button>
        <p class="auth-switch">没有账号？<a @click="view = 'register'">注册账号</a></p>
      </form>
    </div>

    <div v-else-if="view === 'register'" class="page auth-page" :style="authPageStyle">
      <form class="auth-card" @submit.prevent="register()">
        <span class="page-eyebrow">NBYS MEMBER ACCESS</span>
        <h2>加入宁波甬士</h2>
        <p class="auth-intro">创建队员档案，正式审核通过后开放全部功能。</p>
        <label class="member-field"><span>用户名</span><input v-model="registerForm.username" required /></label>
        <label class="member-field"><span>呼号</span><input v-model="registerForm.callsign" placeholder="选填" /></label>
        <label class="member-field"><span>密码</span><input v-model="registerForm.password" type="password" required /></label>
        <label class="member-field"><span>确认密码</span><input v-model="registerForm.confirm_password" type="password" /></label>
        <label class="member-field"><span>邀请码</span><input v-model="registerForm.invite_code" placeholder="选填" /></label>
        <p class="muted">用户名、密码为必填项</p>
        <label class="upload-field">
          <span>头像</span>
          <input type="file" accept="image/*" @change="uploadRegisterAvatar" />
        </label>
        <img v-if="registerForm.avatar_url" class="avatar-preview" :src="registerForm.avatar_url" alt="头像预览" />
        <button class="btn" style="width: 100%" type="submit">注册</button>
        <p class="auth-switch"><a @click="view = 'login'">返回登录</a></p>
      </form>
    </div>

    <template v-else>
    <div v-if="tab === 'activities' && !selectedActivity && !selectedPlan">
      <div class="page activity-home-page">
        <section
          v-if="showEscapeEntryBanner"
          class="escape-entry-banner"
          :style="escapeEntryStyle"
          role="button"
          tabindex="0"
          aria-label="进入战区"
          @click="openEscape"
          @keydown.enter="openEscape"
          @keydown.space.prevent="openEscape"
        >
        </section>
        <div class="member-section-head">
          <div><span class="page-eyebrow">ACTIVE OPERATIONS</span><h2>近期活动</h2></div>
          <span>{{ activities.length }} 项进行中</span>
        </div>
        <div class="activity-list">
          <div v-for="activity in activities" :key="`${activity.record_kind}-${activity.id}`" class="card" :class="{ 'plan-card': activity.record_kind === 'plan' }" @click="openHomeCard(activity)">
            <div class="activity-card-media">
              <img class="banner" :src="activity.banner_url || defaultActivityBanner" loading="lazy" decoding="async" />
              <span class="status" :class="statusClass(activity.display_status)">{{ statusLabel(activity.display_status) }}</span>
            </div>
            <div class="activity-card-body">
              <h3>{{ activity.name }}</h3>
              <div v-if="activity.record_kind === 'activity'" class="card-meta activity-meta-grid">
                <p><span>活动时间</span><strong>{{ formatDateTime(activity.start_at) }}</strong></p>
                <p><span>报名进度</span><strong>{{ activity.enroll_count || 0 }} / {{ activity.signup_limit || '-' }}</strong></p>
                <p><span>活动地点</span><strong>{{ displayVenueName(activity) }}</strong></p>
                <p><span>发起人</span><strong>{{ activity.creator_name || '未设置' }}</strong></p>
              </div>
              <div v-else class="card-meta activity-meta-grid">
                <p><span>投票截止</span><strong>{{ formatDateTime(activity.vote_deadline) }}</strong></p>
                <p><span>已投票</span><strong>{{ activity.voter_count || 0 }} 人</strong></p>
                <p><span>可选场地</span><strong>{{ planNames(activity.venues, 'name') || '待配置' }}</strong></p>
                <p><span>发起人</span><strong>{{ activity.creator_name || '未设置' }}</strong></p>
              </div>
              <div v-if="activity.record_kind === 'activity'" class="activity-progress" aria-hidden="true"><i :style="{ width: activityProgress(activity) }"></i></div>
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
          <div class="vote-summary-line">
            <span>总投票人数：{{ selectedPlan.voter_count || 0 }}</span>
            <div class="voter-avatars" aria-label="已投票人员">
              <template v-for="voter in selectedPlan.voters || []" :key="voter.id">
                <img v-if="voter.avatar_url" class="voter-avatar" :src="voter.avatar_url" :alt="voter.callsign || voter.username || '投票人员'" />
                <span v-else class="voter-avatar fallback">{{ shortName(voter.callsign || voter.username) }}</span>
              </template>
              <span v-if="!(selectedPlan.voters || []).length" class="muted">暂无投票</span>
            </div>
          </div>
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
      <button class="btn secondary plan-vote-btn" @click="openShareDialog">分享策划</button>
    </div>

    <div v-if="tab === 'activities' && selectedActivity" class="page detail-page">
      <section class="detail-card">
        <img class="detail-banner" :src="detail.banner_url || defaultActivityBanner" loading="lazy" decoding="async" />
        <h2>{{ detail.name }}</h2>
        <p class="detail-time">{{ formatTimeRange(detail.start_at, detail.end_at) }} · {{ displayVenueName(detail) }}</p>
        <p class="detail-copy">发起人：{{ detail.creator_name || '未设置' }}</p>
        <div v-if="hasActivityVoteSummary(detail)" class="vote-summary-line detail-vote-summary">
          <span>总投票人数：{{ detail.voter_count || 0 }}</span>
          <div class="voter-avatars" aria-label="已投票人员">
            <template v-for="voter in detail.voters || []" :key="voter.id">
              <img v-if="voter.avatar_url" class="voter-avatar" :src="voter.avatar_url" :alt="voter.callsign || voter.username || '投票人员'" />
              <span v-else class="voter-avatar fallback">{{ shortName(voter.callsign || voter.username) }}</span>
            </template>
            <span v-if="!(detail.voters || []).length" class="muted">暂无投票</span>
          </div>
        </div>
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
          <button v-if="canManageActivities" class="btn secondary detail-main-action admin-entry" @click="goAdminActivity">活动管理</button>
          <button class="btn secondary detail-main-action" @click="openShareDialog">分享活动</button>
          <button v-if="detail.display_status === '报名中' && !detail.my_enrollment" class="btn detail-main-action" @click="startEnroll">报名</button>
          <button v-if="detail.display_status === '报名中' && detail.my_enrollment && isWeeklyActivity(detail)" class="btn secondary detail-main-action" @click="openEnrollmentDialog">修改人数</button>
          <button v-if="canCheckinActivity(detail) && detail.my_enrollment && !detail.checkin?.present" class="btn detail-main-action" :disabled="checkinSubmitting" @click="openCheckinDialog()">
            签到
          </button>
          <button v-if="detail.can_show_checkin_qr && canCheckinActivity(detail) && canUseQrCheckin(detail)" class="btn secondary detail-main-action" @click="showCheckinQr">签到二维码</button>
          <button v-if="detail.checkin?.present" class="btn secondary detail-main-action" disabled>已签到</button>
          <button v-if="detail.my_enrollment" class="btn secondary detail-main-action" @click="openActivityRentals">发射器租赁</button>
          <button v-if="detail.my_enrollment && !detail.checkin?.present" class="btn danger detail-main-action" @click="cancelEnroll">取消报名</button>
        </div>
      </section>

      <section v-if="(detail.my_enrollment || detail.is_activity_creator) && isWeeklyActivity(detail)" class="squad-panel">
        <h3>已报名人员</h3>
        <p class="muted">共 {{ detail.enroll_count || 0 }} 人报名</p>
        <div class="weekly-member-list">
          <div v-for="member in detail.members" :key="member.id" class="weekly-member">
            <strong>{{ member.callsign || member.username }}</strong>
            <div class="weekly-member-meta">
              <span class="weekly-member-status" :class="{ checked: member.checked_in }">
                {{ member.checked_in ? '已签到' : '已报名' }}
              </span>
              <span class="weekly-member-count">共 {{ participantCount(member) }} 人</span>
            </div>
          </div>
          <div v-if="!detail.members?.length" class="muted">暂无报名人员</div>
        </div>
      </section>

      <section v-if="(detail.my_enrollment || detail.is_activity_creator) && !isWeeklyActivity(detail)" class="squad-panel">
        <h3>阵营 / 小队 / 人员列表</h3>
        <p class="muted">发起人管理全部小队；队长管理本队；普通成员只能在未锁定时修改自己或更换小队。</p>
        <div v-for="camp in camps" :key="camp" class="camp-block">
          <div class="camp-head">
            <span>阵营 {{ camp }}</span>
            <small>{{ membersByCamp(camp).length }}/{{ squadsByCamp(camp).reduce((sum, squad) => sum + (squad.member_count || 0), 0) || '-' }}</small>
          </div>
          <div class="detail-grid">
            <div v-for="squad in squadsByCamp(camp)" :key="squad.id" class="detail-squad">
              <div class="squad-head">
                <strong>{{ squad.name }}</strong>
                <span>{{ squad.member_count }}人 · 队长：{{ squadLeaderName(squad) }}</span>
              </div>
              <label class="radio-field">
                <span>对讲频率</span>
                <div class="squad-setting-line">
                  <input v-model="squadEdits[squad.id].radio_channel" :readonly="!canManageSquad(squad)" />
                  <button v-if="canManageSquad(squad)" class="btn secondary compact-action" @click="saveSquadSettings(squad)">保存频率</button>
                </div>
              </label>
              <div class="squad-lock-row">
                <span class="lock-state" :class="{ locked: isSquadLocked(squad) }">{{ isSquadLocked(squad) ? '已锁定：成员不可改职业、退出或换队' : '未锁定' }}</span>
                <button v-if="canManageSquad(squad)" class="btn secondary lock-btn" @click="toggleSquadLock(squad)">
                  {{ isSquadLocked(squad) ? '解除锁定' : '锁定小队' }}
                </button>
              </div>
              <div v-if="detail.is_activity_creator && membersBySquad(camp, squad.squad_no).length" class="leader-setting">
                <select v-model="squadEdits[squad.id].leaderUserId" class="compact-select">
                  <option v-for="member in membersBySquad(camp, squad.squad_no)" :key="member.user_id" :value="String(member.user_id)">{{ member.callsign || member.username }} / {{ member.job || '未选职业' }}</option>
                </select>
                <button class="btn secondary compact-action" @click="saveSquadLeader(squad)">设置队长</button>
              </div>
              <div v-if="detail.my_enrollment && !detail.is_activity_creator && !isMySquad(squad)" class="join-squad-row">
                <select v-model="joinJobs[squad.id]" class="compact-select" :disabled="cannotSelfJoin(squad)">
                  <option value="">请选择职业</option>
                  <option v-for="job in availableJobs" :key="job" :value="job">{{ job }}</option>
                </select>
                <button class="btn compact-action" :disabled="cannotSelfJoin(squad)" @click="joinSquad(squad, joinJobs[squad.id])">{{ selfJoinLabel(squad) }}</button>
              </div>
              <div class="member-list">
                <div v-for="member in membersBySquad(camp, squad.squad_no)" :key="member.id" class="squad-member-row">
                  <div class="member-identity"><strong>{{ member.callsign || member.username }}</strong><span v-if="isMemberLeader(squad, member)" class="leader-badge">队长</span></div>
                  <template v-if="detail.is_activity_creator">
                    <select v-model="memberAssignments[member.user_id].squadKey" class="compact-select">
                      <option v-for="target in detail.squads" :key="target.id" :value="`${target.camp_no}:${target.squad_no}`">阵营{{ target.camp_no }} / {{ target.name }}</option>
                    </select>
                    <select v-model="memberAssignments[member.user_id].job" class="compact-select"><option value="" disabled>请选择职业</option><option v-for="job in availableJobs" :key="job" :value="job">{{ job }}</option></select>
                    <button class="btn secondary compact-action" @click="saveMemberAssignment(member)">保存</button>
                    <button class="btn danger compact-action" :disabled="isMemberLeader(squad, member)" @click="removeMember(squad, member)">踢出</button>
                  </template>
                  <template v-else-if="canEditMemberJob(squad, member)">
                    <select v-model="memberAssignments[member.user_id].job" class="compact-select" :disabled="selfMemberLocked(squad, member)"><option value="" disabled>请选择职业</option><option v-for="job in availableJobs" :key="job" :value="job">{{ job }}</option></select>
                    <button class="btn secondary compact-action" :disabled="selfMemberLocked(squad, member)" @click="saveMemberJob(member)">保存职业</button>
                    <button v-if="isSquadLeader(squad) && !isSelf(member)" class="btn danger compact-action" @click="removeMember(squad, member)">踢出</button>
                    <button v-if="isSelf(member) && !isSquadLeader(squad)" class="btn danger compact-action" :disabled="isSquadLocked(squad)" @click="removeMember(squad, member, true)">退出小队</button>
                  </template>
                  <span v-else class="member-job-text">{{ member.job || '未选职业' }}</span>
                </div>
                <div v-if="membersBySquad(camp, squad.squad_no).length === 0" class="muted">暂无队员</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="!isWeeklyActivity(detail) && (detail.my_enrollment || detail.is_activity_creator) && unassignedMembers.length" class="squad-panel">
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
                <option v-for="squad in detail.squads" :key="squad.id" :value="`${squad.camp_no}:${squad.squad_no}`">
                  阵营{{ squad.camp_no }} / {{ squad.name }}{{ isSquadLocked(squad) ? '（已锁定）' : '' }}
                </option>
              </select>
              <select v-model="memberAssignments[member.user_id].job" class="compact-select">
                <option value="">未选择职业</option>
                <option v-for="job in availableJobs" :key="job" :value="job">{{ job }}</option>
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

      <section v-if="showActivityStats(detail)" class="squad-panel activity-stats-panel">
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
      <div class="member-page-hero"><span class="page-eyebrow">FIELD EQUIPMENT</span><h2>活动发射器</h2><p>为当前活动选择可租用设备</p></div>
      <div class="rental-notice">
        <p>发射器租赁默认只有发射器+头灯接收器，是否含瞄具和头盔会在说明里描述。</p>
        <p>发射器租赁默认不提供电池，请自备XT30 11.1V电池*1 ， CR123A 3.7V 头灯电池*2</p>
        <p>发射器如因使用不当造成物理损坏，需要照市场价赔偿。</p>
      </div>
      <div class="activity-rental-grid">
        <div v-for="item in activityRentalItems" :key="item.id" class="rental-card activity-rental-card">
          <button v-if="item.photo_filename" type="button" class="rental-photo rental-photo-button" aria-label="查看发射器大图" @click="openRentalPreview(item)">
            <img :src="item.photo_filename" :alt="`${item.name || '发射器'}图片`" />
          </button>
          <div v-else class="rental-photo"><span>无照片</span></div>
          <div class="rental-body">
            <h3>{{ item.name }}</h3>
            <p class="rental-meta">所有人：{{ item.owner_callsign || item.owner_name }} · 租金：{{ item.rent_fee }}</p>
            <p class="rental-desc activity-rental-desc">{{ item.description || '暂无发射器说明' }}</p>
            <p v-if="item.rental_status" class="rental-renter">租赁者：{{ item.renter_callsign || item.renter_name }}</p>
            <button v-if="isMyLauncherRental(item)" class="btn danger activity-rental-btn" @click="cancelLauncherRental(item)">
              取消租赁
            </button>
            <button v-else class="btn activity-rental-btn" :disabled="rentalButtonDisabled(item)" @click="rentLauncher(item)">
              {{ rentalButtonText(item) }}
            </button>
          </div>
        </div>
      </div>
      <div v-if="activityRentalItems.length === 0" class="empty-state">暂无可租借发射器</div>
    </div>

    <div v-if="tab === 'rentals'" class="page">
      <div class="member-page-hero launcher-arsenal-hero"><span class="page-eyebrow">LAUNCHER RENTAL</span><h2>我的发射器</h2><p>维护个人设备与出租状态</p></div>
      <div class="section-title">
        <h2>设备列表</h2>
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
            <p class="rental-meta">所有人：{{ me.callsign || me.username }} · 租金：{{ item.rent_fee }} · {{ item.active ? '已上架' : '已下架' }}</p>
            <p v-if="item.description" class="rental-desc">{{ item.description }}</p>
            <div class="rental-actions">
              <button class="rental-edit" @click="openRentalDialog(item)">编辑</button>
              <button class="rental-delete" @click="deleteRental(item.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="rentalItems.length === 0" class="empty-state">暂无发射器出租信息</div>
    </div>

    <div v-if="tab === 'notifications'" class="page">
      <div class="member-page-hero launcher-arsenal-hero"><span class="page-eyebrow">MESSAGE CENTER</span><h2>通知</h2><p>活动、租赁与系统动态</p></div>
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
          <img v-if="me.avatar_url" class="profile-avatar" :src="remoteFirstAvatar(me.avatar_url)" alt="用户头像" @error="fallbackAvatar" />
          <div v-else class="profile-avatar fallback">{{ avatarText }}</div>
          <div class="mine-identity">
            <span class="page-eyebrow">MEMBER DOSSIER</span>
            <h2>{{ me.callsign || '未设置呼号' }}</h2>
            <p>{{ me.username }}</p>
          </div>
        </section>

        <section class="mine-panel">
          <div class="panel-head">
            <div>
              <h3>{{ attendanceMatrix.year || currentYear }} 年出勤</h3>
              <p class="muted">向左滑动查看本年度每一次活动</p>
            </div>
          </div>
          <div v-if="attendanceMatrixLoading" class="attendance-state">出勤记录加载中…</div>
          <div v-else-if="attendanceMatrixError" class="attendance-state error">
            <span>{{ attendanceMatrixError }}</span>
            <button class="btn secondary" @click="loadAttendanceMatrix">重新加载</button>
          </div>
          <div v-else-if="!attendanceMatrix.events.length" class="attendance-state">本年度暂无活动</div>
          <div
            v-else
            class="attendance-table-scroll"
            :class="{ dragging: attendanceDrag.active }"
            @pointerdown="startAttendanceDrag"
            @pointermove="moveAttendanceDrag"
            @pointerup="stopAttendanceDrag"
            @pointercancel="stopAttendanceDrag"
            @pointerleave="stopAttendanceDrag"
          >
            <table class="attendance-table">
              <thead>
                <tr>
                  <th class="attendance-count sticky-count">出勤次数</th>
                  <th v-for="event in attendanceMatrix.events" :key="event.id" class="attendance-event-head">
                    <strong>{{ event.name }}</strong>
                    <span>{{ formatAttendanceDate(event.event_date) }}</span>
                    <span>{{ event.location || '地点待定' }}</span>
                    <span>{{ event.organizer || '组织者待定' }}</span>
                    <em v-if="event.activity_region">{{ event.activity_region }}</em>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="attendance-count sticky-count">{{ attendanceMatrix.present_count }}</td>
                  <td v-for="event in attendanceMatrix.events" :key="event.id" class="attendance-mark-cell">
                    <span v-if="Number(event.attended) === 1" class="attendance-dot" :aria-label="`${event.name} 已出勤`"></span>
                    <span v-else-if="Number(event.enrolled) === 1" class="attendance-dot enrolled" :aria-label="`${event.name} 已报名未签到`"></span>
                  </td>
                </tr>
              </tbody>
            </table>
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
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">浙ICP备2026046394号</a>
    </footer>

    <div class="tabs">
      <div class="tab" :class="{ active: tab === 'activities' }" @click="tab = 'activities'; selectedActivity = null; selectedPlan = null; loadActivities()"><b>⌂</b><span>活动</span></div>
      <div class="tab" :class="{ active: tab === 'rentals' }" @click="tab = 'rentals'; loadRentals()"><b>▣</b><span>发射器租赁</span></div>
      <div class="tab" :class="{ active: tab === 'notifications' }" @click="openNotifications">
        <b>●</b><span>通知</span><span v-if="unreadCount" class="tab-dot"></span>
      </div>
      <div class="tab" :class="{ active: tab === 'mine' }" @click="openMine"><b>◆</b><span>我的</span></div>
    </div>

    <div v-if="showRentalDialog" class="modal">
      <div class="modal-backdrop" @click="showRentalDialog = false"></div>
      <div class="modal-panel">
        <div class="modal-head">
          <h2>{{ rentalForm.id ? '编辑发射器' : '新增发射器' }}</h2>
          <button class="btn secondary" @click="showRentalDialog = false">关闭</button>
        </div>
        <input v-model="rentalForm.name" placeholder="名称" />
        <textarea v-model="rentalForm.description" maxlength="50" placeholder="发射器说明，50字以内" />
        <input v-model="rentalForm.rent_fee" type="number" min="0" step="0.01" placeholder="租金" />
        <input type="file" accept="image/*" @change="uploadRentalPhoto" />
        <img v-if="rentalForm.photo_filename" class="avatar-preview wide" :src="rentalForm.photo_filename" alt="发射器图片预览" />
        <label class="rental-active-field">
          <input v-model="rentalForm.active" type="checkbox" />
          <span>上架展示</span>
        </label>
        <button class="btn" style="width: 100%" @click="saveRental">{{ rentalForm.id ? '保存修改' : '确认新增' }}</button>
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

    <div v-if="checkinDialog.show" class="modal" role="dialog" aria-modal="true" aria-label="活动签到">
      <div class="modal-backdrop" @click="closeCheckinDialog"></div>
      <div class="modal-panel confirm-panel checkin-modal-panel">
        <template v-if="checkinDialog.mode === 'choice'">
          <h2>选择签到方式</h2>
          <p>{{ checkinChoiceHint }}</p>
          <p class="muted">场地地址：{{ displayVenueAddress(detail) || '地址待配置' }}</p>
          <div class="checkin-choice-actions">
            <button v-if="canUseLocationCheckin(detail)" class="btn" :disabled="checkinSubmitting" @click="startLocationCheckin">自主定位签到</button>
            <button v-if="canUseQrCheckin(detail)" class="btn secondary" :disabled="checkinSubmitting" @click="startQrScanner">扫描签到二维码</button>
          </div>
          <button class="btn secondary" :disabled="checkinSubmitting" @click="closeCheckinDialog">取消</button>
        </template>
        <template v-else-if="checkinDialog.mode === 'scanner'">
          <h2>扫描签到二维码</h2>
          <p class="muted">请将活动发起者展示的二维码放入取景框。</p>
          <div id="checkin-qr-reader" class="checkin-qr-reader"></div>
          <button class="btn secondary" @click="closeCheckinDialog">取消扫码</button>
        </template>
        <template v-else>
          <h2>扫码签到</h2>
          <p>二维码已识别，可直接完成签到，无需获取当前位置。</p>
          <p class="muted">场地地址：{{ displayVenueAddress(detail) || '地址待配置' }}</p>
          <div class="confirm-actions">
            <button class="btn secondary" :disabled="checkinSubmitting" @click="closeCheckinDialog">取消</button>
            <button class="btn" :disabled="checkinSubmitting" @click="checkin">{{ checkinSubmitting ? '签到中…' : '确认签到' }}</button>
          </div>
        </template>
      </div>
    </div>

    <div v-if="checkinQrDialog.show" class="modal" role="dialog" aria-modal="true" aria-label="活动签到二维码">
      <div class="modal-backdrop" @click="checkinQrDialog.show = false"></div>
      <div class="modal-panel confirm-panel checkin-modal-panel">
        <h2>活动签到二维码</h2>
        <img v-if="checkinQrDialog.dataUrl" class="checkin-qr-image" :src="checkinQrDialog.dataUrl" alt="活动签到二维码" />
        <p>请参与者扫码登录，识别有效二维码后即可签到。</p>
        <p class="muted">有效至：{{ formatDateTime(checkinQrDialog.expiresAt) }}</p>
        <button class="btn secondary" @click="checkinQrDialog.show = false">关闭</button>
      </div>
    </div>

    <div v-if="enrollmentDialog.show" class="modal" role="dialog" aria-modal="true" aria-label="活动报名人数">
      <div class="modal-backdrop" @click="closeEnrollmentDialog"></div>
      <div class="modal-panel confirm-panel enrollment-modal-panel">
        <h2>{{ detail.my_enrollment ? '修改报名人数' : '活动报名' }}</h2>
        <p>额外同行人数不包含你本人；填 0 表示仅自己 1 人报名。</p>
        <label class="enrollment-count-field">
          <span>额外人数</span>
          <input v-model.number="enrollmentDialog.extra_count" type="number" min="0" step="1" inputmode="numeric" />
        </label>
        <p class="muted">本次合计：{{ 1 + normalizedExtraCount(enrollmentDialog.extra_count) }} 人</p>
        <div class="confirm-actions">
          <button class="btn secondary" :disabled="enrollmentDialog.submitting" @click="closeEnrollmentDialog">取消</button>
          <button class="btn" :disabled="enrollmentDialog.submitting" @click="submitEnrollment">{{ enrollmentDialog.submitting ? '提交中…' : '确认' }}</button>
        </div>
      </div>
    </div>

    <div v-if="shareDialog.show" class="modal" role="dialog" aria-modal="true" aria-label="微信分享活动">
      <div class="modal-backdrop" @click="shareDialog.show = false"></div>
      <div class="modal-panel confirm-panel share-modal-panel">
        <h2>分享活动</h2>
        <div class="share-preview-card">
          <img :src="shareDialog.image" alt="活动分享图" />
          <div>
            <strong>{{ shareDialog.title }}</strong>
            <span>{{ shareDialog.time }}</span>
            <span>{{ shareDialog.location }}</span>
          </div>
        </div>
        <p class="muted">微信内可点击右上角分享给好友或朋友圈；也可以复制链接发送。</p>
        <div class="share-link-box">{{ shareDialog.url }}</div>
        <div class="confirm-actions">
          <button class="btn secondary" @click="shareDialog.show = false">关闭</button>
          <button class="btn secondary" @click="copyShareLink">复制链接</button>
          <button class="btn" @click="nativeShareActivity">系统分享</button>
        </div>
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

    <Transition name="rental-preview">
      <div v-if="rentalPreview" class="rental-lightbox" role="dialog" aria-modal="true" aria-label="发射器图片预览" @click.self="closeRentalPreview">
        <div class="rental-lightbox-panel">
          <button type="button" class="rental-lightbox-close" aria-label="关闭图片预览" @click="closeRentalPreview">×</button>
          <img :src="rentalPreview.photo_filename" :alt="`${rentalPreview.name || '发射器'}详情图`" />
          <div class="rental-lightbox-copy">
            <strong>{{ rentalPreview.name }}</strong>
            <span>{{ rentalPreview.description || '暂无发射器说明' }}</span>
          </div>
        </div>
      </div>
    </Transition>

    </template>

    <div v-if="toast.show" class="toast" role="alert" aria-live="assertive">{{ toast.message }}</div>
  </div>
</template>

<script>
import { api, setErrorHandler, setToken, token } from './api'
import logoUrl from './assets/nbys-logo.png'
import defaultActivityBanner from './assets/activity-default.jpg'
import xpBannerUrl from './escape/assets/xp-banner.jpg'
import QRCode from 'qrcode'
import { Html5Qrcode } from 'html5-qrcode'

export default {
  emits: ['loading-start', 'ready'],
  data() {
    return {
      view: token() ? 'app' : new URLSearchParams(location.search).get('invite') ? 'register' : 'login',
      tab: 'activities',
      me: {},
      systemImages: {},
      loginForm: {},
      loginSubmitting: false,
      logoUrl,
      defaultActivityBanner,
      registerForm: { invite_code: new URLSearchParams(location.search).get('invite') || '' },
      activities: [],
      attendanceSummary: { present_count: 0, activity_total: 0 },
      attendanceMatrix: { year: new Date().getFullYear(), username: '', present_count: 0, events: [] },
      attendanceMatrixLoading: false,
      attendanceMatrixError: '',
      attendanceDrag: { active: false, pointerId: null, startX: 0, scrollLeft: 0 },
      selectedActivity: null,
      selectedPlan: null,
      planVoteForm: { date_option_ids: [], venue_ids: [], game_mode_ids: [] },
      detail: {},
      checkinSubmitting: false,
      checkinDialog: { show: false, qrToken: '', mode: 'choice' },
      checkinQrScanner: null,
      checkinQrDialog: { show: false, dataUrl: '', expiresAt: '' },
      enrollmentDialog: { show: false, extra_count: 0, submitting: false },
      shareDialog: { show: false, title: '', time: '', location: '', image: '', url: '' },
      jobs: ['突击兵', '支援兵', '医疗兵', '狙击手', '弹药兵', '填线兵'],
      joinJobs: {},
      squadEdits: {},
      memberAssignments: {},
      rentalItems: [],
      activityRentalItems: [],
      rentalPreview: null,
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
      confirmDialog: { show: false, title: '确认操作', message: '', resolve: null }
    }
  },
  computed: {
    camps() {
      return [...new Set((this.detail.squads || []).map(squad => squad.camp_no))]
    },
    unassignedMembers() {
      return (this.detail.members || []).filter(member => member.camp_no == null || member.squad_no == null)
    },
    availableJobs() {
      const configured = String(this.detail.allowed_jobs || '').split(',').map(item => item.trim()).filter(Boolean)
      return configured.length ? configured : this.jobs
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
    currentYear() {
      return new Date().getFullYear()
    },
    showHeaderBack() {
      if (this.view !== 'app') return false
      return this.tab !== 'activities' || !!this.selectedActivity || !!this.selectedPlan
    },
    showSiteHome() {
      return this.view === 'app' && this.tab === 'activities' && !this.selectedActivity && !this.selectedPlan
    },
    showEscapeEntryBanner() {
      return this.me.is_regular_member === true || Number(this.me.is_regular_member) === 1
    },
    escapeEntryStyle() {
      return {
        backgroundImage: `url("${xpBannerUrl}")`
      }
    },
    canManageActivities() {
      const role = String(this.me.role || '')
      const guest = role === 'guest' || (role === 'user' && !this.me.is_regular_member)
      return !guest && (this.me.permissions || []).includes('activity:view')
    },
    displayLogoUrl() {
      return this.systemImages.login_logo_url || this.logoUrl
    },
    authPageStyle() {
      return { backgroundColor: '#070b0c' }
    },
    checkinChoiceHint() {
      const methods = this.checkinMethods(this.detail)
      if (methods.length === 1 && methods[0] === 'location') return '本活动仅开放自主定位签到，需验证活动场地 1 公里范围。'
      if (methods.length === 1 && methods[0] === 'qr') return '本活动仅开放扫描现场二维码签到，无需定位。'
      return '自主签到需验证活动场地 1 公里范围；扫描现场二维码无需定位。'
    }
  },
  async mounted() {
    setErrorHandler(this.showToast)
    window.addEventListener('nbys-auth-expired', this.expireSession)
    try {
      await Promise.all([this.loadSystemImages(), this.init()])
      await this.openRequestedActivity()
    } catch {
      setToken('')
      this.view = new URLSearchParams(location.search).get('invite') ? 'register' : 'login'
      await this.loadSystemImages()
      await this.preloadImages([this.displayLogoUrl, this.systemImages.login_background_url])
    } finally {
      this.$emit('ready')
    }
  },
  beforeUnmount() {
    window.removeEventListener('nbys-auth-expired', this.expireSession)
    this.stopQrScanner()
  },
  methods: {
    expireSession() {
      if (this.selectedActivity) this.replaceActivityUrl(this.selectedActivity)
      if (this.selectedPlan) this.replacePlanUrl(this.selectedPlan.id)
      setToken('')
      this.view = 'login'
      this.tab = 'activities'
      this.selectedActivity = null
      this.selectedPlan = null
      this.me = {}
    },
    async loadSystemImages() {
      try {
        this.systemImages = await api('/api/public/system-settings/images', { silent: true })
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
      if (this.loginSubmitting) return
      this.loginSubmitting = true
      let activityLoadingStarted = false
      try {
        const data = await api('/api/h5/auth/login', { method: 'POST', body: this.loginForm, timeout: 30000 })
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
        if (!this.redirectAfterLogin()) await this.openRequestedActivity()
      } catch (error) {
        this.showToast(error.message || '登录失败')
      } finally {
        this.loginSubmitting = false
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
        .filter(activity => ['报名中', '活动进行中', '活动开始', '进行中'].includes(activity.display_status))
        .map(activity => ({ ...activity, record_kind: 'activity' }))
      const planning = (dashboard.plans || [])
        .map(plan => ({ ...plan, record_kind: 'plan', display_status: '策划中' }))
      this.activities = [...activeActivities, ...planning].sort((a, b) => this.sortTime(b.created_at) - this.sortTime(a.created_at))
      this.attendanceSummary = dashboard.attendance_summary || { present_count: 0, activity_total: 0 }
      this.notifications = dashboard.notifications || []
    },
    remoteFirstAvatar(url) {
      const value = String(url || '')
      if (!value.startsWith('/uploads/')) return value
      return `http://8.160.183.48:575${value}`
    },
    fallbackAvatar(event) {
      const original = event?.target?.dataset?.original || event?.target?.getAttribute('src')?.replace('http://8.160.183.48:575', '')
      if (!original || event.target.src === original) return
      event.target.dataset.original = original
      event.target.src = original
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
          .filter(activity => ['报名中', '活动进行中', '活动开始', '进行中'].includes(activity.display_status))
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
        this.clearDetailUrl()
        this.loadActivities()
      }
    },
    goSiteHome() {
      window.location.hash = '#/'
    },
    openEscape() {
      window.location.hash = '#/escape'
    },
    goAdminActivity() {
      if (!this.selectedActivity || !this.canManageActivities) return
      const base = location.port === '5174' ? `${location.protocol}//${location.hostname}:5173/admin/` : '/admin/'
      const params = new URLSearchParams({ view: 'activities', from: 'h5', activityId: String(this.selectedActivity) })
      window.location.href = `${base}?${params.toString()}`
    },
    async openRequestedActivity() {
      const params = new URLSearchParams(location.search)
      const id = Number(params.get('activityId'))
      if (this.view === 'app' && Number.isInteger(id) && id > 0 && Number(this.selectedActivity) !== id) {
        await this.openActivity(id)
        return
      }
      const planId = Number(params.get('planId'))
      if (this.view === 'app' && Number.isInteger(planId) && planId > 0 && Number(this.selectedPlan?.id) !== planId) {
        await this.openPlanById(planId)
      }
      const qrToken = params.get('checkinToken') || ''
      if (this.view === 'app' && this.selectedActivity && qrToken && !this.detail.checkin?.present) this.openCheckinDialog(qrToken)
    },
    redirectAfterLogin() {
      const target = new URLSearchParams(location.search).get('returnTo')
      if (!target) return false
      if (target.startsWith('/')) {
        window.location.href = target
        return true
      }
      try {
        const destination = new URL(target)
        const localAdmin = location.port === '5174' && destination.hostname === location.hostname && destination.port === '5173'
        if (destination.origin === location.origin || localAdmin) {
          window.location.href = destination.href
          return true
        }
      } catch {}
      return false
    },
    statusLabel(status) {
      return status === '活动开始' || status === '活动进行中' ? '进行中' : status
    },
    statusClass(status) {
      if (status === '报名中') return 'signup'
      if (status === '活动开始' || status === '活动进行中' || status === '进行中') return 'running'
      if (status === '策划中') return 'planning'
      return ''
    },
    activityProgress(activity) {
      const current = Number(activity.enroll_count) || 0
      const total = Number(activity.signup_limit) || 0
      if (!total) return '0%'
      return `${Math.min(100, Math.max(0, current / total * 100))}%`
    },
    openHomeCard(item) {
      if (item.record_kind === 'activity') return this.openActivity(item.id)
      return this.openPlan(item)
    },
    openPlan(item) {
      this.selectedActivity = null
      this.detail = {}
      this.selectedPlan = item
      this.planVoteForm = this.planVoteFormFromPlan(item)
      this.replacePlanUrl(item.id)
      this.updatePlanShareMeta()
    },
    async openPlanById(id) {
      let plan = this.activities.find(item => item.record_kind === 'plan' && Number(item.id) === Number(id))
      if (!plan) {
        await this.loadActivities()
        plan = this.activities.find(item => item.record_kind === 'plan' && Number(item.id) === Number(id))
      }
      if (!plan) throw new Error('策划不存在或不可见')
      this.openPlan(plan)
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
    isWeeklyActivity(activity) {
      return activity?.activity_type === '周常' || activity?.activity_type === '接龙'
    },
    hasActivityVoteSummary(activity) {
      return activity?.converted_from_plan === true || activity?.converted_from_plan === 1 || activity?.converted_from_plan === '1'
    },
    showActivityStats(activity) {
      return !!activity?.is_activity_creator && !this.isWeeklyActivity(activity) && !this.hasActivityVoteSummary(activity)
    },
    normalizedExtraCount(value) {
      const count = Number.parseInt(value, 10)
      return Number.isFinite(count) && count > 0 ? count : 0
    },
    participantCount(member) {
      const count = Number(member?.participant_count)
      return Number.isFinite(count) && count > 0 ? count : 1 + this.normalizedExtraCount(member?.extra_count)
    },
    shortName(value) {
      return String(value || '甬').trim().slice(0, 2) || '甬'
    },
    checkinMethods(activity) {
      const raw = activity?.checkin_methods
      const values = Array.isArray(raw) ? raw : String(raw || '').split(',')
      const methods = values.map(item => String(item).trim()).filter(item => ['location', 'qr'].includes(item))
      return methods.length ? [...new Set(methods)] : ['location', 'qr']
    },
    canUseLocationCheckin(activity) {
      return this.checkinMethods(activity).includes('location')
    },
    canUseQrCheckin(activity) {
      return this.checkinMethods(activity).includes('qr')
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
    canCheckinActivity(activity) {
      if (typeof activity?.checkin_open === 'boolean') return activity.checkin_open
      if (!activity?.start_at || !activity?.end_at) return false
      const start = new Date(String(activity.start_at).replace(' ', 'T'))
      const end = new Date(String(activity.end_at).replace(' ', 'T'))
      if (Number.isNaN(start.getTime())) return false
      const configuredValue = Number(activity.checkin_open_value ?? 3)
      const value = Number.isFinite(configuredValue) ? Math.max(0, Math.floor(configuredValue)) : 3
      const unit = activity.checkin_open_unit === 'day' ? 'day' : 'hour'
      const offset = value * (unit === 'day' ? 24 : 1) * 60 * 60 * 1000
      const now = new Date()
      return now.getTime() >= start.getTime() - offset && now.getTime() <= end.getTime()
    },
    formatAttendanceDate(value) {
      if (!value) return '时间待定'
      return String(value).replace('T', ' ').slice(0, 10)
    },
    startAttendanceDrag(event) {
      if (event.pointerType !== 'mouse' || event.button !== 0) return
      const scroller = event.currentTarget
      this.attendanceDrag = {
        active: true,
        pointerId: event.pointerId,
        startX: event.clientX,
        scrollLeft: scroller.scrollLeft
      }
      scroller.setPointerCapture?.(event.pointerId)
    },
    moveAttendanceDrag(event) {
      if (!this.attendanceDrag.active || this.attendanceDrag.pointerId !== event.pointerId) return
      event.preventDefault()
      event.currentTarget.scrollLeft = this.attendanceDrag.scrollLeft + this.attendanceDrag.startX - event.clientX
    },
    stopAttendanceDrag(event) {
      if (!this.attendanceDrag.active || this.attendanceDrag.pointerId !== event.pointerId) return
      event.currentTarget.releasePointerCapture?.(event.pointerId)
      this.attendanceDrag.active = false
      this.attendanceDrag.pointerId = null
    },
    async openActivity(id) {
      this.selectedPlan = null
      this.selectedActivity = id
      this.replaceActivityUrl(id)
      this.detail = await api(`/api/h5/activities/${id}`)
      this.prepareJoinJobs()
      this.prepareSquadEdits()
      this.prepareMemberAssignments()
      this.updateActivityShareMeta()
    },
    activityShareUrl(id = this.selectedActivity) {
      const url = new URL(location.href)
      url.search = ''
      url.searchParams.set('activityId', String(id))
      url.hash = '#/app'
      return url.href
    },
    replaceActivityUrl(id) {
      if (!id || this.view !== 'app') return
      const next = this.activityShareUrl(id)
      if (next !== location.href) history.replaceState(null, '', next)
    },
    planShareUrl(id = this.selectedPlan?.id) {
      const url = new URL(location.href)
      url.search = ''
      url.searchParams.set('planId', String(id))
      url.hash = '#/app'
      return url.href
    },
    replacePlanUrl(id) {
      if (!id || this.view !== 'app') return
      const next = this.planShareUrl(id)
      if (next !== location.href) history.replaceState(null, '', next)
    },
    clearDetailUrl() {
      const url = new URL(location.href)
      if (!url.searchParams.has('activityId') && !url.searchParams.has('planId') && !url.searchParams.has('checkinToken')) return
      url.searchParams.delete('activityId')
      url.searchParams.delete('planId')
      url.searchParams.delete('checkinToken')
      url.hash = '#/app'
      history.replaceState(null, '', url.href)
    },
    absoluteUrl(value) {
      return new URL(value || this.defaultActivityBanner, location.origin).href
    },
    shareTitle() {
      if (this.selectedPlan) return this.selectedPlan.name || '宁波甬士活动策划'
      return this.detail.name || '宁波甬士活动'
    },
    shareDescription() {
      if (this.selectedPlan) return `发起人：${this.selectedPlan.creator_name || '未设置'}`
      const time = this.formatTimeRange(this.detail.start_at, this.detail.end_at)
      const venue = this.displayVenueAddress(this.detail) || this.displayVenueName(this.detail)
      return [time, venue].filter(Boolean).join(' · ')
    },
    updateActivityShareMeta() {
      const title = this.shareTitle()
      const description = this.shareDescription()
      const image = this.absoluteUrl(this.detail.banner_url || this.defaultActivityBanner)
      document.title = title
      this.setMeta('description', description)
      this.setMeta('og:title', title, 'property')
      this.setMeta('og:description', description, 'property')
      this.setMeta('og:image', image, 'property')
      this.setMeta('og:url', this.activityShareUrl(), 'property')
    },
    updatePlanShareMeta() {
      const title = this.shareTitle()
      const description = this.shareDescription()
      const image = this.absoluteUrl(this.selectedPlan?.banner_url || this.defaultActivityBanner)
      document.title = title
      this.setMeta('description', description)
      this.setMeta('og:title', title, 'property')
      this.setMeta('og:description', description, 'property')
      this.setMeta('og:image', image, 'property')
      this.setMeta('og:url', this.planShareUrl(), 'property')
    },
    setMeta(name, content, attr = 'name') {
      if (!content) return
      let el = document.head.querySelector(`meta[${attr}="${name}"]`)
      if (!el) {
        el = document.createElement('meta')
        el.setAttribute(attr, name)
        document.head.appendChild(el)
      }
      el.setAttribute('content', content)
    },
    openShareDialog() {
      const isPlan = !!this.selectedPlan
      const url = isPlan ? this.planShareUrl() : this.activityShareUrl()
      this.shareDialog = {
        show: true,
        title: this.shareTitle(),
        time: isPlan ? `发起人：${this.selectedPlan.creator_name || '未设置'}` : (this.formatTimeRange(this.detail.start_at, this.detail.end_at) || '时间待定'),
        location: isPlan ? `投票截止：${this.formatDateTime(this.selectedPlan.vote_deadline) || '待定'}` : (this.displayVenueAddress(this.detail) || this.displayVenueName(this.detail) || '地点待定'),
        image: this.absoluteUrl((isPlan ? this.selectedPlan.banner_url : this.detail.banner_url) || this.defaultActivityBanner),
        url
      }
      isPlan ? this.updatePlanShareMeta() : this.updateActivityShareMeta()
    },
    async copyShareLink() {
      const url = this.shareDialog.url || this.activityShareUrl()
      try {
        await navigator.clipboard.writeText(url)
      } catch {
        const input = document.createElement('textarea')
        input.value = url
        input.setAttribute('readonly', 'readonly')
        input.style.position = 'fixed'
        input.style.left = '-9999px'
        document.body.appendChild(input)
        input.select()
        document.execCommand('copy')
        document.body.removeChild(input)
      }
      this.showToast('活动链接已复制')
    },
    async nativeShareActivity() {
      const data = {
        title: this.shareDialog.title || this.shareTitle(),
        text: `${this.shareDialog.time || ''} ${this.shareDialog.location || ''}`.trim(),
        url: this.shareDialog.url || this.activityShareUrl()
      }
      if (!navigator.share) {
        await this.copyShareLink()
        return
      }
      try {
        await navigator.share(data)
      } catch {}
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
    startEnroll() {
      if (this.isWeeklyActivity(this.detail)) return this.openEnrollmentDialog()
      return this.enroll()
    },
    openEnrollmentDialog() {
      const currentExtra = this.detail.my_enrollment ? this.normalizedExtraCount(this.detail.my_enrollment.extra_count) : 0
      this.enrollmentDialog = { show: true, extra_count: currentExtra, submitting: false }
    },
    closeEnrollmentDialog() {
      if (this.enrollmentDialog.submitting) return
      this.enrollmentDialog = { show: false, extra_count: 0, submitting: false }
    },
    async submitEnrollment() {
      if (this.enrollmentDialog.submitting) return
      const extraCount = this.normalizedExtraCount(this.enrollmentDialog.extra_count)
      this.enrollmentDialog.submitting = true
      try {
        await this.enroll({ extra_count: extraCount })
        this.enrollmentDialog = { show: false, extra_count: 0, submitting: false }
        this.showToast('报名人数已更新')
      } catch (error) {
        this.enrollmentDialog.submitting = false
        throw error
      }
    },
    async enroll(body = {}) {
      await api(`/api/h5/activities/${this.selectedActivity}/enroll`, { method: 'POST', body })
      await this.openActivity(this.selectedActivity)
      await this.loadActivities()
    },
    cancelEnroll() {
      return api(`/api/h5/activities/${this.selectedActivity}/enroll`, { method: 'DELETE' }).then(async () => {
        await this.openActivity(this.selectedActivity)
        await this.loadActivities()
      })
    },
    openCheckinDialog(qrToken = '') {
      if (qrToken) {
        if (!this.canUseQrCheckin(this.detail)) return this.showToast('该活动未开放二维码签到')
        this.checkinDialog = { show: true, qrToken, mode: 'confirm' }
        return
      }
      const methods = this.checkinMethods(this.detail)
      if (methods.length === 1 && methods[0] === 'location') {
        this.checkinDialog = { show: true, qrToken: '', mode: 'choice' }
        return this.startLocationCheckin()
      }
      if (methods.length === 1 && methods[0] === 'qr') {
        this.checkinDialog = { show: true, qrToken: '', mode: 'choice' }
        return this.startQrScanner()
      }
      this.checkinDialog = { show: true, qrToken: '', mode: 'choice' }
    },
    async closeCheckinDialog() {
      if (this.checkinSubmitting) return
      await this.stopQrScanner()
      this.checkinDialog = { show: false, qrToken: '', mode: 'choice' }
    },
    startLocationCheckin() {
      if (!this.canUseLocationCheckin(this.detail)) return this.showToast('该活动未开放定位签到')
      this.checkinDialog.qrToken = ''
      return this.checkin()
    },
    async startQrScanner() {
      if (!this.canUseQrCheckin(this.detail)) return this.showToast('该活动未开放二维码签到')
      this.checkinDialog.mode = 'scanner'
      await this.$nextTick()
      try {
        await this.stopQrScanner()
        this.checkinQrScanner = new Html5Qrcode('checkin-qr-reader')
        await this.checkinQrScanner.start(
          { facingMode: 'environment' },
          { fps: 10, qrbox: { width: 240, height: 240 }, aspectRatio: 1 },
          decodedText => this.handleScannedCheckin(decodedText),
          () => {}
        )
      } catch (error) {
        await this.stopQrScanner()
        this.checkinDialog.mode = 'choice'
        this.showToast('无法打开摄像头，请允许相机权限后重试')
      }
    },
    async stopQrScanner() {
      const scanner = this.checkinQrScanner
      this.checkinQrScanner = null
      if (!scanner) return
      try {
        if (scanner.isScanning) await scanner.stop()
        scanner.clear()
      } catch {}
    },
    async handleScannedCheckin(decodedText) {
      if (this.checkinDialog.mode !== 'scanner') return
      try {
        const url = new URL(decodedText, location.origin)
        const activityId = Number(url.searchParams.get('activityId'))
        const qrToken = url.searchParams.get('checkinToken') || ''
        if (activityId !== Number(this.selectedActivity) || !qrToken) throw new Error('请扫描当前活动的签到二维码')
        this.checkinDialog.mode = 'confirm'
        await this.stopQrScanner()
        this.checkinDialog = { show: true, qrToken, mode: 'confirm' }
        this.showToast('二维码识别成功')
      } catch (error) {
        this.showToast(error.message || '签到二维码无效')
      }
    },
    currentPosition() {
      return new Promise((resolve, reject) => {
        if (!navigator.geolocation) return reject(new Error('当前浏览器不支持定位，请使用手机浏览器打开'))
        navigator.geolocation.getCurrentPosition(resolve, error => {
          const messages = { 1: '定位权限被拒绝，请在浏览器设置中允许定位', 2: '暂时无法获取位置，请到开阔处重试', 3: '获取位置超时，请重试' }
          reject(new Error(messages[error.code] || '获取位置失败，请重试'))
        }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 })
      })
    },
    async checkin() {
      if (this.checkinSubmitting) return
      this.checkinSubmitting = true
      try {
        const qrToken = this.checkinDialog.qrToken
        const position = qrToken ? null : await this.currentPosition()
        await api(`/api/h5/activities/${this.selectedActivity}/checkin`, {
          method: 'POST',
          body: {
            latitude: position?.coords.latitude,
            longitude: position?.coords.longitude,
            accuracy: position?.coords.accuracy,
            source: qrToken ? 'qr' : 'location',
            qr_token: qrToken || undefined
          }
        })
        this.checkinDialog = { show: false, qrToken: '', mode: 'choice' }
        this.showToast('签到成功')
        await this.openActivity(this.selectedActivity)
      } catch (error) {
        this.showToast(error.message || '签到失败')
      } finally {
        this.checkinSubmitting = false
      }
    },
    async showCheckinQr() {
      try {
        const result = await api(`/api/h5/activities/${this.selectedActivity}/checkin-qr`)
        const params = new URLSearchParams({ activityId: String(this.selectedActivity), checkinToken: result.token })
        const url = `${location.origin}${location.pathname}?${params.toString()}#/app`
        const dataUrl = await QRCode.toDataURL(url, { width: 320, margin: 2, errorCorrectionLevel: 'M' })
        this.checkinQrDialog = { show: true, dataUrl, expiresAt: result.expires_at }
      } catch (error) {
        this.showToast(error.message || '二维码生成失败')
      }
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
    canManageSquad(squad) {
      return !!this.detail.is_activity_creator || this.isSquadLeader(squad)
    },
    isSelf(member) {
      return Number(member?.user_id) === Number(this.me.id)
    },
    isMemberLeader(squad, member) {
      return Number(squad?.leader_user_id) === Number(member?.user_id)
    },
    canEditMemberJob(squad, member) {
      return this.isSquadLeader(squad) || this.isSelf(member)
    },
    selfMemberLocked(squad, member) {
      return this.isSelf(member) && !this.isSquadLeader(squad) && this.isSquadLocked(squad)
    },
    squadLeaderName(squad) {
      const leader = (this.detail.members || []).find(member => Number(member.user_id) === Number(squad?.leader_user_id))
      return leader ? (leader.callsign || leader.username) : '未设置'
    },
    isMySquad(squad) {
      const mine = this.detail.my_enrollment || {}
      return Number(mine.camp_no) === Number(squad?.camp_no) && Number(mine.squad_no) === Number(squad?.squad_no)
    },
    defaultRadioChannel(squad) {
      const campNo = Number(squad?.camp_no || 1)
      const squadNo = Number(squad?.squad_no || 1)
      return `${438 + campNo}.${String(squadNo * 100).padStart(3, '0')}`
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
    prepareSquadEdits() {
      this.squadEdits = Object.fromEntries((this.detail.squads || []).map(squad => [squad.id, {
        radio_channel: squad.radio_channel || this.defaultRadioChannel(squad),
        leaderUserId: squad.leader_user_id == null ? '' : String(squad.leader_user_id)
      }]))
    },
    async assignMember(member) {
      return this.saveMemberAssignment(member)
    },
    async saveMemberAssignment(member) {
      const assignment = this.memberAssignments[member.user_id] || {}
      if (!assignment.squadKey) return this.showToast('请先选择小队')
      if (!assignment.job) return this.showToast('请先选择职业')
      const [campNo, squadNo] = assignment.squadKey.split(':').map(value => Number(value))
      const changedSquad = Number(member.camp_no) !== campNo || Number(member.squad_no) !== squadNo
      if (changedSquad && !(await this.askConfirm(`确认将 ${member.callsign || member.username} 调整到阵营${campNo}的目标小队吗？`))) return
      await api(`/api/h5/activities/${this.selectedActivity}/members/${member.user_id}/assignment`, {
        method: 'PUT',
        body: { camp_no: campNo, squad_no: squadNo, job: assignment.job }
      })
      await this.openActivity(this.selectedActivity)
      this.showToast('成员信息已保存')
    },
    async saveMemberJob(member) {
      const job = this.memberAssignments[member.user_id]?.job
      if (!job) return this.showToast('请选择职业')
      await api(`/api/h5/activities/${this.selectedActivity}/members/${member.user_id}/job`, { method: 'PUT', body: { job } })
      await this.openActivity(this.selectedActivity)
      this.showToast('职业已保存')
    },
    async removeMember(squad, member, self = false) {
      if (this.isMemberLeader(squad, member)) return this.showToast('请先设置新的队长')
      const action = self ? '退出小队' : `将 ${member.callsign || member.username} 踢出小队`
      if (!(await this.askConfirm(`确认${action}吗？职业和所属阵营将一并清空。`))) return
      await api(`/api/h5/activities/${this.selectedActivity}/members/${member.user_id}/squad`, { method: 'DELETE' })
      await this.openActivity(this.selectedActivity)
      this.showToast(self ? '已退出小队' : '成员已移至未分配')
    },
    cannotSelfJoin(squad) {
      const mine = this.detail.my_enrollment || {}
      const source = (this.detail.squads || []).find(item => Number(item.camp_no) === Number(mine.camp_no) && Number(item.squad_no) === Number(mine.squad_no))
      return this.isSquadLocked(squad) || !!(source && this.isSquadLocked(source)) || !!(source && this.isSquadLeader(source))
    },
    selfJoinLabel(squad) {
      if (this.isSquadLocked(squad)) return '目标小队已锁定'
      const mine = this.detail.my_enrollment || {}
      const source = (this.detail.squads || []).find(item => Number(item.camp_no) === Number(mine.camp_no) && Number(item.squad_no) === Number(mine.squad_no))
      if (source && this.isSquadLeader(source)) return '请先转让队长'
      if (source && this.isSquadLocked(source)) return '原小队已锁定'
      return '加入小队'
    },
    async joinSquad(squad, job) {
      if (!job || job === '请选择职业') {
        this.showToast('请选择职业')
        return
      }
      if (this.cannotSelfJoin(squad)) return this.showToast(this.selfJoinLabel(squad))
      if (!(await this.askConfirm(`确认加入阵营${squad.camp_no} / ${squad.name}吗？原小队信息将被替换。`))) return
      await api(`/api/h5/activities/${this.selectedActivity}/squad`, { method: 'PUT', body: { camp_no: squad.camp_no, squad_no: squad.squad_no, job } })
      await this.openActivity(this.selectedActivity)
      this.showToast('已加入小队')
    },
    async saveSquadSettings(squad, locked = this.isSquadLocked(squad)) {
      const edit = this.squadEdits[squad.id] || {}
      await api(`/api/h5/activities/${this.selectedActivity}/squads/${squad.id}/settings`, { method: 'PUT', body: { radio_channel: edit.radio_channel, locked } })
      await this.openActivity(this.selectedActivity)
      this.showToast('小队设置已保存')
    },
    async toggleSquadLock(squad) {
      if (!this.canManageSquad(squad)) return this.showToast('没有权限修改小队设置')
      const locked = !this.isSquadLocked(squad)
      if (!(await this.askConfirm(locked ? '锁定后普通成员将不能改职业、退出或换队，确认锁定吗？' : '确认解除小队锁定吗？'))) return
      await this.saveSquadSettings(squad, locked)
      this.showToast(locked ? '小队已锁定' : '小队已解除锁定')
    },
    async saveSquadLeader(squad) {
      const userId = this.squadEdits[squad.id]?.leaderUserId
      if (!userId) return this.showToast('请选择队长')
      if (Number(userId) === Number(squad.leader_user_id)) return this.showToast('当前成员已经是队长')
      const member = (this.detail.members || []).find(item => Number(item.user_id) === Number(userId))
      if (!(await this.askConfirm(`确认将 ${member?.callsign || member?.username || '该成员'} 设置为 ${squad.name} 队长吗？`))) return
      await api(`/api/h5/activities/${this.selectedActivity}/squads/${squad.id}/leader`, { method: 'PUT', body: { user_id: userId } })
      await this.openActivity(this.selectedActivity)
      this.showToast('队长已更新')
    },
    openActivityRentals() {
      this.tab = 'activityRentals'
      return api(`/api/h5/activities/${this.selectedActivity}/launcher-rentals`).then(data => { this.activityRentalItems = data })
    },
    openRentalPreview(item) {
      if (item?.photo_filename) this.rentalPreview = item
    },
    closeRentalPreview() {
      this.rentalPreview = null
    },
    rentalButtonDisabled(item) {
      return Number(item.created_by_id) === Number(this.me.id) || !!item.rental_status
    },
    isMyLauncherRental(item) {
      return !!item.rental_status && Number(item.renter_id) === Number(this.me.id)
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
    async cancelLauncherRental(item) {
      if (!this.isMyLauncherRental(item) || !item.rental_id) return
      if (!(await this.askConfirm('确认取消租赁该发射器？'))) return
      await api(`/api/h5/launcher-rentals/${item.rental_id}/cancel`, { method: 'PUT' })
      await this.openActivityRentals()
      this.showToast('已取消租赁')
    },
    loadRentals() {
      return api('/api/h5/launcher-rentals/my-items').then(data => { this.rentalItems = data })
    },
    openRentalDialog(item = null) {
      this.rentalForm = item
        ? { id: item.id, name: item.name, description: item.description || '', photo_filename: item.photo_filename || '', rent_fee: item.rent_fee, active: !!item.active }
        : { name: '', description: '', photo_filename: '', rent_fee: '', active: true }
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
    saveRental() {
      if (!String(this.rentalForm.name || '').trim()) return this.showToast('请填写发射器名称')
      const editing = !!this.rentalForm.id
      const url = editing ? `/api/h5/launcher-rentals/my-items/${this.rentalForm.id}` : '/api/h5/launcher-rentals/my-items'
      return api(url, { method: editing ? 'PUT' : 'POST', body: this.rentalForm }).then(() => {
        this.rentalForm = {}
        this.showRentalDialog = false
        this.loadRentals()
        this.showToast(editing ? '发射器已修改' : '发射器已新增')
      })
    },
    async deleteRental(id) {
      if (!(await this.askConfirm('确认删除该发射器出租信息？'))) return
      return api(`/api/h5/launcher-rentals/my-items/${id}`, { method: 'DELETE' }).then(() => this.loadRentals())
    },
    loadNotifications() {
      return api('/api/h5/notifications').then(data => { this.notifications = data })
    },
    async loadAttendanceMatrix() {
      this.attendanceMatrixLoading = true
      this.attendanceMatrixError = ''
      try {
        const data = await api(`/api/h5/attendance/my-matrix?year=${this.currentYear}`)
        this.attendanceMatrix = data || { year: this.currentYear, username: this.me.username || '', present_count: 0, events: [] }
      } catch (error) {
        this.attendanceMatrixError = error.message || '出勤记录加载失败'
      } finally {
        this.attendanceMatrixLoading = false
      }
    },
    openMine() {
      this.tab = 'mine'
      return this.loadAttendanceMatrix()
    },
    async openNotifications() {
      this.tab = 'notifications'
      await this.loadNotifications()
      if (this.unreadCount) {
        await api('/api/h5/notifications/read-all', { method: 'PUT' })
        await this.loadNotifications()
      }
    },
    confirmRentalNotice(notice) {
      return api(`/api/h5/launcher-rentals/${notice.rental_action_id || notice.related_id}/confirm`, { method: 'PUT' }).then(async () => {
        this.showToast('已确认租借')
        await this.loadNotifications()
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
    async logout() {
      try { await api('/api/auth/logout', { method: 'POST' }) } catch {}
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
