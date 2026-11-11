<template>
  <section class="card batch-page">
    <div class="page-title-row"><div><h2>跑批管理</h2><p class="muted">配置任务启停、周期和启动时间，查看执行日志。</p></div><el-button @click="load">刷新</el-button></div>
    <el-table :data="tasks" border>
      <el-table-column type="index" label="序号" width="70" />
      <el-table-column prop="task_name" label="跑批名称" min-width="220" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '开启' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="周期" width="120"><template #default="{ row }">{{ cycleLabel(row.cycle) }}</template></el-table-column>
      <el-table-column label="执行规则" width="210"><template #default="{ row }"><span class="batch-time">{{ scheduleLabel(row) }}</span></template></el-table-column>
      <el-table-column label="操作" width="260"><template #default="{ row }"><el-button size="small" @click="edit(row)">编辑</el-button><el-button size="small" @click="showLogs(row)">跑批日志</el-button><el-button size="small" type="primary" :disabled="!row.enabled" @click="run(row)">手动执行</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="editVisible" title="编辑跑批任务" width="500px">
      <el-form v-if="form" label-width="100px">
        <el-form-item label="是否开启"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item v-if="form.enabled" label="跑批周期"><el-select v-model="form.cycle" style="width: 100%"><el-option label="每天" value="daily"/><el-option label="每周" value="weekly"/><el-option label="每月" value="monthly"/><el-option label="每年" value="yearly"/></el-select></el-form-item>
        <el-form-item v-if="form.enabled && form.cycle === 'weekly'" label="执行星期"><el-select v-model="form.schedule_weekday" style="width: 100%"><el-option v-for="item in weekdayOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item v-if="form.enabled && form.cycle === 'monthly'" label="执行日期"><div class="batch-date-control"><el-input-number v-model="form.schedule_day" :min="1" :max="31" controls-position="right" /><span>日</span></div></el-form-item>
        <el-form-item v-if="form.enabled && form.cycle === 'yearly'" label="执行日期"><div class="batch-date-control"><el-select v-model="form.schedule_month"><el-option v-for="month in 12" :key="month" :label="`${month} 月`" :value="month" /></el-select><el-select v-model="form.schedule_day"><el-option v-for="day in 31" :key="day" :label="`${day} 日`" :value="day" /></el-select></div></el-form-item>
        <el-form-item v-if="form.enabled" label="启动时间"><el-time-picker v-model="form.schedule_time" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="选择时分秒" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="logsVisible" title="跑批日志" width="1000px"><el-table :data="logs" border><el-table-column label="开始时间" width="190"><template #default="{ row }"><span class="batch-time">{{ formatDateTime(row.started_at) }}</span></template></el-table-column><el-table-column label="结束时间" width="190"><template #default="{ row }"><span class="batch-time">{{ formatDateTime(row.finished_at) }}</span></template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column><el-table-column prop="message" label="日志" min-width="300" show-overflow-tooltip/></el-table></el-dialog>
  </section>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from './api'
const tasks = ref([]); const logs = ref([]); const form = ref(null); const editVisible = ref(false); const logsVisible = ref(false)
const weekdayOptions = [
  { value: 1, label: '星期一' }, { value: 2, label: '星期二' }, { value: 3, label: '星期三' },
  { value: 4, label: '星期四' }, { value: 5, label: '星期五' }, { value: 6, label: '星期六' },
  { value: 7, label: '星期日' }
]
const cycleLabel = value => ({ daily: '每天', weekly: '每周', monthly: '每月', yearly: '每年' }[value] || value || '-')
const statusLabel = value => ({ success: '成功', failed: '失败', running: '执行中' }[value] || value || '-')
const statusType = value => ({ success: 'success', failed: 'danger', running: 'warning' }[value] || 'info')
const formatDateTime = value => {
  if (!value) return '-'
  const match = String(value).trim().match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/)
  return match ? `${match[1]} ${match[2]}` : String(value)
}
const scheduleParts = value => {
  const match = formatDateTime(value).match(/^(\d{4})-(\d{2})-(\d{2}) (\d{2}:\d{2}:\d{2})$/)
  if (!match) return { year: 2026, month: 1, day: 1, weekday: 4, time: '00:00:00' }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]), weekday: date.getDay() || 7, time: match[4] }
}
const scheduleLabel = row => {
  const parts = scheduleParts(row.start_at)
  if (row.cycle === 'weekly') return `每周${weekdayOptions.find(item => item.value === parts.weekday)?.label.replace('星期', '') || '-'} ${parts.time}`
  if (row.cycle === 'monthly') return `每月 ${parts.day} 日 ${parts.time}`
  if (row.cycle === 'yearly') return `每年 ${parts.month} 月 ${parts.day} 日 ${parts.time}`
  return `每天 ${parts.time}`
}
async function load() { tasks.value = await api('/api/escape/admin/batches') }
function edit(row) {
  const parts = scheduleParts(row.start_at)
  form.value = { ...row, enabled: !!row.enabled, schedule_time: parts.time, schedule_weekday: parts.weekday, schedule_month: parts.month, schedule_day: parts.day }
  editVisible.value = true
}
function buildStartAt(value) {
  const parts = scheduleParts(value.start_at)
  const date = new Date(parts.year, parts.month - 1, parts.day)
  if (value.cycle === 'weekly') date.setDate(date.getDate() + Number(value.schedule_weekday) - (date.getDay() || 7))
  if (value.cycle === 'monthly') date.setDate(Math.min(Number(value.schedule_day), new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()))
  if (value.cycle === 'yearly') {
    date.setDate(1)
    date.setMonth(Number(value.schedule_month) - 1)
    date.setDate(Math.min(Number(value.schedule_day), new Date(date.getFullYear(), Number(value.schedule_month), 0).getDate()))
  }
  const dateText = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return `${dateText} ${value.schedule_time}`
}
async function save() {
  if (form.value.enabled && !form.value.schedule_time) return ElMessage.warning('请选择启动时间')
  const payload = { ...form.value, start_at: buildStartAt(form.value) }
  await api(`/api/escape/admin/batches/${form.value.task_key}`, { method: 'PUT', body: payload })
  editVisible.value = false
  ElMessage.success('已保存')
  await load()
}
async function showLogs(row) { logs.value = await api(`/api/escape/admin/batches/${row.task_key}/logs`); logsVisible.value = true }
async function run(row) { await ElMessageBox.confirm(`确认执行“${row.task_name}”？`, '手动执行'); await api(`/api/escape/admin/batches/${row.task_key}/run`, { method: 'POST', body: {} }); ElMessage.success('执行完成'); await showLogs(row) }
onMounted(load)
</script>
<style scoped>
.batch-time {
  white-space: nowrap;
}

.batch-date-control {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}

.batch-date-control .el-select,
.batch-date-control .el-input-number {
  flex: 1;
  width: 0;
}
</style>
