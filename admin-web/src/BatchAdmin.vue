<template>
  <section class="card batch-page">
    <div class="page-title-row"><div><h2>跑批管理</h2><p class="muted">查看定时任务执行记录，也可以手动执行指定日期的任务。</p></div><el-button @click="load">刷新</el-button></div>
    <div class="batch-toolbar"><el-select v-model="taskKey" style="width: 240px"><el-option v-for="task in taskOptions" :key="task.key" :label="task.name" :value="task.key" /></el-select><el-date-picker v-model="businessDate" value-format="YYYY-MM-DD" type="date" /><el-button type="primary" @click="run">手动执行</el-button></div>
    <el-table :data="rows" border><el-table-column prop="task_name" label="任务" min-width="180" /><el-table-column prop="business_date" label="业务日期" width="130" /><el-table-column prop="trigger_type" label="触发方式" width="110"><template #default="{ row }">{{ row.trigger_type === 'manual' ? '手动' : '定时' }}</template></el-table-column><el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'success' ? 'success' : row.status === 'failed' ? 'danger' : 'warning'">{{ row.status === 'success' ? '成功' : row.status === 'failed' ? '失败' : '执行中' }}</el-tag></template></el-table-column><el-table-column prop="started_at" label="开始时间" width="180" /><el-table-column prop="finished_at" label="结束时间" width="180" /><el-table-column prop="message" label="日志" min-width="240" show-overflow-tooltip /></el-table>
  </section>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from './api'
const rows = ref([])
const taskKey = ref('shop-stock-plan')
const businessDate = ref(new Date().toISOString().slice(0, 10))
const taskOptions = [{ key: 'price-refresh', name: '每日商品价格刷新' }, { key: 'shop-stock-plan', name: '商店库存每日计划' }, { key: 'shop-stock-replenish', name: '商店随机补货' }, { key: 'buffer-liquidation', name: '缓冲区自动出售' }]
async function load() { rows.value = await api('/api/escape/admin/batches') }
async function run() { await ElMessageBox.confirm(`确认执行“${taskOptions.find(t => t.key === taskKey.value)?.name}”（${businessDate.value}）？`, '手动执行'); await api(`/api/escape/admin/batches/${taskKey.value}/run?business_date=${businessDate.value}`, { method: 'POST', body: {} }); ElMessage.success('执行完成'); await load() }
onMounted(load)
</script>
