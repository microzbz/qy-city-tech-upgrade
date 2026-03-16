<template>
  <div class="page-card">
    <h3 class="section-title">审计日志</h3>
    <el-form :inline="true" class="filter-form" @submit.prevent>
      <el-form-item label="单据号">
        <el-input v-model="filters.documentNo" clearable placeholder="请输入单据号" style="width: 180px" />
      </el-form-item>
      <el-form-item label="企业名称">
        <el-input v-model="filters.companyName" clearable placeholder="请输入企业名称" style="width: 220px" />
      </el-form-item>
      <el-form-item label="时间范围">
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
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="rows" border>
      <el-table-column prop="id" label="ID" width="80"/>
      <el-table-column prop="userDisplayName" label="操作用户" width="150"/>
      <el-table-column prop="userId" label="用户ID" width="100"/>
      <el-table-column prop="documentNo" label="单据号" width="160"/>
      <el-table-column prop="enterpriseName" label="企业名称" width="220"/>
      <el-table-column label="模块" width="140">
        <template #default="{ row }">
          {{ moduleLabel(row.moduleName) }}
        </template>
      </el-table-column>
      <el-table-column label="动作" width="160">
        <template #default="{ row }">
          {{ actionLabel(row.actionName) }}
        </template>
      </el-table-column>
      <el-table-column prop="businessId" label="业务ID" width="120"/>
      <el-table-column prop="detailText" label="详情"/>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
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
import { onMounted, reactive, ref } from 'vue'
import http from '../api/http'
import { formatDateTime } from '../utils/datetime'

const rows = ref([])
const pageSizeOptions = [10, 20, 50, 100]
const filters = reactive({
  documentNo: '',
  companyName: '',
  timeRange: []
})
const pager = reactive({
  page: 1,
  size: 20,
  total: 0
})
const moduleMap = {
  AUTH: '认证',
  SUBMISSION: '企业填报',
  WORKFLOW: '审批流程',
  APPROVAL: '审批',
  USER: '用户管理',
  INDUSTRY_MAPPING: '行业映射',
  FILE: '附件'
}

const actionMap = {
  LOGIN: '登录',
  LOGOUT: '退出登录',
  SAVE_DRAFT: '保存',
  SUBMIT: '提交审批',
  START: '发起流程',
  APPROVE: '通过',
  REJECT: '驳回',
  RETURN: '退回修改',
  CREATE_USER: '新增用户',
  UPDATE_USER: '编辑用户',
  ASSIGN_ROLE: '分配角色',
  CREATE_TEMPLATE: '新增流程模板',
  UPDATE_TEMPLATE: '编辑流程模板',
  ACTIVATE_TEMPLATE: '启用流程模板',
  DELETE_TEMPLATE: '删除流程模板',
  CREATE_PROCESS_MAPPING: '新增工序映射',
  UPDATE_PROCESS_MAPPING: '编辑工序映射',
  DELETE_PROCESS_MAPPING: '删除工序映射',
  CREATE_EQUIPMENT_MAPPING: '新增设备映射',
  UPDATE_EQUIPMENT_MAPPING: '编辑设备映射',
  DELETE_EQUIPMENT_MAPPING: '删除设备映射',
  UPLOAD: '上传附件',
  DELETE: '删除附件'
}

const moduleLabel = (value) => moduleMap[value] || value || '-'
const actionLabel = (value) => actionMap[value] || value || '-'

const load = async () => {
  const res = await http.get('/audit-logs', {
    params: {
      documentNo: filters.documentNo || undefined,
      companyName: filters.companyName || undefined,
      startTime: filters.timeRange?.[0] || undefined,
      endTime: filters.timeRange?.[1] || undefined,
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

const reset = () => {
  filters.documentNo = ''
  filters.companyName = ''
  filters.timeRange = []
  pager.page = 1
  load()
}

const search = () => {
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
