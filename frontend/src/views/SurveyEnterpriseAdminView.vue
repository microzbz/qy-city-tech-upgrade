<template>
  <div class="page-card">
    <h3 class="section-title">调研企业库维护</h3>

    <el-form inline>
      <el-form-item label="镇街园区">
        <el-input v-model="query.townPark" placeholder="镇街园区关键词" clearable />
      </el-form-item>
      <el-form-item label="企业名称">
        <el-input v-model="query.enterpriseName" placeholder="企业名称关键词" clearable />
      </el-form-item>
      <el-form-item label="行业代码">
        <el-input v-model="query.industryCode" placeholder="行业代码" clearable />
      </el-form-item>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <el-button type="success" @click="openCreate">新增</el-button>
    </el-form>

    <el-table :data="rows" border v-loading="loading" style="margin-top: 10px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="townPark" label="镇街园区" min-width="160" />
      <el-table-column prop="enterpriseName" label="企业名称" min-width="280" />
      <el-table-column prop="industryCode" label="行业代码" width="120" />
      <el-table-column prop="enterpriseCodeFirstDigit" label="编码1位" width="90" />
      <el-table-column prop="enterpriseCodeTownDigits" label="编码2-3位" width="110" />
      <el-table-column prop="enterpriseCodeIndustryDigits" label="编码4-5位" width="110" />
      <el-table-column prop="enterpriseCodeSequenceDigits" label="编码6-8位" width="110" />
      <el-table-column label="导入时间" min-width="180">
        <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button>
          <el-button type="danger" link @click="removeRow(scope.row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑调研企业信息' : '新增调研企业信息'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="镇街园区" prop="townPark">
          <el-input v-model="form.townPark" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="企业名称" prop="enterpriseName">
          <el-input v-model="form.enterpriseName" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="所属行业代码">
          <el-input v-model="form.industryCode" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="企业编码第1位">
          <el-input v-model="form.enterpriseCodeFirstDigit" maxlength="1" />
        </el-form-item>
        <el-form-item label="企业编码第2-3位">
          <el-input v-model="form.enterpriseCodeTownDigits" maxlength="2" />
        </el-form-item>
        <el-form-item label="企业编码第4-5位">
          <el-input v-model="form.enterpriseCodeIndustryDigits" maxlength="2" />
        </el-form-item>
        <el-form-item label="企业编码第6-8位">
          <el-input v-model="form.enterpriseCodeSequenceDigits" maxlength="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const loading = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const editId = ref(null)
const formRef = ref()
const pageSizeOptions = [10, 20, 50, 100]

const query = reactive({
  townPark: '',
  enterpriseName: '',
  industryCode: ''
})

const pager = reactive({
  page: 1,
  size: 20,
  total: 0
})

const form = reactive({
  townPark: '',
  enterpriseName: '',
  industryCode: '',
  enterpriseCodeFirstDigit: '',
  enterpriseCodeTownDigits: '',
  enterpriseCodeIndustryDigits: '',
  enterpriseCodeSequenceDigits: ''
})

const rules = {
  townPark: [{ required: true, message: '请输入镇街园区', trigger: 'blur' }],
  enterpriseName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }]
}

const cleanParams = (obj) => {
  const out = {}
  Object.keys(obj).forEach((k) => {
    const val = typeof obj[k] === 'string' ? obj[k].trim() : obj[k]
    if (val !== '' && val !== null && typeof val !== 'undefined') {
      out[k] = val
    }
  })
  return out
}

const cleanPayload = () => cleanParams({
  townPark: form.townPark,
  enterpriseName: form.enterpriseName,
  industryCode: form.industryCode,
  enterpriseCodeFirstDigit: form.enterpriseCodeFirstDigit,
  enterpriseCodeTownDigits: form.enterpriseCodeTownDigits,
  enterpriseCodeIndustryDigits: form.enterpriseCodeIndustryDigits,
  enterpriseCodeSequenceDigits: form.enterpriseCodeSequenceDigits
})

const formatDateTime = (v) => {
  if (!v) return '-'
  return `${v}`.replace('T', ' ')
}

const resetForm = () => {
  form.townPark = ''
  form.enterpriseName = ''
  form.industryCode = ''
  form.enterpriseCodeFirstDigit = ''
  form.enterpriseCodeTownDigits = ''
  form.enterpriseCodeIndustryDigits = ''
  form.enterpriseCodeSequenceDigits = ''
}

const load = async () => {
  loading.value = true
  try {
    const res = await http.get('/survey-enterprises', {
      params: {
        ...cleanParams(query),
        page: pager.page,
        size: pager.size
      }
    })
    const data = res.data || {}
    rows.value = data.records || []
    pager.total = data.total || 0
    pager.page = data.page || pager.page
    pager.size = data.size || pager.size
    if (rows.value.length === 0 && pager.total > 0 && pager.page > 1) {
      pager.page -= 1
      await load()
    }
  } finally {
    loading.value = false
  }
}

const search = async () => {
  pager.page = 1
  await load()
}

const resetQuery = async () => {
  query.townPark = ''
  query.enterpriseName = ''
  query.industryCode = ''
  pager.page = 1
  await load()
}

const openCreate = () => {
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  editId.value = row.id
  form.townPark = row.townPark || ''
  form.enterpriseName = row.enterpriseName || ''
  form.industryCode = row.industryCode || ''
  form.enterpriseCodeFirstDigit = row.enterpriseCodeFirstDigit || ''
  form.enterpriseCodeTownDigits = row.enterpriseCodeTownDigits || ''
  form.enterpriseCodeIndustryDigits = row.enterpriseCodeIndustryDigits || ''
  form.enterpriseCodeSequenceDigits = row.enterpriseCodeSequenceDigits || ''
  dialogVisible.value = true
}

const save = async () => {
  await formRef.value.validate()
  const payload = cleanPayload()
  if (editId.value) {
    await http.put(`/survey-enterprises/${editId.value}`, payload)
  } else {
    await http.post('/survey-enterprises', payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

const removeRow = async (row) => {
  try {
    await ElMessageBox.confirm(
      `删除后将影响基于“${row.enterpriseName}”的行业匹配结果，且无法恢复。是否确认删除？`,
      '高风险删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }
  await http.delete(`/survey-enterprises/${row.id}`)
  ElMessage.success('删除成功')
  await load()
}

const onSizeChange = async () => {
  pager.page = 1
  await load()
}

const onPageChange = async () => {
  await load()
}

onMounted(load)
</script>
