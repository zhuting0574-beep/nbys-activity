<template>
  <section class="escape-app">
    <header class="escape-header escape-main-header">
      <button type="button" class="escape-route-back" aria-label="返回活动首页" @click="$emit('back')">‹</button>
      <div><span>ESCAPE FROM XP</span><h1>逃离西撇镇</h1></div>
      <div class="escape-cash"><small>甬士币</small><b>{{ money(summary.cash) }}</b></div>
    </header>
    <nav class="escape-top-tabs" aria-label="逃离西撇镇功能导航">
      <button v-for="item in navItems" :key="item.value" type="button" :class="{ active: activeView === item.value }" @click="switchView(item.value)">{{ item.label }}</button>
    </nav>

    <main class="escape-content escape-rework-content">
      <EscapeState
        v-if="activeView !== 'matches' && currentState.loading"
        type="loading"
        :title="`正在加载${navLabel}`"
        description="正在同步最新对局与资产信息"
      />
      <EscapeState
        v-else-if="activeView !== 'matches' && currentState.error"
        type="error"
        :title="`${navLabel}加载失败`"
        :description="currentState.error"
        action-label="重新加载"
        @action="loadActive"
      />

      <template v-else-if="activeView === 'profile'">
        <section class="escape-profile-hero">
          <img v-if="user.avatar_url" class="escape-profile-avatar" :src="user.avatar_url" :alt="`${summary.callsign || user.callsign || user.username || '用户'}头像`" />
          <div v-else class="escape-profile-avatar fallback">{{ shortName(summary.callsign || user.callsign || user.username) }}</div>
          <div><span>OPERATOR PROFILE</span><h2>{{ summary.callsign || user.callsign || user.username || '未设置呼号' }}</h2><p>{{ user.username || 'NBYS 正式队员' }}</p></div>
          <em v-if="summary.season">{{ summary.season.name }}</em>
        </section>

        <section class="escape-profile-metrics" aria-label="个人数据">
          <div><span>总对局</span><strong>{{ summary.stats?.matches_played || 0 }}</strong></div>
          <div><span>成功撤离</span><strong>{{ summary.stats?.successful_escapes || 0 }}</strong></div>
          <div><span>撤离率</span><strong>{{ percent(summary.extraction_rate) }}</strong></div>
          <div><span>场均收益</span><strong>{{ money(summary.average_income) }}</strong></div>
        </section>

        <section v-if="summary.season" class="escape-season-summary">
          <header>
            <div><span>SEASON REPORT</span><h2>本赛季统计</h2></div>
            <small>{{ seasonRemaining(summary.season) }}</small>
          </header>
          <div class="escape-season-progress"><i :style="{ width: seasonProgress(summary.season) }"></i></div>
          <div class="escape-season-row"><strong>{{ summary.season.name }}</strong><small>{{ seasonDateRange(summary.season) }}</small></div>
          <div class="escape-rarity-summary">
            <article v-for="rarity in rarityCards" :key="rarity.value" :class="rarity.value">
              <strong>{{ summary.rarity_summary?.[rarity.value] || 0 }}</strong>
              <span>{{ rarity.label }}</span>
            </article>
          </div>
        </section>

        <section class="escape-active-section">
          <div class="escape-section-head"><div><span>ACTIVE MATCH</span><h2>当前对局</h2></div><button type="button" aria-label="刷新对局" title="刷新对局" @click="loadProfile">↻</button></div>
          <EscapeState v-if="!matches.length" type="empty" title="暂无可加入对局" description="新对局创建后会出现在这里" />
          <template v-else>
          <article v-for="match in matches" :key="match.id" class="escape-match-card">
            <div class="escape-match-cover" :style="coverStyle(match)">
              <span class="escape-status" :class="match.status">{{ statusText(match.status) }}</span>
              <span>XP-{{ match.id }}</span>
            </div>
            <div class="escape-match-body">
              <h3>{{ match.name }}</h3>
              <p>{{ match.venue_name || '场地待定' }}</p>
              <div class="escape-match-meta">
                <span><b>{{ match.member_count || 0 }}</b>/{{ match.capacity || '-' }} 人</span>
                <span><b>{{ match.squad_count || 0 }}</b> 个小队</span>
              </div>
              <button v-if="match.status === 'preparing' && !match.loadout_locked" class="escape-primary wide" type="button" @click="openLoadout(match)">进入对局并配置装备</button>
              <div v-else-if="match.loadout_locked" class="escape-locked">
                <div class="escape-locked-team-head">
                  <strong>已锁定 · 第 {{ match.team_no || '-' }} 小队 · {{ match.team_member_count || 0 }} 人</strong>
                  <button type="button" aria-label="刷新小队成员" title="刷新小队成员" @click="loadProfile">↻</button>
                </div>
                <div v-if="match.team_members?.length" class="escape-team-members">
                  <div v-for="member in match.team_members" :key="member.user_id" class="escape-team-member">
                    <img v-if="member.avatar_url" :src="member.avatar_url" :alt="memberDisplayName(member)" />
                    <span v-else class="escape-team-member-avatar">{{ shortName(memberDisplayName(member)) }}</span>
                    <span>{{ memberDisplayName(member) }}</span>
                  </div>
                </div>
                <p v-else>{{ match.team_member_names || '暂无小队成员' }}</p>
              </div>
              <div v-else class="escape-locked neutral">{{ statusText(match.status) }}</div>
            </div>
          </article>
          </template>
        </section>

        <EscapeWarehouseBoard
          :warehouses="warehouses"
          :moving="warehouseMoving"
          @move="moveInventory"
          @sell="sellItem"
          @sell-all="sellAllItems"
          @invalid="$emit('notify', $event)"
          @history="openWarehouseHistory"
        />

      </template>

      <template v-else-if="activeView === 'shop'">
        <div class="escape-shop-hero"><span>BLACK MARKET</span><h2>补给商店</h2><p>每日 05:00 刷新价格与库存</p></div>
        <div class="escape-filter-row">
          <button v-for="filterItem in shopFilters" :key="filterItem.value" type="button" :class="{ active: shopCategory === filterItem.value }" @click="switchShop(filterItem.value)">{{ filterItem.label }}</button>
        </div>
        <EscapeState v-if="!products.length" type="empty" title="暂无在售商品" description="商品上架后会出现在这里" />
        <div v-else class="escape-products">
          <article v-for="product in products" :key="product.id">
            <div :class="`rarity-${product.rarity || 'normal'}`"><img v-if="product.image_url" :src="product.image_url" :alt="product.name" /><span v-else>{{ itemSymbol(product) }}</span></div>
            <div><span class="escape-rarity">{{ product.category_label || rarityText(product.rarity) }}</span><h3>{{ product.name }}</h3><p>库存 {{ product.stock == null ? '不限' : product.stock }} · {{ product.product_type === 'expansion' ? '购买后立即生效' : '进入缓冲区' }}</p></div>
            <button type="button" :disabled="product.stock === 0 || purchasingId === product.id" @click="purchase(product)">{{ purchasingId === product.id ? '购买中…' : `¥${money(product.price)}` }}</button>
          </article>
        </div>
      </template>

      <template v-else-if="activeView === 'records'">
        <div class="escape-shop-hero"><span>COMBAT HISTORY</span><h2>最近战绩</h2><p>历史对局与撤离记录</p></div>
        <label class="escape-record-season-filter">
          <span>赛季</span>
          <select v-model="selectedRecordSeasonId">
            <option v-for="season in recordSeasons" :key="season.id" :value="season.id">{{ season.name }}</option>
          </select>
        </label>
        <section class="escape-record-section">
          <EscapeState v-if="!filteredRecords.length" type="empty" title="该赛季暂无战绩" description="完成对局并结算后，记录会保存在这里" />
          <div v-else class="escape-records">
            <article v-for="record in filteredRecords" :key="record.id" role="button" tabindex="0" @click="openRecord(record)" @keydown.enter="openRecord(record)">
              <i :class="{ success: record.extracted }"></i>
              <div><h3>{{ record.match_name }}</h3><p>{{ dateText(record.ended_at) }} · {{ record.squad_name }} · {{ record.class_name || '未配置' }}</p></div>
              <strong :class="{ loss: Number(record.net_income) < 0 }">{{ signedMoney(record.net_income) }}<small>{{ record.extracted ? '成功撤离' : '撤离失败' }}</small></strong>
            </article>
          </div>
        </section>
      </template>

      <EscapeMatchManagerView
        v-else-if="activeView === 'matches' && summary.can_manage_matches"
        :role="summary.role"
        @notify="$emit('notify', $event)"
        @updated="loadProfile"
      />
    </main>

    <LoadoutDialog
      :open="loadout.open"
      :loading="loadout.loading"
      :submitting="loadout.submitting"
      :error="loadout.error"
      :submit-error="loadout.submitError"
      :options="loadout.options"
      @close="loadout.open = false"
      @retry="loadLoadout"
      @submit="submitLoadout"
    />
    <MatchControlDialog
      :open="control.open"
      :loading="control.loading"
      :saving="control.saving"
      :error="control.error"
      :submit-error="control.submitError"
      :data="control.data"
      @close="control.open = false"
      @retry="loadMatchControl"
      @start="startControlledMatch"
      @confirm-special="confirmSpecialWeapon"
      @settle="settleControlledMatch"
    />
    <div v-if="historyWarehouse.open" class="escape-modal escape-history-modal">
      <button class="escape-modal-backdrop" type="button" aria-label="关闭" @click="historyWarehouse.open = false"></button>
      <section class="escape-history-dialog" role="dialog" aria-modal="true" aria-labelledby="escape-history-title">
        <header>
          <div><span>ARCHIVE STORAGE</span><h2 id="escape-history-title">历史仓库</h2></div>
          <button type="button" class="escape-icon-button" aria-label="关闭" @click="historyWarehouse.open = false">×</button>
        </header>
        <label class="escape-history-season"><span>选择赛季</span><select v-model="historyWarehouse.seasonId" :disabled="historyWarehouse.loading" @change="loadWarehouseHistory"><option v-for="season in historyWarehouse.seasons" :key="season.id" :value="String(season.id)">{{ season.name }}</option></select></label>
        <EscapeState v-if="historyWarehouse.loading" type="loading" title="正在读取历史仓库" description="正在加载赛季物资快照" />
        <EscapeState v-else-if="historyWarehouse.error" type="error" title="历史仓库加载失败" :description="historyWarehouse.error" action-label="重新加载" @action="loadWarehouseHistory" />
        <EscapeState v-else-if="!historyWarehouse.seasons.length" type="empty" title="暂无历史仓库" description="赛季结束后会在这里保留物资快照" />
        <EscapeWarehouseBoard v-else :warehouses="historyWarehouse.warehouses" readonly :show-history="false" />
      </section>
    </div>
  </section>
</template>

<script>
import { escapeApi } from './api'
import EscapeMatchManagerView from './EscapeMatchManagerView.vue'
import EscapeState from './EscapeState.vue'
import EscapeWarehouseBoard from './EscapeWarehouseBoard.vue'
import LoadoutDialog from './LoadoutDialog.vue'
import MatchControlDialog from './MatchControlDialog.vue'

const emptyWarehouse = type => ({ type, width: type === 'buffer' ? 48 : 12, height: type === 'buffer' ? 16 : 8, items: [] })

export default {
  name: 'EscapeApp',
  components: { EscapeMatchManagerView, EscapeState, EscapeWarehouseBoard, LoadoutDialog, MatchControlDialog },
  props: { user: { type: Object, default: () => ({}) } },
  emits: ['notify', 'back'],
  data() {
    return {
      activeView: 'profile',
      summary: {},
      matches: [],
      warehouses: { buffer: emptyWarehouse('buffer'), personal: emptyWarehouse('personal') },
      warehouseMoving: false,
      historyWarehouse: { open: false, loading: false, error: '', seasons: [], seasonId: '', warehouses: { buffer: emptyWarehouse('buffer'), personal: emptyWarehouse('personal') } },
      shopCategory: 'all',
      products: [],
      records: [],
      selectedRecordSeasonId: '',
      states: { profile: { loading: true, error: '' }, shop: { loading: false, error: '' }, records: { loading: false, error: '' } },
      loaded: new Set(),
      purchasingId: null,
      control: { open: false, loading: false, saving: false, error: '', submitError: '', match: null, data: {} },
      loadout: { open: false, match: null, loading: false, submitting: false, error: '', submitError: '', options: { squads: [], classes: [], weapons: [] } },
      shopFilters: [
        { value: 'all', label: '全部' },
        { value: 'expansion', label: '扩容' },
        { value: 'item', label: '物资' },
        { value: 'weapon', label: '武器' }
      ]
    }
  },
  computed: {
    navItems() {
      const items = [{ value: 'profile', label: '个人主页' }, { value: 'records', label: '最近战绩' }, { value: 'shop', label: '商店' }]
      if (this.summary.can_manage_matches) items.push({ value: 'matches', label: '对局管理' })
      return items
    },
    currentState() {
      return this.states[this.activeView] || { loading: false, error: '' }
    },
    navLabel() {
      return this.navItems.find(item => item.value === this.activeView)?.label || ''
    },
    recordSeasons() {
      const seasons = new Map()
      if (this.summary.season?.id != null) {
        seasons.set(String(this.summary.season.id), { id: String(this.summary.season.id), name: this.summary.season.name })
      }
      for (const record of this.records) {
        const id = record.season_id == null ? 'none' : String(record.season_id)
        if (!seasons.has(id)) seasons.set(id, { id, name: record.season_name || '未关联赛季' })
      }
      return [...seasons.values()]
    },
    filteredRecords() {
      return this.records.filter(record => (record.season_id == null ? 'none' : String(record.season_id)) === this.selectedRecordSeasonId)
    },
    rarityCards() {
      return [
        { value: 'extraordinary', label: '超凡' },
        { value: 'epic', label: '史诗' },
        { value: 'fine', label: '精品' },
        { value: 'normal', label: '普通' }
      ]
    }
  },
  mounted() {
    this.loadProfile()
  },
  methods: {
    setState(view, patch) {
      this.states[view] = { ...this.states[view], ...patch }
    },
    async request(view, task) {
      this.setState(view, { loading: true, error: '' })
      try {
        await task()
        this.loaded.add(view)
      } catch (error) {
        this.setState(view, { error: error.message || '网络异常，请稍后重试' })
      } finally {
        this.setState(view, { loading: false })
      }
    },
    loadActive() {
      if (this.activeView === 'shop') return this.loadShop()
      if (this.activeView === 'records') return this.loadRecords()
      return this.loadProfile()
    },
    loadProfile() {
      return this.request('profile', async () => {
        const [dashboard, matches, buffer, personal, records] = await Promise.all([
          escapeApi.dashboard(), escapeApi.matches(), escapeApi.warehouse('buffer'), escapeApi.warehouse('personal'), escapeApi.records()
        ])
        this.summary = {
          ...(dashboard || {}),
          callsign: dashboard?.display_name,
          cash: dashboard?.asset?.cash_balance,
          extraction_rate: dashboard?.stats?.escape_rate,
          average_income: dashboard?.stats?.average_cash,
          season: dashboard?.active_season
        }
        this.matches = (Array.isArray(matches) ? matches : matches?.items || []).map(item => ({
          ...item,
          member_count: item.participant_count,
          squad_count: item.team_count,
          capacity: Number(item.team_count || 0) * Number(item.team_capacity || 0),
          loadout_locked: ['locked', 'in_match', 'settled'].includes(item.loadout_status),
          team_members: Array.isArray(item.team_members) ? item.team_members : this.parseTeamMembers(item.team_members_json)
        }))
        this.warehouses = { buffer: this.normalizeWarehouse(buffer, 'buffer'), personal: this.normalizeWarehouse(personal, 'personal') }
        this.records = this.normalizeRecords(records)
        this.selectDefaultRecordSeason()
      })
    },
    normalizeRecords(records) {
      return (Array.isArray(records) ? records : records?.items || []).map(item => ({
          ...item,
          match_id: item.id,
          match_name: item.name,
          ended_at: item.settled_at,
          squad_name: item.team_no ? `第 ${item.team_no} 小队` : '独狼',
          class_name: item.profession_name,
          extracted: item.escaped === true || Number(item.escaped) === 1,
          net_income: Number(item.manual_cash || 0) + Number(item.kill_cash || 0)
        }))
    },
    parseTeamMembers(value) {
      if (!value) return []
      try { return typeof value === 'string' ? JSON.parse(value) : value } catch { return [] }
    },
    memberDisplayName(member) {
      return String(member?.callsign || '').trim() || String(member?.username || '').trim() || '未知成员'
    },
    selectDefaultRecordSeason() {
      const currentId = this.summary.season?.id == null ? '' : String(this.summary.season.id)
      const available = this.recordSeasons.map(season => season.id)
      if (!available.includes(this.selectedRecordSeasonId)) {
        this.selectedRecordSeasonId = available.includes(currentId) ? currentId : (available[0] || '')
      }
    },
    async loadWarehouses() {
      const [buffer, personal] = await Promise.all([escapeApi.warehouse('buffer'), escapeApi.warehouse('personal')])
      this.warehouses = { buffer: this.normalizeWarehouse(buffer, 'buffer'), personal: this.normalizeWarehouse(personal, 'personal') }
    },
    async openWarehouseHistory() {
      this.historyWarehouse.open = true
      this.historyWarehouse.loading = true
      this.historyWarehouse.error = ''
      try {
        const seasons = await escapeApi.warehouseHistorySeasons()
        this.historyWarehouse.seasons = Array.isArray(seasons) ? seasons : []
        this.historyWarehouse.seasonId = String(this.historyWarehouse.seasons[0]?.id || '')
        if (this.historyWarehouse.seasonId) await this.loadWarehouseHistory()
      } catch (error) {
        this.historyWarehouse.error = error.message || '历史仓库加载失败'
      } finally {
        this.historyWarehouse.loading = false
      }
    },
    async loadWarehouseHistory() {
      if (!this.historyWarehouse.seasonId) return
      this.historyWarehouse.loading = true
      this.historyWarehouse.error = ''
      try {
        const data = await escapeApi.warehouseHistory(this.historyWarehouse.seasonId)
        this.historyWarehouse.warehouses = {
          buffer: this.normalizeWarehouse(data?.buffer, 'buffer'),
          personal: this.normalizeWarehouse(data?.personal, 'personal')
        }
      } catch (error) {
        this.historyWarehouse.error = error.message || '历史仓库加载失败'
      } finally {
        this.historyWarehouse.loading = false
      }
    },
    normalizeWarehouse(data, type) {
      return { ...emptyWarehouse(type), ...(data || {}), items: data?.items || [] }
    },
    loadShop() {
      return this.request('shop', async () => {
        const data = await escapeApi.shop(this.shopCategory)
        const products = Array.isArray(data) ? data : data?.items || []
        this.products = this.shopCategory === 'all' ? products : products.filter(item => item.product_type === (this.shopCategory === 'item' ? 'regular' : this.shopCategory))
      })
    },
    loadRecords() {
      return this.request('records', async () => {
        const records = await escapeApi.records()
        this.records = this.normalizeRecords(records)
        this.selectDefaultRecordSeason()
      })
    },
    switchView(view) {
      if (view === 'matches' && !this.summary.can_manage_matches) return
      this.activeView = view
      if (view !== 'matches' && !this.loaded.has(view)) this.loadActive()
    },
    switchShop(category) {
      if (this.shopCategory === category) return
      this.shopCategory = category
      this.loadShop()
    },
    async moveInventory({ item, targetWarehouse, posX, posY }) {
      this.warehouseMoving = true
      try {
        await escapeApi.moveItem(item.inventory_id, { target_warehouse: targetWarehouse, pos_x: posX, pos_y: posY })
        await this.loadWarehouses()
        this.$emit('notify', `已移动到${targetWarehouse === 'buffer' ? '缓冲区' : '个人仓库'}`)
      } catch (error) {
        this.$emit('notify', error.message || '物品移动失败')
        await this.loadWarehouses().catch(() => {})
      } finally {
        this.warehouseMoving = false
      }
    },
    async sellItem(item) {
      if (!window.confirm(`确认出售“${item.name}”？此操作不可撤销。`)) return
      try {
        const data = await escapeApi.sellItem(item.inventory_id)
        if (data?.cash_balance != null) this.summary.cash = data.cash_balance
        await this.loadWarehouses()
        this.$emit('notify', '出售成功')
      } catch (error) {
        this.$emit('notify', error.message || '出售失败')
      }
    },
    async sellAllItems(type) {
      if (!window.confirm(`确认出售${type === 'buffer' ? '缓冲区' : '个人仓库'}全部可售物品？此操作不可撤销。`)) return
      try {
        const data = await escapeApi.sellAll(type)
        if (data?.cash_balance != null) this.summary.cash = data.cash_balance
        await this.loadWarehouses()
        this.$emit('notify', `已出售 ${data?.sold_count || 0} 件物品`)
      } catch (error) {
        this.$emit('notify', error.message || '批量出售失败')
      }
    },
    async openLoadout(match) {
      if (!match.loadout_status && !window.confirm(`确认加入对局“${match.name}”并开始配置装备？`)) return
      this.loadout.open = true
      this.loadout.match = match
      await this.loadLoadout()
    },
    async loadLoadout() {
      this.loadout.loading = true
      this.loadout.error = ''
      this.loadout.submitError = ''
      try {
        const data = await escapeApi.loadoutOptions(this.loadout.match.id)
        const teamCount = Number(data?.match?.team_count || 0)
        const teamCapacity = Number(data?.match?.team_capacity || 0)
        const participants = data?.participants || []
        const squads = Array.from({ length: teamCount }, (_, index) => {
          const teamNo = index + 1
          const members = participants.filter(item => Number(item.team_no) === teamNo)
          return { id: teamNo, name: `第 ${teamNo} 小队`, members, member_count: members.length, capacity: teamCapacity, full: members.length >= teamCapacity }
        })
        const classes = (data?.professions || []).map(item => ({ ...item, maintenance_cost: item.maintenance_fee }))
        const allWeapons = (data?.weapons || []).map(item => ({ ...item, price: item.usage_fee, type_label: { knife: '近战武器', regular: '普通武器', special: '特殊武器' }[item.weapon_type] || item.weapon_type }))
        const weapons = allWeapons.filter(item => item.weapon_type !== 'special')
        for (const inventory of data?.special_weapons || []) {
          const target = allWeapons.find(item => Number(item.id) === Number(inventory.weapon_id))
          if (target) weapons.push({ ...target, id: `${target.id}:${inventory.inventory_id}`, weapon_id: target.id, special_inventory_id: inventory.inventory_id, name: inventory.weapon_name || inventory.name, durability: inventory.durability_percent })
        }
        this.loadout.options = { squads, classes, weapons }
      } catch (error) {
        this.loadout.error = error.message || '无法读取配装'
      } finally {
        this.loadout.loading = false
      }
    },
    async submitLoadout(form) {
      this.loadout.submitting = true
      this.loadout.submitError = ''
      try {
        const selectedWeapon = this.loadout.options.weapons.find(item => String(item.id) === String(form.weapon_id))
        await escapeApi.saveLoadout(this.loadout.match.id, {
          team_no: Number(form.squad_id), profession_id: Number(form.class_id),
          weapon_id: Number(selectedWeapon?.weapon_id || selectedWeapon?.id || form.weapon_id), special_inventory_id: selectedWeapon?.special_inventory_id || null
        })
        await escapeApi.lockLoadout(this.loadout.match.id)
        this.loadout.open = false
        this.$emit('notify', '配装已保存并锁定')
        await this.loadProfile()
      } catch (error) {
        this.loadout.submitError = error.message || '配装锁定失败'
      } finally {
        this.loadout.submitting = false
      }
    },
    async openMatchControl(match) {
      this.control.open = true
      this.control.match = match
      this.control.submitError = ''
      await this.loadMatchControl()
    },
    async loadMatchControl() {
      this.control.loading = true
      this.control.error = ''
      try {
        this.control.data = this.control.match.status === 'in_progress' ? await escapeApi.settlementPreview(this.control.match.id) : await escapeApi.matchControl(this.control.match.id)
      } catch (error) {
        this.control.error = error.message || '对局控制加载失败'
      } finally {
        this.control.loading = false
      }
    },
    async startControlledMatch() {
      if (!window.confirm(`确认开始对局“${this.control.match.name}”？开始后将扣除全员费用。`)) return
      const specialUsers = (this.control.data?.participants || []).filter(item => item.weapon_type === 'special')
      if (specialUsers.length && !window.confirm(`本局有 ${specialUsers.length} 人携带特殊武器：${specialUsers.map(item => item.callsign).join('、')}。撤离失败时特殊武器将永久销毁，确认继续开始？`)) return
      this.control.saving = true
      try {
        await escapeApi.startMatch(this.control.match.id)
        this.$emit('notify', '对局已开始')
        this.control.open = false
        await this.loadProfile()
      } catch (error) {
        this.$emit('notify', error.message || '开始对局失败')
      } finally {
        this.control.saving = false
      }
    },
    async confirmSpecialWeapon(participant) {
      if (!window.confirm(`确认已核对 ${participant.callsign} 携带的特殊武器？`)) return
      this.control.saving = true
      try {
        this.control.data = await escapeApi.confirmSpecialWeapon(this.control.match.id, participant.id)
        this.$emit('notify', '特殊武器已确认')
      } catch (error) {
        this.$emit('notify', error.message || '特殊武器确认失败')
      } finally {
        this.control.saving = false
      }
    },
    async settleControlledMatch(body) {
      if (!window.confirm('最终结算不可撤销，确认提交全部参与者的结算结果？')) return
      this.control.saving = true
      this.control.submitError = ''
      try {
        let result = await escapeApi.settleMatch(this.control.match.id, body)
        if (result?.match?.status !== 'settled') result = await escapeApi.settlementPreview(this.control.match.id)
        if (result?.match?.status !== 'settled') throw new Error('后端未确认战局结算完成，请重试')
        this.control.data = result
        this.control.match = { ...this.control.match, status: 'settled' }
        await this.loadProfile()
        this.$emit('notify', '对局结算完成')
      } catch (error) {
        this.control.submitError = error.message || '对局结算失败'
        this.$emit('notify', this.control.submitError)
      } finally {
        this.control.saving = false
      }
    },
    async purchase(product) {
      if (!window.confirm(`确认花费 ¥${this.money(product.price)} 购买“${product.name}”？`)) return
      this.purchasingId = product.id
      try {
        const data = await escapeApi.purchase(product.id)
        if (data?.cash_balance != null) this.summary.cash = data.cash_balance
        this.$emit('notify', product.product_type === 'expansion' ? '仓库扩容成功' : '购买成功，物品已进入缓冲区')
        await Promise.all([this.loadShop(), this.loadWarehouses()])
      } catch (error) {
        this.$emit('notify', error.message || '购买失败')
      } finally {
        this.purchasingId = null
      }
    },
    async openRecord(record) {
      try {
        const detail = await escapeApi.recordDetail(record.match_id || record.id)
        const itemCount = (detail?.items || []).reduce((total, item) => total + Number(item.quantity || 0), 0)
        const participant = detail?.participants?.[0] || {}
        window.alert(`${record.match_name || '对局'}\n击杀：${participant.kills ?? record.kills ?? 0}\n结算物品：${itemCount} 件`)
      } catch (error) {
        this.$emit('notify', error.message || '战绩详情加载失败')
      }
    },
    money(value) {
      return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
    },
    signedMoney(value) {
      const number = Number(value || 0)
      return `${number >= 0 ? '+' : '-'}¥${this.money(Math.abs(number))}`
    },
    percent(value) {
      const number = Number(value || 0)
      return `${number > 0 && number <= 1 ? Math.round(number * 100) : Math.round(number)}%`
    },
    shortName(value) {
      return String(value || '甬').slice(0, 2)
    },
    itemSymbol(item) {
      return String(item.name || '物').slice(0, 1)
    },
    rarityText(value) {
      return { extraordinary: '超凡', epic: '史诗', fine: '精品', premium: '精品', normal: '普通' }[value] || '普通'
    },
    statusText(status) {
      return { preparing: '整备中', in_progress: '进行中', settled: '已结算', cancelled: '已取消' }[status] || status || '未知状态'
    },
    dateText(value) {
      if (!value) return '时间待定'
      const date = new Date(String(value).replace(' ', 'T'))
      return Number.isNaN(date.getTime()) ? value : `${String(date.getMonth() + 1).padStart(2, '0')}月${String(date.getDate()).padStart(2, '0')}日`
    },
    seasonRemaining(season) {
      if (!season?.end_date) return '赛季进行中'
      const days = Math.max(0, Math.ceil((new Date(`${season.end_date}T23:59:59`).getTime() - Date.now()) / 86400000))
      return `剩余 ${days} 天`
    },
    seasonProgress(season) {
      if (!season?.start_date || !season?.end_date) return '0%'
      const start = new Date(`${season.start_date}T00:00:00`).getTime()
      const end = new Date(`${season.end_date}T23:59:59`).getTime()
      const rate = end > start ? (Date.now() - start) / (end - start) * 100 : 0
      return `${Math.max(0, Math.min(100, rate))}%`
    },
    seasonDateRange(season) {
      if (!season?.start_date || !season?.end_date) return ''
      return `${String(season.start_date).slice(5)} 至 ${String(season.end_date).slice(5)}`
    },
    coverStyle(match) {
      return match.banner_url ? { backgroundImage: `linear-gradient(90deg, rgba(3,7,8,.8), rgba(3,7,8,.15)), url("${match.banner_url}")` } : {}
    }
  }
}
</script>
