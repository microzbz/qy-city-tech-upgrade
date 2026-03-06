<template>
  <div class="page-card">
    <h3 class="section-title">已审批</h3>
    <el-table :data="rows" border>
      <el-table-column prop="taskId" label="任务ID" width="90"/>
      <el-table-column prop="documentNo" label="单据号" width="150"/>
      <el-table-column prop="enterpriseName" label="企业名称" />
      <el-table-column prop="nodeName" label="节点" width="140"/>
      <el-table-column prop="action" label="动作" width="120"/>
      <el-table-column prop="comment" label="意见" />
      <el-table-column prop="handledAt" label="处理时间" width="180"/>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" link @click="viewSubmission(row.submissionId, row.taskId)">查看</el-button>
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

onMounted(async () => {
  const res = await http.get('/approvals/done')
  rows.value = res.data || []
})

const viewSubmission = (submissionId, taskId) => {
  router.push(`/approvals/submission-view/${submissionId}?taskId=${taskId}`)
}
</script>
