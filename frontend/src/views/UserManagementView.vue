<template>
  <div class="page-card">
    <h3 class="section-title">用户管理</h3>
    <el-button type="primary" @click="openCreate">新建用户</el-button>
    <el-table :data="rows" border style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="80"/>
      <el-table-column prop="username" label="用户名" width="140"/>
      <el-table-column prop="displayName" label="姓名" width="140"/>
      <el-table-column prop="status" label="状态" width="120"/>
      <el-table-column prop="enterpriseId" label="企业ID" width="100"/>
      <el-table-column prop="enterpriseCodeFirstDigitScope" label="管理编码1位" width="130">
        <template #default="scope">{{ scope.row.enterpriseCodeFirstDigitScope || '全部' }}</template>
      </el-table-column>
      <el-table-column prop="townStreetCodeScope" label="镇街编号" width="150">
        <template #default="scope">{{ townCodeLabel(scope.row.townStreetCodeScope) }}</template>
      </el-table-column>
      <el-table-column label="角色">
        <template #default="scope">{{ (scope.row.roleCodes || []).join(', ') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope"><el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button></template>
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

    <el-dialog v-model="showDialog" title="用户信息" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.id"/></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.displayName"/></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password"/></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option label="ACTIVE" value="ACTIVE"/><el-option label="DISABLED" value="DISABLED"/></el-select></el-form-item>
        <el-form-item label="企业ID"><el-input v-model="form.enterpriseId"/></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCodes" multiple>
            <el-option label="ENTERPRISE_USER" value="ENTERPRISE_USER"/>
            <el-option label="APPROVER_ADMIN" value="APPROVER_ADMIN"/>
            <el-option label="TOWN_MONITOR" value="TOWN_MONITOR"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="isApproverForm" label="管理编码1位">
          <el-input
            v-model="form.enterpriseCodeFirstDigitScope"
            maxlength="1"
            clearable
            placeholder="留空表示可查看全部"
          />
        </el-form-item>
        <el-form-item v-if="isTownMonitorForm" label="镇街编号">
          <el-select
            v-model="form.townStreetCodeScope"
            filterable
            clearable
            placeholder="请选择镇街编号"
          >
            <el-option
              v-for="item in townCodes"
              :key="item.code"
              :label="`${item.code} ${item.name}`"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const rows = ref([])
const townCodes = ref([])
const showDialog = ref(false)
const pageSizeOptions = [10, 20, 50, 100]
const pager = reactive({ page: 1, size: 20, total: 0 })
const form = reactive({
  id: null,
  username: '',
  displayName: '',
  password: '',
  status: 'ACTIVE',
  enterpriseId: '',
  enterpriseCodeFirstDigitScope: '',
  townStreetCodeScope: '',
  roleCodes: []
})
const isApproverForm = computed(() => form.roleCodes.includes('APPROVER_ADMIN'))
const isTownMonitorForm = computed(() => form.roleCodes.includes('TOWN_MONITOR'))

const townCodeLabel = (code) => {
  if (!code) return '-'
  const matched = townCodes.value.find((item) => item.code === code)
  return matched ? `${matched.code} ${matched.name}` : code
}

const load = async () => {
  const res = await http.get('/users', { params: { page: pager.page, size: pager.size } })
  const data = res.data || {}
  rows.value = data.records || []
  pager.total = data.total || 0
  pager.page = data.page || pager.page
  pager.size = data.size || pager.size
}

const loadTownCodes = async () => {
  const res = await http.get('/town-street-codes')
  townCodes.value = res.data || []
}

const reset = () => {
  form.id = null
  form.username = ''
  form.displayName = ''
  form.password = ''
  form.status = 'ACTIVE'
  form.enterpriseId = ''
  form.enterpriseCodeFirstDigitScope = ''
  form.townStreetCodeScope = ''
  form.roleCodes = []
}

const openCreate = () => {
  reset()
  showDialog.value = true
}

const openEdit = (row) => {
  form.id = row.id
  form.username = row.username
  form.displayName = row.displayName
  form.password = ''
  form.status = row.status
  form.enterpriseId = row.enterpriseId
  form.enterpriseCodeFirstDigitScope = row.enterpriseCodeFirstDigitScope || ''
  form.townStreetCodeScope = row.townStreetCodeScope || ''
  form.roleCodes = [...(row.roleCodes || [])]
  showDialog.value = true
}

const save = async () => {
  if (isApproverForm.value && !`${form.enterpriseCodeFirstDigitScope || ''}`.trim()) {
    try {
      await ElMessageBox.confirm(
        '该用户可以查看任意企业的填报数据，确定创建？',
        '权限确认',
        {
          type: 'warning',
          confirmButtonText: '确定',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
  }
  if (isTownMonitorForm.value && !`${form.townStreetCodeScope || ''}`.trim()) {
    ElMessage.warning('请选择镇街编号')
    return
  }
  if (!form.id) {
    const payload = {
      username: form.username,
      password: form.password,
      displayName: form.displayName,
      enterpriseId: form.enterpriseId ? Number(form.enterpriseId) : null,
      enterpriseCodeFirstDigitScope: isApproverForm.value ? (form.enterpriseCodeFirstDigitScope || null) : null,
      townStreetCodeScope: isTownMonitorForm.value ? (form.townStreetCodeScope || null) : null,
      roleCodes: form.roleCodes
    }
    await http.post('/users', payload)
  } else {
    await http.put(`/users/${form.id}/roles`, {
      roleCodes: form.roleCodes,
      enterpriseCodeFirstDigitScope: isApproverForm.value ? (form.enterpriseCodeFirstDigitScope || null) : null,
      townStreetCodeScope: isTownMonitorForm.value ? (form.townStreetCodeScope || null) : null
    })
    await http.put(`/users/${form.id}`, {
      displayName: form.displayName,
      password: form.password,
      status: form.status,
      enterpriseId: form.enterpriseId ? Number(form.enterpriseId) : null,
      enterpriseCodeFirstDigitScope: isApproverForm.value ? (form.enterpriseCodeFirstDigitScope || null) : null,
      townStreetCodeScope: isTownMonitorForm.value ? (form.townStreetCodeScope || null) : null
    })
  }
  ElMessage.success('保存成功')
  showDialog.value = false
  await load()
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

onMounted(async () => {
  await loadTownCodes()
  await load()
})

watch(
  () => form.roleCodes,
  () => {
    if (!isApproverForm.value) {
      form.enterpriseCodeFirstDigitScope = ''
    }
    if (!isTownMonitorForm.value) {
      form.townStreetCodeScope = ''
    }
  },
  { deep: true }
)
</script>
