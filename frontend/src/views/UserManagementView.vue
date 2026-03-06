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
      <el-table-column label="角色">
        <template #default="scope">{{ (scope.row.roleCodes || []).join(', ') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope"><el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button></template>
      </el-table-column>
    </el-table>

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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const rows = ref([])
const showDialog = ref(false)
const form = reactive({ id: null, username: '', displayName: '', password: '', status: 'ACTIVE', enterpriseId: '', roleCodes: [] })

const load = async () => {
  const res = await http.get('/users')
  rows.value = res.data || []
}

const reset = () => {
  form.id = null
  form.username = ''
  form.displayName = ''
  form.password = ''
  form.status = 'ACTIVE'
  form.enterpriseId = ''
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
  form.roleCodes = [...(row.roleCodes || [])]
  showDialog.value = true
}

const save = async () => {
  if (!form.id) {
    const payload = {
      username: form.username,
      password: form.password,
      displayName: form.displayName,
      enterpriseId: form.enterpriseId ? Number(form.enterpriseId) : null,
      roleCodes: form.roleCodes
    }
    await http.post('/users', payload)
  } else {
    await http.put(`/users/${form.id}`, {
      displayName: form.displayName,
      password: form.password,
      status: form.status,
      enterpriseId: form.enterpriseId ? Number(form.enterpriseId) : null
    })
    await http.put(`/users/${form.id}/roles`, { roleCodes: form.roleCodes })
  }
  ElMessage.success('保存成功')
  showDialog.value = false
  await load()
}

onMounted(load)
</script>
