<template>
  <div class="page-card">
    <h3 class="section-title">我的填报</h3>
    <el-table :data="rows" border>
      <el-table-column prop="documentNo" label="单据号" width="170"/>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          {{ row.statusLabel || statusLabel(row.status) }}
        </template>
      </el-table-column>
      <el-table-column prop="currentNodeName" label="当前节点" />
      <el-table-column prop="progressText" label="审批进度" min-width="180"/>
      <el-table-column label="驳回/退回原因" min-width="220">
        <template #default="{ row }">
          {{ reviewReason(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="180"/>
      <el-table-column prop="updatedAt" label="更新时间" width="180"/>
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button type="primary" link @click="open(scope.row.submissionId)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'

const rows = ref([])
const router = useRouter()
const statusMap = {
  DRAFT: '草稿',
  SUBMITTED: '审批中',
  UNDER_REVIEW: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  RETURNED: '已退回'
}

const statusLabel = (status) => statusMap[status] || status || '-'

const reviewReason = (row) => {
  if (!row.reviewActionLabel) {
    return '-'
  }
  return row.reviewActionLabel + (row.reviewComment ? `：${row.reviewComment}` : '')
}

const load = async () => {
  const res = await http.get('/submissions/my-list')
  rows.value = res.data || []
}

const open = (id) => {
  router.push(`/enterprise/submission?id=${id}`)
}

onMounted(load)
</script>
