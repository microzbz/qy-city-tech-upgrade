<template>
  <div class="page-card">
    <div class="toolbar">
      <div>
        <h3 class="section-title">已审批记录</h3>
        <div class="toolbar-hint">展示已通过、已退回、已驳回的填报单，其中已通过、已驳回且匹配编码的记录支持导出。</div>
      </div>
      <div class="toolbar-actions">
        <el-button @click="loadRows">刷新</el-button>
        <el-button :loading="downloadingReport" @click="downloadReport">报表导出</el-button>
        <el-button type="primary" :disabled="!batchExportRows.length || polling" @click="startExport(batchExportRows)">
          批量导出
        </el-button>
      </div>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent>
      <el-form-item label="企业名称">
        <el-input v-model="filters.companyName" clearable placeholder="请输入企业名称" style="width: 220px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 180px">
          <el-option
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="处理时间">
        <el-date-picker
          v-model="filters.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" border @selection-change="onSelectionChange">
      <el-table-column type="selection" width="48" :selectable="isRowSelectable" />
      <el-table-column prop="documentNo" label="单据号" width="150" />
      <el-table-column prop="enterpriseName" label="企业名称" min-width="280" />
      <el-table-column prop="reportYear" label="填报年份" width="100" />
      <el-table-column label="审批结果" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.statusLabel || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reviewComment" label="退回/驳回原因" min-width="220">
        <template #default="{ row }">
          {{ renderReviewComment(row) }}
        </template>
      </el-table-column>
      <el-table-column label="导出状态" width="140">
        <template #default="{ row }">
          <el-tag :type="row.exportable ? 'success' : 'warning'">{{ row.exportHint }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.submittedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="处理时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.reviewHandledAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="viewSubmission(row.submissionId)">查看</el-button>
          <el-button v-if="['APPROVED', 'RETURNED', 'REJECTED'].includes(row.status)" type="warning" link @click="editSubmission(row.submissionId)">编辑</el-button>
          <el-button v-if="row.status === 'APPROVED'" type="danger" link @click="returnSubmission(row.submissionId)">退回企业</el-button>
          <el-button type="success" link :disabled="polling || !row.exportable" @click="startExport([row])">导出</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-pager">
      <el-pagination
        v-model:current-page="pager.page"
        v-model:page-size="pager.size"
        :total="pager.total"
        :page-sizes="pageSizeOptions"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>

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
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { formatDateTime } from '../utils/datetime'

const rows = ref([])
const selectedRows = ref([])
const router = useRouter()
const jobDialogVisible = ref(false)
const job = ref(null)
const polling = ref(false)
const downloadingReport = ref(false)
const filters = ref({
  companyName: '',
  status: '',
  timeRange: []
})
const pageSizeOptions = [10, 20, 50, 100]
const pager = ref({
  page: 1,
  size: 20,
  total: 0
})
const statusOptions = [
  { label: '已通过', value: 'APPROVED' },
  { label: '已退回', value: 'RETURNED' },
  { label: '已驳回', value: 'REJECTED' }
]
let pollTimer = null

const batchExportRows = computed(() => {
  const selected = (selectedRows.value || []).filter((item) => item?.exportable)
  if (selected.length) {
    return selected
  }
  return (rows.value || []).filter((item) => item?.exportable)
})

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
  const res = await http.get('/submission-exports/approved-list', {
    params: {
      companyName: filters.value.companyName || undefined,
      status: filters.value.status || undefined,
      startTime: filters.value.timeRange?.[0] || undefined,
      endTime: filters.value.timeRange?.[1] || undefined,
      page: pager.value.page,
      size: pager.value.size
    }
  })
  const data = res.data || {}
  rows.value = data.records || []
  pager.value.total = data.total || 0
  pager.value.page = data.page || pager.value.page
  pager.value.size = data.size || pager.value.size
  selectedRows.value = []
}

function onSelectionChange(selection) {
  selectedRows.value = selection || []
}

function viewSubmission(submissionId) {
  router.push(`/approvals/submission-view/${submissionId}`)
}

async function editSubmission(submissionId) {
  try {
    await ElMessageBox.confirm('您确定要修改企业填报信息，点击确定将进入编辑页面', '进入编辑', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    router.push(`/approvals/submission-view/${submissionId}?mode=edit`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

async function returnSubmission(submissionId) {
  try {
    const defaultComment = '新型技改城市项目材料需要补充完善'
    const { value } = await ElMessageBox.prompt('请输入退回原因', '退回企业', {
      inputValue: defaultComment,
      inputPlaceholder: '可直接在默认文案后补充说明',
      confirmButtonText: '确认退回',
      cancelButtonText: '取消',
      inputValidator: (val) => {
        const text = (val || '').trim()
        if (!text) return '发送内容不能为空'
        if (text.length > 200) return '发送内容不能超过200个字'
        return true
      }
    })
    await http.post(`/approvals/submissions/${submissionId}/return-approved`, { comment: value.trim() })
    ElMessage.success('已退回企业修改')
    await loadRows()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

async function startExport(targetRows) {
  const submissionIds = (targetRows || []).filter((item) => item.exportable).map((item) => item.submissionId).filter(Boolean)
  if (!submissionIds.length) {
    ElMessage.warning('当前筛选结果中没有可导出的记录')
    return
  }
  const res = await http.post('/submission-exports/jobs', { submissionIds })
  job.value = res.data
  jobDialogVisible.value = true
  startPolling()
}

function search() {
  pager.value.page = 1
  loadRows()
}

function resetFilters() {
  filters.value = {
    companyName: '',
    status: '',
    timeRange: []
  }
  pager.value.page = 1
  loadRows()
}

function onPageChange(page) {
  pager.value.page = page
  loadRows()
}

function onSizeChange(size) {
  pager.value.page = 1
  pager.value.size = size
  loadRows()
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

function statusTagType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'RETURNED') return 'warning'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

function renderReviewComment(row) {
  if (row?.status === 'RETURNED' || row?.status === 'REJECTED') {
    return row?.reviewComment || '-'
  }
  return '-'
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

async function downloadReport() {
  downloadingReport.value = true
  try {
    const token = localStorage.getItem('ctu_token')
    const response = await axios.get('/api/submission-exports/report', {
      responseType: 'blob',
      timeout: 0,
      params: {
        companyName: filters.value.companyName || undefined,
        status: filters.value.status || undefined,
        startTime: filters.value.timeRange?.[0] || undefined,
        endTime: filters.value.timeRange?.[1] || undefined
      },
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = parseFileName(response.headers['content-disposition']) || '审批记录报表.xlsx'
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } finally {
    downloadingReport.value = false
  }
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
