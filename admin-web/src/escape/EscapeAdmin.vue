<template>
  <section class="escape-admin">
    <header class="escape-heading">
      <div>
        <span class="escape-kicker">ESCAPE FROM XP</span>
        <h1>逃离西撇镇</h1>
        <p>赛季、物品、商店与用户资产统一运营</p>
      </div>
    </header>

    <nav class="escape-tabs" aria-label="逃离西撇镇后台导航">
      <button
        v-for="tab in visibleTabs"
        :key="tab.key"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div v-if="loading" class="escape-state">
      <el-skeleton :rows="7" animated />
    </div>
    <el-result
      v-else-if="error"
      icon="error"
      title="数据加载失败"
      :sub-title="error"
      class="escape-state"
    >
      <template #extra><el-button type="primary" @click="load">重试</el-button></template>
    </el-result>

    <template v-else>
      <div v-if="activeTab === 'overview'" class="escape-overview">
        <div class="escape-metrics">
          <article v-for="metric in metrics" :key="metric.label">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.hint }}</small>
          </article>
        </div>
        <div class="escape-overview-grid">
          <article class="escape-panel">
            <div class="escape-panel-title"><div><span>ECONOMY</span><h2>经济概况</h2></div></div>
            <dl class="escape-economy">
              <div><dt>今日产出</dt><dd>{{ money(overview.economy?.income_today) }}</dd></div>
              <div><dt>今日消耗</dt><dd>{{ money(overview.economy?.expense_today) }}</dd></div>
              <div><dt>净流入</dt><dd :class="{ danger: Number(overview.economy?.net_today) < 0 }">{{ money(overview.economy?.net_today) }}</dd></div>
            </dl>
          </article>
        </div>
      </div>

      <div v-else class="escape-list-page">
        <div class="escape-filterbar">
          <el-input v-model="filters.keyword" clearable placeholder="输入名称、编号或用户呼号" style="width: 260px" @keyup.enter="load" />
          <el-select v-if="activeTab === 'items'" v-model="filters.rarity" clearable placeholder="全部稀有度" style="width: 140px">
            <el-option v-for="rarity in rarities" :key="rarity" :label="rarity" :value="rarity" />
          </el-select>
          <el-button @click="load">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <span class="escape-filter-spacer" />
          <el-button
            v-if="resourceConfig?.createPermission && allowed(resourceConfig.createPermission)"
            type="primary"
            @click="openEditor(activeTab)"
          >
            {{ activeTab === 'grants' ? '物品入库' : `新增${resourceConfig.singular}` }}
          </el-button>
        </div>

        <div class="escape-panel">
          <el-table v-if="rows.length" :data="rows" border row-key="id">
            <el-table-column v-for="column in resourceConfig.columns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.width">
              <template #default="{ row }">
                <el-tag v-if="column.kind === 'status'" :type="statusType(row[column.prop])">{{ statusLabel(row[column.prop]) }}</el-tag>
                <el-tag v-else-if="column.kind === 'rarity'" :class="`escape-rarity-${row[column.prop]}`">{{ row[column.prop] || '-' }}</el-tag>
                <span v-else-if="column.kind === 'money'">{{ money(row[column.prop]) }}</span>
                <span v-else-if="column.kind === 'date'">{{ formatDate(row[column.prop]) }}</span>
                <span v-else-if="column.kind === 'boolean'">{{ row[column.prop] ? '启用' : '停用' }}</span>
                <span v-else>{{ displayValue(row, column) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!['grants', 'audit'].includes(activeTab)" label="操作" fixed="right" width="180">
              <template #default="{ row }">
                <el-button v-if="resourceConfig.updatePermission && allowed(resourceConfig.updatePermission)" link type="primary" @click="openEditor(activeTab, row)">编辑</el-button>
                <el-button
                  v-if="resourceConfig.deletePermission && allowed(resourceConfig.deletePermission)"
                  link
                  type="danger"
                  @click="removeRow(row)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else :description="`暂无${resourceConfig.singular}数据`">
            <el-button v-if="resourceConfig?.createPermission && allowed(resourceConfig.createPermission)" type="primary" @click="openEditor(activeTab)">
              新增{{ resourceConfig.singular }}
            </el-button>
          </el-empty>
        </div>
      </div>
    </template>

    <el-dialog v-model="editorVisible" :title="`${editing?.id ? '编辑' : '新增'}${resourceConfig?.singular || ''}`" width="620px" destroy-on-close>
      <el-form v-if="editing" label-width="120px">
        <template v-for="field in resourceConfig.fields" :key="field.prop">
          <el-form-item :label="field.label" :required="field.required">
            <el-input v-if="field.type === 'text'" v-model="editing[field.prop]" :placeholder="field.placeholder" />
            <el-input v-else-if="field.type === 'textarea'" v-model="editing[field.prop]" type="textarea" :rows="3" />
            <el-input-number v-else-if="field.type === 'number'" v-model="editing[field.prop]" :min="field.min ?? 0" :precision="field.precision" style="width: 100%" />
            <el-date-picker v-else-if="field.type === 'datetime'" v-model="editing[field.prop]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            <div v-else-if="field.type === 'image'" class="escape-item-image-upload">
              <div class="escape-item-image-actions">
                <el-upload
                  action="/api/admin/files/upload"
                  accept="image/*"
                  :http-request="options => uploadItemImage(options, field.prop)"
                  :show-file-list="false"
                  :on-error="handleImageUploadError"
                ><el-button :loading="imageUploading">{{ editing[field.prop] ? '重新上传' : '选择图片' }}</el-button></el-upload>
                <el-button v-if="editing[field.prop]" @click="editing[field.prop] = ''">清除</el-button>
              </div>
              <span class="escape-item-image-hint">图片会自动压缩到 100KB 以内</span>
              <img v-if="editing[field.prop]" :src="editing[field.prop]" alt="物品图片预览" />
            </div>
            <el-select v-else-if="field.type === 'select'" v-model="editing[field.prop]" filterable style="width: 100%">
              <el-option v-for="option in field.options || []" :key="option.value ?? option" :label="option.label ?? option" :value="option.value ?? option" />
            </el-select>
            <el-switch v-else-if="field.type === 'boolean'" v-model="editing[field.prop]" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEditor">保存</el-button>
      </template>
    </el-dialog>

  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { compressImageFile } from '../imageCompression'
import { escapeAction, escapeCreate, escapeGet, escapeRemove, escapeUpdate, rowsOf } from './api'
import './styles.css'

const props = defineProps({
  can: { type: Function, default: () => true }
})

const rarities = ['超凡', '史诗', '精品', '普通']
const weaponTypes = [
  { label: '近战武器', value: 'KNIFE' },
  { label: '普通武器', value: 'REGULAR' },
  { label: '特殊武器', value: 'SPECIAL' }
]
const ITEM_IMAGE_MAX_BYTES = 100 * 1024

const configs = {
  items: {
    label: '物品配置', singular: '物品', path: '/items',
    viewPermission: 'escape:item:view', createPermission: 'escape:item:create', updatePermission: 'escape:item:update', deletePermission: 'escape:item:delete',
    columns: [
      { prop: 'name', label: '物品名称', width: 160 },
      { prop: 'rarity', label: '稀有度', width: 100, kind: 'rarity' },
      { prop: 'category', label: '分类', width: 110 },
      { prop: 'size', label: '仓库尺寸', width: 100, derive: row => `${row.width || 1} × ${row.height || 1}` },
      { prop: 'stock_quantity', label: '数量', width: 90 },
      { prop: 'today_price', label: '今日价格', width: 110, kind: 'money' },
      { prop: 'enabled', label: '状态', width: 80, kind: 'boolean' }
    ],
    fields: [
      { prop: 'name', label: '物品名称', type: 'text', required: true },
      { prop: 'rarity', label: '稀有度', type: 'select', options: rarities, required: true },
      { prop: 'category', label: '分类', type: 'text', required: true },
      { prop: 'min_price', label: '最低价格', type: 'number', required: true },
      { prop: 'max_price', label: '最高价格', type: 'number', required: true },
      { prop: 'width', label: '宽度格数', type: 'number', min: 1, required: true },
      { prop: 'height', label: '高度格数', type: 'number', min: 1, required: true },
      { prop: 'stock_quantity', label: '数量', type: 'number', min: 0, required: true },
      { prop: 'image_url', label: '物品图片', type: 'image' },
      { prop: 'enabled', label: '启用', type: 'boolean' }
    ]
  },
  products: {
    label: '商店商品', singular: '商品', path: '/shop-products',
    viewPermission: 'escape:shop:view', createPermission: 'escape:shop:create', updatePermission: 'escape:shop:update', deletePermission: 'escape:shop:delete',
    columns: [
      { prop: 'name', label: '商品名称', width: 180 },
      { prop: 'product_type', label: '类型', width: 110 },
      { prop: 'price', label: '售价', width: 110, kind: 'money' },
      { prop: 'stock', label: '库存', width: 90 },
      { prop: 'offline_at', label: '下架时间', width: 160, kind: 'date' },
      { prop: 'enabled', label: '状态', width: 80, kind: 'boolean' }
    ],
    fields: [
      { prop: 'name', label: '商品名称', type: 'text', required: true },
      { prop: 'product_type', label: '商品类型', type: 'select', options: ['EXPANSION', 'ITEM', 'WEAPON'], required: true },
      { prop: 'item_id', label: '关联物品 ID', type: 'number' },
      { prop: 'price', label: '售价', type: 'number', required: true },
      { prop: 'stock', label: '库存', type: 'number', required: true },
      { prop: 'offline_at', label: '下架时间', type: 'datetime' },
      { prop: 'enabled', label: '上架', type: 'boolean' }
    ]
  },
  seasons: {
    label: '赛季管理', singular: '赛季', path: '/seasons',
    viewPermission: 'escape:season:view', createPermission: 'escape:season:create', updatePermission: 'escape:season:update', deletePermission: 'escape:season:delete',
    columns: [
      { prop: 'name', label: '赛季名称', width: 180 },
      { prop: 'start_at', label: '开始时间', width: 160, kind: 'date' },
      { prop: 'end_at', label: '结束时间', width: 160, kind: 'date' },
      { prop: 'kill_reward', label: '每击杀奖励', width: 120, kind: 'money' },
      { prop: 'enabled', label: '状态', width: 90, kind: 'boolean' }
    ],
    fields: [
      { prop: 'name', label: '赛季名称', type: 'text', required: true },
      { prop: 'start_at', label: '开始时间', type: 'datetime', required: true },
      { prop: 'end_at', label: '结束时间', type: 'datetime', required: true },
      { prop: 'kill_reward', label: '每击杀奖励', type: 'number', required: true },
      { prop: 'enabled', label: '启用', type: 'boolean' }
    ]
  },
  classes: {
    label: '职业配置', singular: '职业', path: '/classes',
    viewPermission: 'escape:class:view', createPermission: 'escape:class:create', updatePermission: 'escape:class:update', deletePermission: 'escape:class:delete',
    columns: [
      { prop: 'name', label: '职业名称', width: 160 },
      { prop: 'health', label: '生命值', width: 100 },
      { prop: 'maintenance_cost', label: '维护费', width: 110, kind: 'money' },
      { prop: 'enabled', label: '状态', width: 80, kind: 'boolean' }
    ],
    fields: [
      { prop: 'name', label: '职业名称', type: 'text', required: true },
      { prop: 'health', label: '生命值', type: 'number', min: 1, required: true },
      { prop: 'maintenance_cost', label: '维护费', type: 'number', required: true },
      { prop: 'enabled', label: '启用', type: 'boolean' }
    ]
  },
  weapons: {
    label: '武器配置', singular: '武器', path: '/weapons',
    viewPermission: 'escape:weapon:view', createPermission: 'escape:weapon:create', updatePermission: 'escape:weapon:update', deletePermission: 'escape:weapon:delete',
    columns: [
      { prop: 'name', label: '武器名称', width: 160 },
      { prop: 'weapon_type', label: '类型', width: 110 },
      { prop: 'cost', label: '使用费', width: 100, kind: 'money' },
      { prop: 'max_durability', label: '最大耐久', width: 100 },
      { prop: 'enabled', label: '状态', width: 80, kind: 'boolean' }
    ],
    fields: [
      { prop: 'name', label: '武器名称', type: 'text', required: true },
      { prop: 'weapon_type', label: '武器类型', type: 'select', options: weaponTypes, required: true },
      { prop: 'cost', label: '使用费用', type: 'number' },
      { prop: 'max_durability', label: '最大耐久', type: 'number', min: 1 },
      { prop: 'enabled', label: '启用', type: 'boolean' }
    ]
  },
  assets: {
    label: '用户资产', singular: '用户资产', path: '/user-assets',
    viewPermission: 'escape:userAsset:view', updatePermission: 'escape:userAsset:adjust',
    columns: [
      { prop: 'callsign', label: '用户呼号', width: 150 },
      { prop: 'mobile', label: '手机号', width: 130 },
      { prop: 'cash', label: '现金余额', width: 120, kind: 'money' },
      { prop: 'personal_usage', label: '个人仓库', width: 100 },
      { prop: 'buffer_usage', label: '暂存仓库', width: 100 },
      { prop: 'extraction_rate', label: '赛季撤离率', width: 110 }
    ],
    fields: [
      { prop: 'cash_delta', label: '现金调整', type: 'number', min: -99999999, required: true },
      { prop: 'reason', label: '调整原因', type: 'textarea', required: true }
    ]
  },
  grants: {
    label: '物品入库', singular: '入库记录', path: '/item-grants',
    viewPermission: 'escape:itemGrant:create', createPermission: 'escape:itemGrant:create',
    columns: [
      { prop: 'created_at', label: '入库时间', width: 160, kind: 'date' },
      { prop: 'callsign', label: '用户呼号', width: 140 },
      { prop: 'item_name', label: '物品', width: 160 },
      { prop: 'quantity', label: '数量', width: 80 },
      { prop: 'reason', label: '原因', width: 220 },
      { prop: 'operator_name', label: '操作人', width: 140 }
    ],
    fields: [
      { prop: 'user_id', label: '用户 ID', type: 'number', min: 1, required: true },
      { prop: 'item_id', label: '物品 ID', type: 'number', min: 1, required: true },
      { prop: 'quantity', label: '数量', type: 'number', min: 1, required: true },
      { prop: 'reason', label: '入库原因', type: 'textarea', required: true }
    ]
  },
  audit: {
    label: '操作审计', singular: '审计记录', path: '/audit',
    viewPermission: 'escape:audit',
    columns: [
      { prop: 'created_at', label: '操作时间', width: 160, kind: 'date' },
      { prop: 'actor_name', label: '操作人', width: 140 },
      { prop: 'permission_code', label: '权限', width: 180 },
      { prop: 'action', label: '动作', width: 120 },
      { prop: 'entity_type', label: '对象类型', width: 120 },
      { prop: 'entity_id', label: '对象 ID', width: 120 },
      { prop: 'request_snapshot', label: '请求快照', width: 300 }
    ],
    fields: []
  }
}

const tabs = [
  { key: 'overview', label: '运营概览', permission: 'escape:view' },
  ...Object.entries(configs).map(([key, config]) => ({ key, label: config.label, permission: config.viewPermission }))
]

const activeTab = ref('overview')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const rows = ref([])
const overview = ref({})
const filters = reactive({ keyword: '', status: '', rarity: '' })
const editorVisible = ref(false)
const editing = ref(null)
const imageUploading = ref(false)

const allowed = permission => !permission || props.can(permission)
const visibleTabs = computed(() => tabs.filter(tab => allowed(tab.permission)))
const resourceConfig = computed(() => configs[activeTab.value])
const metrics = computed(() => {
  const cards = overview.value.cards || overview.value
  return [
    { label: '本赛季参战', value: cards.season_participations || 0, hint: '累计参战人次' },
    { label: '当前流通现金', value: money(cards.cash_in_circulation), hint: `${cards.player_count || 0} 名玩家` },
    { label: '暂存区物品', value: cards.buffer_item_count || 0, hint: '每日 05:00 自动出售' },
    { label: '平均撤离率', value: `${cards.extraction_rate || 0}%`, hint: '当前赛季' }
  ]
})

onMounted(() => {
  if (!visibleTabs.value.some(tab => tab.key === activeTab.value)) activeTab.value = visibleTabs.value[0]?.key || 'overview'
  load()
})

async function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  resetFilters(false)
  await load()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (activeTab.value === 'overview') {
      overview.value = await escapeGet('/overview')
    } else {
      const result = await escapeGet(resourceConfig.value.path, filters)
      rows.value = rowsOf(result)
    }
  } catch (loadError) {
    error.value = loadError?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

function resetFilters(reload = true) {
  filters.keyword = ''
  filters.status = ''
  filters.rarity = ''
  if (reload) load()
}

function openEditor(type, row = null) {
  const config = configs[type]
  if (!config) return
  editing.value = row ? { ...row } : defaultValues(config)
  editorVisible.value = true
}

function defaultValues(config) {
  const result = {}
  config.fields.forEach(field => {
    if (field.type === 'boolean') result[field.prop] = true
    else if (field.type === 'number') result[field.prop] = field.min > 0 ? field.min : 0
    else result[field.prop] = ''
  })
  return result
}

async function saveEditor() {
  const config = resourceConfig.value
  const missing = config.fields.find(field => field.required && (editing.value[field.prop] === '' || editing.value[field.prop] === null || editing.value[field.prop] === undefined))
  if (missing) {
    ElMessage.warning(`请填写${missing.label}`)
    return
  }
  saving.value = true
  try {
    if (activeTab.value === 'assets') {
      const before = Number(editing.value.cash ?? editing.value.cash_balance ?? 0)
      const after = before + Number(editing.value.cash_delta || 0)
      await confirmDanger(`确认调整 ${editing.value.callsign || '该用户'} 的资产？余额将从 ${money(before)} 变为 ${money(after)}。原因：${editing.value.reason}`, '确认资产调整')
      await escapeAction(config.path, editing.value.id, 'adjust', {
        cash_delta: editing.value.cash_delta,
        reason: editing.value.reason
      })
    } else if (editing.value.id) {
      await escapeUpdate(config.path, editing.value.id, editing.value)
    } else {
      await escapeCreate(config.path, editing.value)
    }
    editorVisible.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (saveError) {
    if (saveError !== 'cancel' && saveError !== 'close') ElMessage.error(saveError?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function uploadItemImage(options, field) {
  imageUploading.value = true
  try {
    const compressed = await compressImageFile(options.file, ITEM_IMAGE_MAX_BYTES)
    const body = new FormData()
    body.append(options.filename || 'file', compressed)
    const data = await api('/api/admin/files/upload', { method: 'POST', body })
    editing.value[field] = data.url
    ElMessage.success(`图片已上传（${Math.ceil(compressed.size / 1024)}KB）`)
    return { data }
  } finally {
    imageUploading.value = false
  }
}

function handleImageUploadError(error) {
  ElMessage.error(error?.message || '图片上传失败')
}

async function removeRow(row) {
  try {
    await confirmDanger(`确认停用“${row.name || row.callsign || row.id}”？历史记录与结算快照会继续保留。`, '停用确认')
    await escapeRemove(resourceConfig.value.path, row.id)
    ElMessage.success('已停用')
    await load()
  } catch (removeError) {
    if (removeError !== 'cancel' && removeError !== 'close') ElMessage.error(removeError?.message || '删除失败')
  }
}

function confirmDanger(message, title) {
  return ElMessageBox.confirm(message, title, {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  })
}

function displayValue(row, column) {
  if (column.derive) return column.derive(row)
  const value = row[column.prop]
  return value === '' || value === null || value === undefined ? '-' : value
}

function statusLabel(status) {
  return {
    PREPARING: '整备中', preparing: '整备中',
    IN_PROGRESS: '进行中', in_progress: '进行中',
    FINISHED: '已结束', finished: '已结束',
    SETTLED: '已结算', settled: '已结算'
  }[status] || status || '-'
}

function statusType(status) {
  if (['IN_PROGRESS', 'in_progress'].includes(status)) return 'success'
  if (['FINISHED', 'finished', 'SETTLED', 'settled'].includes(status)) return 'info'
  return 'warning'
}

function money(value) {
  const number = Number(value || 0)
  return `¥${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(number)}`
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(date)
}
</script>
