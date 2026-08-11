<template>
  <div v-if="open" class="escape-modal">
    <button v-if="!isFinalized" class="escape-modal-backdrop" type="button" aria-label="关闭战局控制" :disabled="saving" @click="requestClose"></button>
    <section class="escape-control-dialog" role="dialog" aria-modal="true" aria-labelledby="escape-control-title">
      <header>
        <div>
          <span>FIELD CONTROL</span>
          <h2 id="escape-control-title">战局控制</h2>
          <p>{{ match?.name || '当前战局' }}</p>
        </div>
        <button v-if="!isFinalized" class="escape-icon-button" type="button" aria-label="关闭" :disabled="saving" @click="requestClose">×</button>
      </header>

      <EscapeState
        v-if="loading"
        type="loading"
        title="正在读取战局状态"
        description="同步参与者、配装与结算信息"
      />
      <EscapeState
        v-else-if="error"
        type="error"
        title="战局控制加载失败"
        :description="error"
        action-label="重试"
        @action="$emit('retry')"
      />

      <template v-else>
        <div class="escape-control-summary">
          <span>{{ statusText }}</span>
          <strong>{{ participants.length }} 人</strong>
          <small v-if="isPreparing">已锁定 {{ lockedCount }}/{{ participants.length }}</small>
          <small v-else>每击杀奖励 ¥{{ money(killReward) }}</small>
        </div>

        <div v-if="isPreparing" class="escape-control-members">
          <article v-for="participant in participants" :key="participant.id">
            <div>
              <strong>{{ participant.callsign }}</strong>
              <small>第 {{ participant.team_no || '-' }} 小队 · {{ participant.profession_name || '未选职业' }}</small>
              <small>{{ participant.weapon_name || '未选武器' }}</small>
            </div>
            <span :class="{ ready: participant.loadout_status === 'locked' }">
              {{ participant.loadout_status === 'locked' ? '已锁定' : '未锁定' }}
            </span>
          </article>
          <p class="escape-warning-copy">开始后将一次性校验全员配装和余额，并扣除职业及武器费用。</p>
          <button class="escape-primary wide" type="button" :disabled="saving || !allLocked" @click="$emit('start')">
            {{ saving ? '正在开始…' : allLocked ? '确认开始战局' : '仍有成员未锁定' }}
          </button>
        </div>

        <section v-else-if="isFinalized" class="escape-state escape-control-complete">
          <div class="escape-state-icon">✓</div>
          <h3>战局结算完成</h3>
          <p>本场参与人员的现金、物品和特殊武器状态已更新。</p>
          <div class="escape-control-summary">
            <span>已完成最终结算</span>
            <strong>{{ participants.length }} 人</strong>
            <small>{{ data?.settlement?.settled_at ? formatDate(data.settlement.settled_at) : '数据已同步' }}</small>
          </div>
        </section>
        <form v-else class="escape-control-settlement" @submit.prevent="submitSettlement">
          <label>
            <span>结算说明</span>
            <textarea v-model.trim="note" rows="2" maxlength="200" placeholder="填写本场结算说明" required></textarea>
          </label>
          <article v-for="participant in drafts" :key="participant.id">
            <div class="escape-control-member-head">
              <div><strong>{{ participant.callsign }}</strong><small>第 {{ participant.team_no || '-' }} 小队</small></div>
              <label class="escape-control-switch">
                <input v-model="participant.escaped" type="checkbox" @change="onEscapeChange(participant)" />
                <span>成功撤离</span>
              </label>
            </div>
            <div class="escape-control-fields">
              <label><span>击杀数</span><input v-model.number="participant.kills" type="number" min="0" :max="teamKillLimit(participant)" inputmode="numeric" /></label>
              <label><span>人工现金</span><input v-model.number="participant.manual_cash" type="number" min="0" step="0.01" inputmode="decimal" /></label>
            </div>
            <div class="escape-settlement-items" :class="{ disabled: !participant.escaped }">
              <small v-if="!participant.escaped" class="escape-settlement-hint">未撤离不能带出物资</small>
              <div class="escape-settlement-item-add">
                <label><span>结算物品</span><select v-model.number="participant.pending_item_id" :disabled="!participant.escaped">
                  <option value="">选择本局物品</option>
                  <option
                    v-for="item in matchItems"
                    :key="item.item_id"
                    :value="item.item_id"
                    :disabled="itemAlreadySelected(participant, item.item_id) || poolRemaining(item.item_id) <= 0"
                  >{{ item.name }}（剩余 {{ poolRemaining(item.item_id) }}）</option>
                </select></label>
                <button type="button" aria-label="添加物品" title="添加物品" :disabled="!participant.escaped || !participant.pending_item_id" @click="addSelectedItem(participant)">＋</button>
                <button type="button" class="scan" aria-label="扫描物品二维码" title="扫描物品二维码" :disabled="!participant.escaped" @click="openScanner(participant)">⌗</button>
              </div>
              <div v-for="(selected, itemIndex) in participant.items" :key="selected.item_id" class="escape-settlement-item-row">
                <span>{{ itemName(selected.item_id) }}</span>
                <label><span>数量</span><input v-model.number="selected.quantity" type="number" min="1" :max="maxQuantity(selected)" inputmode="numeric" /></label>
                <button type="button" aria-label="移除物品" title="移除物品" @click="participant.items.splice(itemIndex, 1)">×</button>
              </div>
            </div>
            <small class="escape-kill-reward">击杀奖励：¥{{ money(Number(participant.kills || 0) * killReward) }}</small>
          </article>
          <p v-if="formError" class="escape-form-error">{{ formError }}</p>
          <p v-if="submitError" class="escape-form-error">{{ submitError }}</p>
          <p class="escape-warning-copy">最终结算不可撤销，将一次性更新所有人的现金、物品和特殊武器状态。</p>
          <button class="escape-control-danger" type="submit" :disabled="saving || !drafts.length">
            {{ saving ? '正在结算…' : '确认最终结算' }}
          </button>
        </form>
      </template>
    </section>

    <div v-if="scanner.open" class="escape-item-scanner">
      <button class="escape-modal-backdrop" type="button" aria-label="关闭扫码" @click="closeScanner"></button>
      <section role="dialog" aria-modal="true" aria-labelledby="escape-scanner-title">
        <header><div><span>QR SCAN</span><h3 id="escape-scanner-title">扫描物品二维码</h3></div><button type="button" aria-label="关闭扫码" @click="closeScanner">×</button></header>
        <template v-if="!scanner.item">
          <div id="escape-settlement-qr-reader" class="escape-qr-reader"></div>
        </template>
        <template v-else>
          <div class="escape-scanned-item"><strong>{{ scanner.item.name }}</strong><small>物品 ID {{ scanner.item.item_id }} · 本局剩余 {{ poolRemaining(scanner.item.item_id) }}</small></div>
          <label class="escape-scanned-quantity"><span>结算数量</span><input v-model.number="scanner.quantity" type="number" min="1" :max="poolRemaining(scanner.item.item_id)" inputmode="numeric" /></label>
          <button class="escape-primary wide" type="button" @click="confirmScannedItem">确认添加</button>
        </template>
        <p v-if="scanner.error" class="escape-form-error">{{ scanner.error }}</p>
      </section>
    </div>
  </div>
</template>

<script>
import EscapeState from './EscapeState.vue'
import { Html5Qrcode } from 'html5-qrcode'

export default {
  name: 'MatchControlDialog',
  components: { EscapeState },
  props: {
    open: Boolean,
    loading: Boolean,
    saving: Boolean,
    error: { type: String, default: '' },
    submitError: { type: String, default: '' },
    data: { type: Object, default: () => ({}) }
  },
  emits: ['close', 'retry', 'start', 'settle'],
  data() {
    return {
      note: '', drafts: [], formError: '', qrScanner: null,
      scanner: { open: false, participantId: null, item: null, quantity: 1, error: '' }
    }
  },
  computed: {
    match() {
      return this.data?.match || {}
    },
    participants() {
      return this.data?.participants || []
    },
    isPreparing() {
      return this.match.status === 'preparing'
    },
    isFinalized() {
      return this.match.status === 'settled'
    },
    statusText() {
      return this.isPreparing ? '整备中' : this.match.status === 'in_progress' ? '进行中' : '已结算'
    },
    lockedCount() {
      return this.participants.filter(item => item.loadout_status === 'locked').length
    },
    allLocked() {
      return this.participants.length > 0 && this.lockedCount === this.participants.length
    },
    killReward() {
      return Number(this.match.kill_reward || 0)
    },
    matchItems() {
      return this.data?.match_items || []
    }
  },
  watch: {
    data: {
      immediate: true,
      deep: true,
      handler(value) {
        this.formError = ''
        this.note = ''
        this.drafts = (value?.participants || []).map(item => ({
          ...item,
          escaped: false,
          kills: 0,
          manual_cash: 0,
          pending_item_id: '',
          items: []
        }))
      }
    },
    open(value) {
      if (!value) this.stopScanner()
    }
  },
  beforeUnmount() {
    this.stopScanner()
  },
  methods: {
    requestClose() {
      if (this.saving) return
      this.closeScanner()
      this.$emit('close')
    },
    money(value) {
      return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(Number(value || 0))
    },
    formatDate(value) {
      if (!value) return ''
      return new Date(value).toLocaleString('zh-CN', { dateStyle: 'short', timeStyle: 'short' })
    },
    itemName(itemId) {
      return this.matchItems.find(item => Number(item.item_id) === Number(itemId))?.name || `物品 ${itemId}`
    },
    onEscapeChange(participant) {
      if (participant.escaped) return
      participant.pending_item_id = ''
      participant.items = []
      if (Number(this.scanner.participantId) === Number(participant.id)) this.closeScanner()
    },
    itemAlreadySelected(participant, itemId) {
      return participant.items.some(item => Number(item.item_id) === Number(itemId))
    },
    selectedTotal(itemId) {
      return this.drafts.reduce((total, participant) => total + participant.items
        .filter(item => Number(item.item_id) === Number(itemId))
        .reduce((sum, item) => sum + Math.max(0, Number(item.quantity || 0)), 0), 0)
    },
    poolRemaining(itemId) {
      const item = this.matchItems.find(row => Number(row.item_id) === Number(itemId))
      return Math.max(0, Number(item?.remaining_quantity || 0) - this.selectedTotal(itemId))
    },
    maxQuantity(selected) {
      return Math.max(1, Number(selected.quantity || 0) + this.poolRemaining(selected.item_id))
    },
    teamKillLimit(participant) {
      const teamSize = this.drafts.filter(item => Number(item.team_no) === Number(participant.team_no)).length
      return Math.max(0, this.drafts.length - teamSize)
    },
    teamKillValidationError() {
      const teams = new Map()
      for (const participant of this.drafts) {
        const kills = Number(participant.kills || 0)
        if (!Number.isInteger(kills) || kills < 0) return `${participant.callsign} 的击杀数必须为非负整数`
        const teamNo = Number(participant.team_no)
        if (!Number.isInteger(teamNo) || teamNo < 1) return `${participant.callsign} 尚未分配小队，无法结算`
        const team = teams.get(teamNo) || { size: 0, kills: 0 }
        team.size += 1
        team.kills += kills
        teams.set(teamNo, team)
      }
      for (const [teamNo, team] of teams) {
        const limit = this.drafts.length - team.size
        if (team.kills > limit) return `第 ${teamNo} 小队击杀数合计为 ${team.kills}，不能超过 ${limit}`
      }
      return ''
    },
    addSelectedItem(participant) {
      const itemId = Number(participant.pending_item_id)
      if (!itemId || this.itemAlreadySelected(participant, itemId) || this.poolRemaining(itemId) <= 0) return
      participant.items.push({ item_id: itemId, quantity: 1 })
      participant.pending_item_id = ''
    },
    async openScanner(participant) {
      if (!participant.escaped) return
      await this.stopScanner()
      this.scanner = { open: true, participantId: participant.id, item: null, quantity: 1, error: '' }
      await this.$nextTick()
      try {
        this.qrScanner = new Html5Qrcode('escape-settlement-qr-reader')
        await this.qrScanner.start(
          { facingMode: 'environment' },
          { fps: 10, qrbox: { width: 220, height: 220 }, aspectRatio: 1 },
          decodedText => this.handleScannedItem(decodedText),
          () => {}
        )
      } catch (error) {
        await this.stopScanner(false)
        this.scanner.error = '无法打开摄像头，请允许相机权限后重试'
      }
    },
    async handleScannedItem(decodedText) {
      const itemId = Number(String(decodedText || '').trim())
      const item = this.matchItems.find(row => Number(row.item_id) === itemId)
      if (!Number.isInteger(itemId) || itemId <= 0 || !item) {
        this.scanner.error = '二维码中的物品不属于本局'
        return
      }
      if (this.poolRemaining(itemId) <= 0) {
        this.scanner.error = '该物品本局数量已全部分配'
        return
      }
      await this.stopScanner(false)
      this.scanner.item = item
      this.scanner.quantity = 1
      this.scanner.error = ''
    },
    confirmScannedItem() {
      const participant = this.drafts.find(item => Number(item.id) === Number(this.scanner.participantId))
      const item = this.scanner.item
      const quantity = Number(this.scanner.quantity)
      if (!participant || !item || !Number.isInteger(quantity) || quantity < 1 || quantity > this.poolRemaining(item.item_id)) {
        this.scanner.error = '请输入不超过本局剩余量的整数'
        return
      }
      const selected = participant.items.find(row => Number(row.item_id) === Number(item.item_id))
      if (selected) selected.quantity = Number(selected.quantity || 0) + quantity
      else participant.items.push({ item_id: Number(item.item_id), quantity })
      this.closeScanner()
    },
    async stopScanner(restoreState = true) {
      const scanner = this.qrScanner
      this.qrScanner = null
      if (scanner) {
        try {
          if (scanner.isScanning) await scanner.stop()
          scanner.clear()
        } catch {}
      }
      if (restoreState && !this.scanner.open) this.scanner = { open: false, participantId: null, item: null, quantity: 1, error: '' }
    },
    async closeScanner() {
      this.scanner.open = false
      await this.stopScanner()
    },
    submitSettlement() {
      this.formError = ''
      if (!this.note) {
        this.formError = '请填写结算说明'
        return
      }
      try {
        const teamKillError = this.teamKillValidationError()
        if (teamKillError) throw new Error(teamKillError)
        const exceeded = this.matchItems.find(item => this.selectedTotal(item.item_id) > Number(item.remaining_quantity || 0))
        if (exceeded) throw new Error(`“${exceeded.name}”的结算数量超过本局可用量`)
        const participants = this.drafts.map(item => ({
          participant_id: item.id,
          escaped: !!item.escaped,
          kills: Math.max(0, Number(item.kills || 0)),
          manual_cash: Math.max(0, Number(item.manual_cash || 0)),
          items: item.escaped ? item.items.map(selected => {
            const quantity = Number(selected.quantity)
            if (!Number.isInteger(quantity) || quantity < 1 || quantity > this.maxQuantity(selected)) {
              throw new Error(`${item.callsign} 的物品数量无效`)
            }
            return { item_id: Number(selected.item_id), quantity }
          }) : []
        }))
        this.$emit('settle', { note: this.note, participants })
      } catch (error) {
        this.formError = error.message || '结算数据格式错误'
      }
    }
  }
}
</script>
