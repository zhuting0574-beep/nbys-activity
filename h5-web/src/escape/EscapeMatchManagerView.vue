<template>
  <section class="escape-manager-view">
    <header class="escape-manager-heading">
      <div><span>FIELD OPERATIONS</span><h2>对局管理</h2><p>当前身份：{{ role === 'superadmin' ? '超级管理员' : '活动发起人' }}</p></div>
      <button type="button" class="escape-manager-create" @click="openEditor()">＋ 创建</button>
    </header>

    <EscapeState v-if="loading" type="loading" title="正在加载对局" description="同步对局状态与参与成员" />
    <EscapeState v-else-if="error" type="error" title="对局管理加载失败" :description="error" action-label="重试" @action="load" />
    <template v-else>
      <div class="escape-manager-summary">
        <div><span>整备中</span><strong>{{ statusCount('preparing') }}</strong></div>
        <div><span>进行中</span><strong>{{ statusCount('in_progress') }}</strong></div>
        <div><span>已结算</span><strong>{{ statusCount('settled') }}</strong></div>
      </div>
      <div class="escape-manager-filters">
        <button v-for="item in filters" :key="item.value" type="button" :class="{ active: filter === item.value }" @click="filter = item.value">{{ item.label }}</button>
      </div>
      <EscapeState v-if="!filteredMatches.length" type="empty" title="暂无对局" description="创建后即可邀请队员加入并完成整备" />
      <div v-else class="escape-managed-matches">
        <article v-for="match in filteredMatches" :key="match.id" :class="match.status">
          <header>
            <div><span class="escape-status" :class="match.status">{{ statusText(match.status) }}</span><small>XP-{{ match.id }}</small></div>
            <button v-if="match.status === 'preparing'" type="button" aria-label="编辑对局" title="编辑对局" @click="openEditor(match)">✎</button>
          </header>
          <h3>{{ match.name }}</h3>
          <p>{{ match.venue_name || '场地待定' }} · {{ match.season_name || '未关联赛季' }}</p>
          <div class="escape-match-numbers">
            <span><b>{{ match.participant_count || 0 }} / {{ match.capacity || '-' }}</b> 玩家</span>
            <span v-if="match.status === 'preparing'"><b>{{ match.locked_count || 0 }}</b> 已锁定</span>
            <span><b>{{ match.team_count || 0 }}</b> 小队</span>
          </div>
          <div class="escape-manager-actions">
            <button v-if="match.status === 'preparing'" type="button" class="danger" @click="cancelMatch(match)">取消对局</button>
            <button v-if="['preparing', 'in_progress'].includes(match.status)" type="button" class="primary" @click="openControl(match)">
              {{ match.status === 'preparing' ? '查看整备 / 开始' : '进入结算' }}
            </button>
            <button v-if="match.status === 'cancelled'" type="button" class="danger" @click="deleteMatch(match)">删除对局</button>
            <span v-else class="escape-manager-finished">结算已完成</span>
          </div>
        </article>
      </div>
    </template>

    <div v-if="editor.open" class="escape-modal">
      <button class="escape-modal-backdrop" type="button" aria-label="关闭创建对局" @click="editor.open = false"></button>
      <form class="escape-manager-editor" @submit.prevent="saveMatch">
        <header>
          <div><span>NEW OPERATION</span><h2>{{ editor.id ? '编辑对局' : '创建对局' }}</h2></div>
          <button type="button" class="escape-icon-button" aria-label="关闭" @click="editor.open = false">×</button>
        </header>
        <label><span>对局名称</span><input v-model.trim="editor.name" maxlength="100" required /></label>
        <label><span>活动场地</span><select v-model="editor.venue_id"><option value="">暂不选择</option><option v-for="venue in options.venues" :key="venue.id" :value="venue.id">{{ venue.name }}</option></select></label>
        <label><span>关联赛季</span><select v-model="editor.season_id"><option value="">暂不选择</option><option v-for="season in options.seasons" :key="season.id" :value="season.id">{{ season.name }}</option></select></label>
        <div class="escape-manager-field-pair">
          <label><span>小队数量</span><input v-model.number="editor.team_count" type="number" min="1" max="99" inputmode="numeric" required /></label>
          <label><span>每队上限</span><input v-model.number="editor.team_capacity" type="number" min="1" max="99" inputmode="numeric" required /></label>
        </div>
        <section class="escape-match-item-editor">
          <header><div><strong>带入本局的物品</strong><small>保存后将从后管物品数量中扣减</small></div><button type="button" @click="addMatchItem">＋ 添加</button></header>
          <p v-if="!editor.match_items.length" class="escape-match-item-empty">暂未选择物品</p>
          <div v-for="(row, index) in editor.match_items" :key="row.key" class="escape-match-item-row">
            <select v-model.number="row.item_id" aria-label="选择物品" required>
              <option value="">请选择物品</option>
              <option
                v-for="item in options.items"
                :key="item.id"
                :value="item.id"
                :disabled="itemSelectedElsewhere(item.id, index)"
              >{{ item.name }}（可用 {{ itemAvailable(item, row) }}）</option>
            </select>
            <input v-model.number="row.quantity" type="number" min="1" :max="selectedItemAvailable(row)" inputmode="numeric" aria-label="带入数量" required />
            <button type="button" aria-label="移除物品" title="移除物品" @click="removeMatchItem(index)">×</button>
          </div>
        </section>
        <p v-if="editor.error" class="escape-form-error">{{ editor.error }}</p>
        <button class="escape-primary wide" type="submit" :disabled="editor.saving || editor.loading">{{ editor.saving ? '正在保存…' : editor.loading ? '正在读取…' : '确认保存' }}</button>
      </form>
    </div>

    <MatchControlDialog
      :open="control.open"
      :loading="control.loading"
      :saving="control.saving"
      :error="control.error"
      :submit-error="control.submitError"
      :data="control.data"
      @close="control.open = false"
      @retry="loadControl"
      @start="startMatch"
      @settle="settleMatch"
    />
  </section>
</template>

<script>
import { escapeApi } from './api'
import EscapeState from './EscapeState.vue'
import MatchControlDialog from './MatchControlDialog.vue'

let itemRowKey = 0
const emptyEditor = () => ({ open: false, id: null, name: '', venue_id: '', season_id: '', team_count: 2, team_capacity: 4, match_items: [], loading: false, saving: false, error: '' })

export default {
  name: 'EscapeMatchManagerView',
  components: { EscapeState, MatchControlDialog },
  props: { role: { type: String, default: '' } },
  emits: ['notify', 'updated'],
  data() {
    return {
      loading: true,
      error: '',
      matches: [],
      options: { venues: [], seasons: [], items: [] },
      filter: 'all',
      filters: [
        { value: 'all', label: '全部' },
        { value: 'preparing', label: '整备中' },
        { value: 'in_progress', label: '进行中' },
        { value: 'settled', label: '已结束' }
      ],
      editor: emptyEditor(),
      control: { open: false, loading: false, saving: false, error: '', submitError: '', match: null, data: {} }
    }
  },
  computed: {
    filteredMatches() {
      if (this.filter === 'all') return this.matches
      if (this.filter === 'settled') return this.matches.filter(item => ['settled', 'cancelled'].includes(item.status))
      return this.matches.filter(item => item.status === this.filter)
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const [matches, options] = await Promise.all([escapeApi.managedMatches(), escapeApi.matchOptions()])
        this.matches = Array.isArray(matches) ? matches : matches?.items || []
        this.options = { venues: options?.venues || [], seasons: options?.seasons || [], items: options?.items || [] }
      } catch (error) {
        this.error = error.message || '对局管理加载失败'
      } finally {
        this.loading = false
      }
    },
    statusCount(status) {
      return this.matches.filter(item => item.status === status).length
    },
    statusText(status) {
      return { preparing: '整备中', in_progress: '进行中', settled: '已结算', cancelled: '已取消' }[status] || status
    },
    async openEditor(match) {
      this.editor = {
        ...emptyEditor(),
        open: true,
        id: match?.id || null,
        name: match?.name || '',
        venue_id: match?.venue_id || '',
        season_id: match?.season_id || '',
        team_count: Number(match?.team_count || 2),
        team_capacity: Number(match?.team_capacity || 4),
        loading: !!match
      }
      if (!match) return
      try {
        const detail = await escapeApi.managedMatch(match.id)
        const rows = detail?.match_items || []
        this.editor.match_items = rows.map(item => ({
          key: ++itemRowKey,
          item_id: Number(item.item_id),
          quantity: Number(item.allocated_quantity),
          original_item_id: Number(item.item_id),
          original_quantity: Number(item.allocated_quantity)
        }))
        for (const item of rows) {
          if (!this.options.items.some(option => Number(option.id) === Number(item.item_id))) {
            this.options.items.push({ id: item.item_id, name: item.name, stock_quantity: item.stock_quantity || 0 })
          }
        }
      } catch (error) {
        this.editor.error = error.message || '读取战局物品失败'
      } finally {
        this.editor.loading = false
      }
    },
    addMatchItem() {
      this.editor.match_items.push({ key: ++itemRowKey, item_id: '', quantity: 1, original_item_id: null, original_quantity: 0 })
    },
    removeMatchItem(index) {
      this.editor.match_items.splice(index, 1)
    },
    itemSelectedElsewhere(itemId, rowIndex) {
      return this.editor.match_items.some((row, index) => index !== rowIndex && Number(row.item_id) === Number(itemId))
    },
    itemAvailable(item, row) {
      return Number(item.stock_quantity || 0) + (Number(row.original_item_id) === Number(item.id) ? Number(row.original_quantity || 0) : 0)
    },
    selectedItemAvailable(row) {
      const item = this.options.items.find(option => Number(option.id) === Number(row.item_id))
      return item ? Math.max(0, this.itemAvailable(item, row)) : 0
    },
    async saveMatch() {
      this.editor.error = ''
      if (!this.editor.name || this.editor.team_count < 1 || this.editor.team_capacity < 1) {
        this.editor.error = '请完整填写对局名称和小队容量'
        return
      }
      const invalidItem = this.editor.match_items.find(row => !row.item_id || Number(row.quantity) < 1 || Number(row.quantity) > this.selectedItemAvailable(row))
      if (invalidItem) {
        this.editor.error = '请选择有效物品并确认带入数量不超过可用库存'
        return
      }
      this.editor.saving = true
      const body = {
        name: this.editor.name,
        venue_id: this.editor.venue_id || null,
        season_id: this.editor.season_id || null,
        team_count: Number(this.editor.team_count),
        team_capacity: Number(this.editor.team_capacity),
        match_items: this.editor.match_items.map(item => ({ item_id: Number(item.item_id), quantity: Number(item.quantity) }))
      }
      try {
        if (this.editor.id) await escapeApi.updateManagedMatch(this.editor.id, body)
        else await escapeApi.createManagedMatch(body)
        this.editor.open = false
        this.$emit('notify', this.editor.id ? '对局已更新' : '对局已创建')
        await this.load()
        this.$emit('updated')
      } catch (error) {
        this.editor.error = error.message || '保存对局失败'
      } finally {
        this.editor.saving = false
      }
    },
    async cancelMatch(match) {
      if (!window.confirm(`确认取消对局“${match.name}”？`)) return
      try {
        await escapeApi.cancelManagedMatch(match.id)
        this.$emit('notify', '对局已取消')
        await this.load()
        this.$emit('updated')
      } catch (error) {
        this.$emit('notify', error.message || '取消对局失败')
      }
    },
    async deleteMatch(match) {
      if (!window.confirm(`确认永久删除已取消的对局“${match.name}”？此操作不可恢复。`)) return
      try {
        await escapeApi.deleteManagedMatch(match.id)
        this.$emit('notify', '对局已删除')
        await this.load()
        this.$emit('updated')
      } catch (error) {
        this.$emit('notify', error.message || '删除对局失败')
      }
    },
    async openControl(match) {
      this.control.open = true
      this.control.match = match
      this.control.submitError = ''
      await this.loadControl()
    },
    async loadControl() {
      this.control.loading = true
      this.control.error = ''
      try {
        this.control.data = this.control.match.status === 'in_progress'
          ? await escapeApi.settlementPreview(this.control.match.id)
          : await escapeApi.matchControl(this.control.match.id)
      } catch (error) {
        this.control.error = error.message || '对局控制加载失败'
      } finally {
        this.control.loading = false
      }
    },
    async startMatch() {
      if (!window.confirm(`确认开始对局“${this.control.match.name}”？开始后将扣除全员费用。`)) return
      this.control.saving = true
      try {
        await escapeApi.startMatch(this.control.match.id)
        this.$emit('notify', '对局已开始')
        this.control.open = false
        await this.load()
        this.$emit('updated')
      } catch (error) {
        this.$emit('notify', error.message || '开始对局失败')
      } finally {
        this.control.saving = false
      }
    },
    async settleMatch(body) {
      if (!window.confirm('最终结算不可撤销，确认提交全部参与者的结算结果？')) return
      this.control.saving = true
      this.control.submitError = ''
      try {
        let result = await escapeApi.settleMatch(this.control.match.id, body)
        if (result?.match?.status !== 'settled') result = await escapeApi.settlementPreview(this.control.match.id)
        if (result?.match?.status !== 'settled') throw new Error('后端未确认战局结算完成，请重试')
        this.control.data = result
        this.control.match = { ...this.control.match, status: 'settled' }
        await this.load()
        this.$emit('notify', '对局结算完成')
        this.control.open = false
        this.$emit('updated')
      } catch (error) {
        this.control.submitError = error.message || '对局结算失败'
        this.$emit('notify', this.control.submitError)
      } finally {
        this.control.saving = false
      }
    }
  }
}
</script>
