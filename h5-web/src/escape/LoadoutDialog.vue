<template>
  <Teleport to="body">
    <div v-if="open" class="escape-modal" role="dialog" aria-modal="true" aria-labelledby="escape-loadout-title">
      <button class="escape-modal-backdrop" aria-label="关闭配装" @click="$emit('close')"></button>
      <form class="escape-loadout-dialog" @submit.prevent="submit">
        <header>
          <div><span>LOADOUT</span><h2 id="escape-loadout-title">战前配装</h2></div>
          <button type="button" class="escape-icon-button" aria-label="关闭" @click="$emit('close')">×</button>
          <button type="button" class="escape-icon-button" aria-label="刷新小队成员" title="刷新小队成员" @click="$emit('retry')">↻</button>
        </header>

        <EscapeState
          v-if="loading"
          type="loading"
          title="正在读取可用配装"
          description="正在核对队伍、余额和仓库武器"
        />
        <EscapeState
          v-else-if="error"
          type="error"
          title="配装加载失败"
          :description="error"
          action-label="重新加载"
          @action="$emit('retry')"
        />
        <template v-else>
          <label>
            <span>选择小队</span>
            <select v-model="form.squad_id" required>
              <option disabled value="">请选择小队</option>
              <option v-for="squad in options.squads" :key="squad.id" :value="squad.id" :disabled="squad.full">
                {{ squad.name }} · {{ squad.member_count || 0 }}/{{ squad.capacity || '-' }} 人
              </option>
            </select>
            <div class="escape-squad-members">
              <span v-for="member in (options.squads.find(item => String(item.id) === String(form.squad_id))?.members || [])" :key="member.user_id">
                {{ member.callsign }}
              </span>
              <small v-if="!(options.squads.find(item => String(item.id) === String(form.squad_id))?.members || []).length">暂无成员</small>
            </div>
          </label>

          <div class="escape-choice-heading"><span>职业</span><small>费用在战局开始时扣除</small></div>
          <div class="escape-choice-grid">
            <button
              v-for="item in options.classes"
              :key="item.id"
              type="button"
              :class="{ selected: form.class_id === item.id }"
              @click="form.class_id = item.id"
            >
              {{ item.name }}<small>¥{{ money(item.maintenance_cost) }}</small>
            </button>
          </div>

          <label>
            <span>主武器</span>
            <select v-model="form.weapon_id" required>
              <option disabled value="">请选择武器</option>
              <option v-for="weapon in availableWeapons" :key="weapon.id" :value="weapon.id" :disabled="weapon.unavailable">
                {{ weapon.name }} · {{ weapon.type_label || '普通武器' }}{{ weapon.durability != null ? ` / 耐久 ${weapon.durability}` : '' }}
              </option>
            </select>
            <small v-if="selectedClass && !availableWeapons.length" class="escape-form-error">
              当前职业没有已启用的可选武器，请联系管理员检查武器配置
            </small>
          </label>

          <div class="escape-cost-summary"><span>预计扣除</span><strong>¥{{ money(estimatedCost) }}</strong></div>
          <p class="escape-warning-copy">锁定后不可修改。特殊武器将在战局期间冻结；撤离失败时永久销毁。</p>
          <p v-if="submitError" class="escape-form-error">{{ submitError }}</p>
          <button class="escape-primary wide" type="submit" :disabled="submitting || !canSubmit">
            {{ submitting ? '正在锁定…' : '保存并锁定配装' }}
          </button>
        </template>

        <div v-if="confirmOpen" class="escape-confirm-layer" role="presentation">
          <div class="escape-confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="loadout-confirm-title">
            <span class="escape-confirm-kicker">CONFIRM LOADOUT</span>
            <h3 id="loadout-confirm-title">确认锁定配装？</h3>
            <p>锁定后不能再修改。确定后将保存当前小队、职业和主武器配置。</p>
            <div class="escape-confirm-actions">
              <button type="button" class="escape-confirm-cancel" @click="confirmOpen = false">取消</button>
              <button type="button" class="escape-primary" @click="confirmSubmit">确定锁定</button>
            </div>
          </div>
        </div>
      </form>
    </div>
  </Teleport>
</template>

<script>
import EscapeState from './EscapeState.vue'

export default {
  name: 'LoadoutDialog',
  components: { EscapeState },
  emits: ['close', 'retry', 'submit'],
  props: {
    open: Boolean,
    loading: Boolean,
    submitting: Boolean,
    error: { type: String, default: '' },
    submitError: { type: String, default: '' },
    options: {
      type: Object,
      default: () => ({ squads: [], classes: [], weapons: [] })
    }
  },
  data() {
    return { form: { squad_id: '', class_id: '', weapon_id: '' }, confirmOpen: false }
  },
  computed: {
    selectedClass() {
      return this.options.classes.find(item => item.id === this.form.class_id)
    },
    selectedWeapon() {
      return this.availableWeapons.find(item => String(item.id) === String(this.form.weapon_id))
    },
    availableWeapons() {
      if (!this.selectedClass) return []
      return this.options.weapons.filter(item => this.selectedClass.knife_only
        ? item.weapon_type === 'knife'
        : item.weapon_type !== 'knife')
    },
    estimatedCost() {
      return Number(this.selectedClass?.maintenance_cost || 0) + Number(this.selectedWeapon?.price || 0)
    },
    canSubmit() {
      return this.form.squad_id !== '' && this.form.class_id !== '' && this.form.weapon_id !== ''
    }
  },
  watch: {
    open(value) {
      this.confirmOpen = false
      if (value) this.form = { squad_id: '', class_id: '', weapon_id: '' }
    },
    'form.class_id'() {
      const currentAllowed = this.availableWeapons.some(item => String(item.id) === String(this.form.weapon_id))
      if (currentAllowed) return
      const preferred = this.selectedClass?.knife_only
        ? this.availableWeapons.find(item => item.weapon_type === 'knife')
        : this.availableWeapons.find(item => item.weapon_type === 'regular')
      this.form.weapon_id = preferred?.id || this.availableWeapons[0]?.id || ''
    }
  },
  methods: {
    money(value) {
      return Number(value || 0).toLocaleString('zh-CN')
    },
    submit() {
      if (this.canSubmit && !this.submitting) this.confirmOpen = true
    },
    confirmSubmit() {
      this.confirmOpen = false
      this.$emit('submit', { ...this.form })
    }
  }
}
</script>
