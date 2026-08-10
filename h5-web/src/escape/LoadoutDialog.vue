<template>
  <Teleport to="body">
    <div v-if="open" class="escape-modal" role="dialog" aria-modal="true" aria-labelledby="escape-loadout-title">
      <button class="escape-modal-backdrop" aria-label="关闭配装" @click="$emit('close')"></button>
      <form class="escape-loadout-dialog" @submit.prevent="submit">
        <header>
          <div><span>LOADOUT</span><h2 id="escape-loadout-title">战前配装</h2></div>
          <button type="button" class="escape-icon-button" aria-label="关闭" @click="$emit('close')">×</button>
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
              <option v-for="weapon in options.weapons" :key="weapon.id" :value="weapon.id" :disabled="weapon.unavailable">
                {{ weapon.name }} · {{ weapon.type_label || '普通武器' }}{{ weapon.durability != null ? ` / 耐久 ${weapon.durability}` : '' }}
              </option>
            </select>
          </label>

          <div class="escape-cost-summary"><span>预计扣除</span><strong>¥{{ money(estimatedCost) }}</strong></div>
          <p class="escape-warning-copy">锁定后不可修改。特殊武器将在战局期间冻结；撤离失败时永久销毁。</p>
          <p v-if="submitError" class="escape-form-error">{{ submitError }}</p>
          <button class="escape-primary wide" type="submit" :disabled="submitting || !canSubmit">
            {{ submitting ? '正在锁定…' : '保存并锁定配装' }}
          </button>
        </template>
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
    return { form: { squad_id: '', class_id: '', weapon_id: '' } }
  },
  computed: {
    selectedClass() {
      return this.options.classes.find(item => item.id === this.form.class_id)
    },
    selectedWeapon() {
      return this.options.weapons.find(item => item.id === this.form.weapon_id)
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
      if (value) this.form = { squad_id: '', class_id: '', weapon_id: '' }
    }
  },
  methods: {
    money(value) {
      return Number(value || 0).toLocaleString('zh-CN')
    },
    submit() {
      if (this.canSubmit) this.$emit('submit', { ...this.form })
    }
  }
}
</script>
