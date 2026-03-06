<template>
  <div class="page-card">
    <h3 class="section-title">审计日志</h3>
    <el-table :data="rows" border>
      <el-table-column prop="id" label="ID" width="80"/>
      <el-table-column prop="userDisplayName" label="操作用户" width="150"/>
      <el-table-column prop="userId" label="用户ID" width="100"/>
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
      <el-table-column prop="createdAt" label="时间" width="180"/>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const rows = ref([])
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

onMounted(async () => {
  const res = await http.get('/audit-logs')
  rows.value = res.data || []
})
</script>
