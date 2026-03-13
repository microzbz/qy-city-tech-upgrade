<template>
  <div class="page-card">
    <div class="toolbar">
      <div>
        <h3 class="section-title">已审批通过</h3>
        <div class="toolbar-hint">仅展示已审批通过的填报单，可单条或批量导出。</div>
      </div>
      <div class="toolbar-actions">
        <el-button @click="loadRows">刷新</el-button>
        <el-button type="primary" :disabled="!selectedRows.length || polling" @click="startExport(selectedRows)">
          批量导出
        </el-button>
      </div>
    </div>

    <el-table :data="rows" border @selection-change="onSelectionChange">
      <el-table-column type="selection" width="48" :selectable="isRowSelectable" />
      <el-table-column prop="documentNo" label="单据号" width="150" />
      <el-table-column prop="enterpriseName" label="企业名称" min-width="280" />
      <el-table-column prop="reportYear" label="填报年份" width="100" />
      <el-table-column label="导出状态" width="140">
        <template #default="{ row }">
          <el-tag :type="row.exportable ? 'success' : 'warning'">{{ row.exportHint }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="180" />
      <el-table-column prop="approvedAt" label="审批通过时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="viewSubmission(row.submissionId)">查看</el-button>
          <el-button type="success" link :disabled="polling || !row.exportable" @click="startExport([row])">导出</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="jobDialogVisible"
      title="导出任务"
      width="720px"
      :close-on-click-modal="false"
      :close-on-press-escape="!polling"
      :show-close="!polling"
    >
      <div v-if="job" class="job-panel">
        <div class="job-summary">
          <div>状态：{{ statusLabel(job.status) }}</div>
          <div>进度：{{ job.completedCount || 0 }}/{{ job.totalCount || 0 }}</div>
          <div v-if="job.currentEnterpriseName">当前企业：{{ job.currentEnterpriseName }}</div>
          <div>{{ job.message || '-' }}</div>
        </div>

        <el-progress :percentage="progressPercent" :status="progressStatus" />

        <el-table :data="job.items || []" border max-height="320">
          <el-table-column prop="submissionId" label="单据ID" width="100" />
          <el-table-column prop="enterpriseName" label="企业名称" min-width="220" />
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="220" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="closeJobDialog" :disabled="polling">关闭</el-button>
        <el-button type="primary" :disabled="!job?.downloadReady" @click="downloadJobResult">下载压缩包</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import http from '../api/http'

const rows = ref([])
const selectedRows = ref([])
const router = useRouter()
const jobDialogVisible = ref(false)
const job = ref(null)
const polling = ref(false)
let pollTimer = null

const progressPercent = computed(() => {
  const total = job.value?.totalCount || 0
  if (!total) return 0
  return Math.min(100, Math.round(((job.value?.completedCount || 0) / total) * 100))
})

const progressStatus = computed(() => {
  if (!job.value) return undefined
  if (job.value.status === 'FAILED') return 'exception'
  if (job.value.status === 'COMPLETED' && job.value.failedCount > 0) return 'warning'
  if (job.value.status === 'COMPLETED') return 'success'
  return undefined
})

onMounted(loadRows)
onBeforeUnmount(stopPolling)

async function loadRows() {
  const res = await http.get('/submission-exports/approved-list')
  rows.value = res.data || []
}

function onSelectionChange(selection) {
  selectedRows.value = selection || []
}

function viewSubmission(submissionId) {
  router.push(`/approvals/submission-view/${submissionId}`)
}

async function startExport(targetRows) {
  const submissionIds = (targetRows || []).filter((item) => item.exportable).map((item) => item.submissionId).filter(Boolean)
  if (!submissionIds.length) {
    ElMessage.warning('请选择可导出的记录')
    return
  }
  const res = await http.post('/submission-exports/jobs', { submissionIds })
  job.value = res.data
  jobDialogVisible.value = true
  startPolling()
}

function startPolling() {
  stopPolling()
  polling.value = true
  pollTimer = window.setInterval(fetchJob, 2000)
  fetchJob()
}

function stopPolling() {
  polling.value = false
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function fetchJob() {
  if (!job.value?.jobId) {
    stopPolling()
    return
  }
  try {
    const res = await http.get(`/submission-exports/jobs/${job.value.jobId}`)
    job.value = res.data
    if (job.value.status === 'COMPLETED' || job.value.status === 'FAILED') {
      stopPolling()
      await loadRows()
    }
  } catch (error) {
    stopPolling()
  }
}

function closeJobDialog() {
  if (polling.value) return
  jobDialogVisible.value = false
}

function statusLabel(status) {
  if (status === 'QUEUED') return '排队中'
  if (status === 'RUNNING') return '处理中'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'FAILED') return '失败'
  return status || '-'
}

function isRowSelectable(row) {
  return !!row?.exportable && !polling.value
}

async function downloadJobResult() {
  if (!job.value?.jobId || !job.value?.downloadReady) {
    return
  }
  const token = localStorage.getItem('ctu_token')
  const response = await axios.get(`/api/submission-exports/jobs/${job.value.jobId}/download`, {
    responseType: 'blob',
    timeout: 0,
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })

  const blob = new Blob([response.data], { type: 'application/zip' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = parseFileName(response.headers['content-disposition']) || job.value.fileName || '导出结果.zip'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

function parseFileName(disposition) {
  if (!disposition) return ''
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const match = disposition.match(/filename=\"?([^\";]+)\"?/i)
  return match?.[1] || ''
}
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.toolbar-hint {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
}

.toolbar-actions {
  display: flex;
  gap: 12px;
}

.job-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.job-summary {
  display: grid;
  gap: 6px;
  color: #303133;
}
</style>
