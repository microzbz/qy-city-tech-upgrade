<template>
  <div class="page-card">
    <h3 class="section-title">待审批</h3>
    <el-form :inline="true" class="filter-form" @submit.prevent>
      <el-form-item label="单据号">
        <el-input v-model="query.documentNo" clearable placeholder="请输入单据号" style="width: 180px" />
      </el-form-item>
      <el-form-item label="企业名称">
        <el-input v-model="query.enterpriseName" clearable placeholder="请输入企业名称" style="width: 220px" />
      </el-form-item>
      <el-form-item label="提交时间">
        <el-date-picker
          v-model="query.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="rows" border>
      <el-table-column prop="taskId" label="任务ID" width="90"/>
      <el-table-column prop="documentNo" label="单据号" width="150"/>
      <el-table-column prop="enterpriseName" label="企业名称" />
      <el-table-column label="提交时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.submittedAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="nodeName" label="节点" width="140"/>
      <el-table-column prop="roleCode" label="审批角色" width="160"/>
      <el-table-column label="操作" width="340">
        <template #default="scope">
          <el-button type="primary" link @click="viewSubmission(scope.row.submissionId, scope.row.taskId)">查看</el-button>
          <el-button type="warning" link @click="editSubmission(scope.row.submissionId, scope.row.taskId)">编辑</el-button>
          <el-button type="success" link @click="act(scope.row.taskId, 'approve')">通过</el-button>
          <el-button type="danger" link @click="act(scope.row.taskId, 'reject')">驳回</el-button>
          <el-button type="warning" link @click="act(scope.row.taskId, 'return')">退回</el-button>
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
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import http from '../api/http'
import { formatDateTime } from '../utils/datetime'

const rows = ref([])
const router = useRouter()
const pageSizeOptions = [10, 20, 50, 100]
const query = reactive({
  documentNo: '',
  enterpriseName: '',
  timeRange: []
})
const pager = reactive({
  page: 1,
  size: 20,
  total: 0
})

const COMMENT_MAX_LENGTH = 200

const load = async () => {
  const res = await http.get('/approvals/todo', {
    params: {
      documentNo: query.documentNo || undefined,
      enterpriseName: query.enterpriseName || undefined,
      startTime: query.timeRange?.[0] || undefined,
      endTime: query.timeRange?.[1] || undefined,
      page: pager.page,
      size: pager.size
    }
  })
  const data = res.data || {}
  rows.value = data.records || []
  pager.total = data.total || 0
  pager.page = data.page || pager.page
  pager.size = data.size || pager.size
}

function getActionDialogConfig(action) {
  if (action === 'approve') {
    return {
      title: '审批通过',
      message: '请确认发送内容',
      inputValue: '新型技改城市项目材料审核通过',
      inputPlaceholder: '请输入发送内容',
      confirmButtonText: '确认发送'
    }
  }
  return {
    title: action === 'reject' ? '驳回' : '退回修改',
    message: '请补充发送内容',
    inputValue: '新型技改城市项目材料需要补充完善',
    inputPlaceholder: '可直接在默认文案后补充说明',
    confirmButtonText: '确认发送'
  }
}

const act = async (taskId, action) => {
  const dialog = getActionDialogConfig(action)
  const { value } = await ElMessageBox.prompt(dialog.message, dialog.title, {
    inputValue: dialog.inputValue,
    inputPlaceholder: dialog.inputPlaceholder,
    confirmButtonText: dialog.confirmButtonText,
    cancelButtonText: '取消',
    inputValidator: (val) => {
      const text = (val || '').trim()
      if (!text) return '发送内容不能为空'
      if (text.length > COMMENT_MAX_LENGTH) return `发送内容不能超过${COMMENT_MAX_LENGTH}个字`
      return true
    }
  })
  await http.post(`/approvals/${taskId}/${action}`, { comment: value.trim() })
  await load()
}

const viewSubmission = (submissionId, taskId) => {
  router.push(`/approvals/submission-view/${submissionId}?taskId=${taskId}`)
}

const editSubmission = async (submissionId, taskId) => {
  try {
    await ElMessageBox.confirm('您确定要修改企业填报信息，点击确定将进入编辑页面', '进入编辑', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    router.push(`/approvals/submission-view/${submissionId}?taskId=${taskId}&mode=edit`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

const search = () => {
  pager.page = 1
  load()
}

const resetQuery = () => {
  query.documentNo = ''
  query.enterpriseName = ''
  query.timeRange = []
  pager.page = 1
  load()
}

const onPageChange = (page) => {
  pager.page = page
  load()
}

const onSizeChange = (size) => {
  pager.page = 1
  pager.size = size
  load()
}

onMounted(load)
</script>
