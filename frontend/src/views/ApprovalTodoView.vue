<template>
  <div class="page-card">
    <h3 class="section-title">待审批</h3>
    <el-table :data="rows" border>
      <el-table-column prop="taskId" label="任务ID" width="90"/>
      <el-table-column prop="documentNo" label="单据号" width="150"/>
      <el-table-column prop="enterpriseName" label="企业名称" />
      <el-table-column prop="nodeName" label="节点" width="140"/>
      <el-table-column prop="roleCode" label="审批角色" width="160"/>
      <el-table-column label="操作" width="280">
        <template #default="scope">
          <el-button type="primary" link @click="viewSubmission(scope.row.submissionId, scope.row.taskId)">查看</el-button>
          <el-button type="success" link @click="act(scope.row.taskId, 'approve')">通过</el-button>
          <el-button type="danger" link @click="act(scope.row.taskId, 'reject')">驳回</el-button>
          <el-button type="warning" link @click="act(scope.row.taskId, 'return')">退回</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import http from '../api/http'

const rows = ref([])
const router = useRouter()

const load = async () => {
  const res = await http.get('/approvals/todo')
  rows.value = res.data || []
}

const act = async (taskId, action) => {
  const { value } = await ElMessageBox.prompt('请输入审批意见（可选）', '审批操作', { inputValue: '' })
  await http.post(`/approvals/${taskId}/${action}`, { comment: value })
  await load()
}

const viewSubmission = (submissionId, taskId) => {
  router.push(`/approvals/submission-view/${submissionId}?taskId=${taskId}`)
}

onMounted(load)
</script>
