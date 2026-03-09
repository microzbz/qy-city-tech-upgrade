<template>
  <div class="page-card">
    <h3 class="section-title">行业映射管理</h3>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="行业代码-工序" name="process">
        <el-form inline>
          <el-form-item label="行业代码">
            <el-input v-model="processQuery.industryCode" placeholder="如 392" clearable />
          </el-form-item>
          <el-form-item label="工序关键词">
            <el-input v-model="processQuery.processName" placeholder="工序关键词" clearable />
          </el-form-item>
          <el-button type="primary" @click="searchProcessMappings">查询</el-button>
          <el-button @click="resetProcessQuery">重置</el-button>
          <el-button type="success" @click="openProcessCreate">新增映射</el-button>
        </el-form>

        <el-table :data="processRows" border v-loading="processLoading" style="margin-top: 10px">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="industryCode" label="行业代码" width="120" />
          <el-table-column prop="industryName" label="行业名称" min-width="180" />
          <el-table-column label="特殊模式" width="120">
            <template #default="scope">
              {{ scope.row.specialMode ? '是' : '否' }}
            </template>
          </el-table-column>
          <el-table-column prop="processNamesText" label="主要工序（分号分隔）" min-width="520" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="openProcessEdit(scope.row)">编辑</el-button>
              <el-button type="danger" link @click="removeProcess(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-pager">
          <el-pagination
            v-model:current-page="processPager.page"
            v-model:page-size="processPager.size"
            :total="processPager.total"
            :page-sizes="pageSizeOptions"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="onProcessSizeChange"
            @current-change="onProcessPageChange"
          />
        </div>

        <el-dialog v-model="processDialogVisible" title="行业代码-工序映射" width="720px">
          <el-form ref="processFormRef" :model="processForm" :rules="processRules" label-width="130px">
            <el-form-item label="行业代码" prop="industryCode">
              <el-input v-model="processForm.industryCode" />
            </el-form-item>
            <el-form-item label="行业名称" prop="industryName">
              <el-input v-model="processForm.industryName" />
            </el-form-item>
            <el-form-item label="特殊模式">
              <el-switch
                v-model="processForm.specialMode"
                active-text="是"
                inactive-text="否"
              />
            </el-form-item>
            <el-form-item label="主要工序串" prop="processNamesText">
              <el-input
                v-model="processForm.processNamesText"
                type="textarea"
                :rows="4"
                :placeholder="processForm.specialMode ? '特殊模式下可按逗号或分号分隔，例如：晶圆制造、光刻、刻蚀' : '请按分号分隔，例如：切割;焊接;装配'"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="processDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveProcess">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="工序-设备" name="equipment">
        <el-form inline>
          <el-form-item label="行业代码">
            <el-input v-model="equipmentQuery.industryCode" placeholder="留空表示共性设备" clearable />
          </el-form-item>
          <el-form-item label="主要工序">
            <el-input v-model="equipmentQuery.processName" placeholder="工序关键词" clearable />
          </el-form-item>
          <el-form-item label="设备关键词">
            <el-input v-model="equipmentQuery.equipmentName" placeholder="设备关键词" clearable />
          </el-form-item>
          <el-button type="primary" @click="searchEquipmentMappings">查询</el-button>
          <el-button @click="resetEquipmentQuery">重置</el-button>
          <el-button type="success" @click="openEquipmentCreate">新增映射</el-button>
        </el-form>

        <el-table :data="equipmentRows" border v-loading="equipmentLoading" style="margin-top: 10px">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="行业代码" width="140">
            <template #default="scope">
              {{ scope.row.industryCode || '共性' }}
            </template>
          </el-table-column>
          <el-table-column prop="processName" label="主要工序" min-width="280" />
          <el-table-column prop="equipmentNamesText" label="主要设备（顿号分隔）" min-width="640" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="openEquipmentEdit(scope.row)">编辑</el-button>
              <el-button type="danger" link @click="removeEquipment(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-pager">
          <el-pagination
            v-model:current-page="equipmentPager.page"
            v-model:page-size="equipmentPager.size"
            :total="equipmentPager.total"
            :page-sizes="pageSizeOptions"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="onEquipmentSizeChange"
            @current-change="onEquipmentPageChange"
          />
        </div>

        <el-dialog v-model="equipmentDialogVisible" title="工序-设备映射" width="720px">
          <el-form ref="equipmentFormRef" :model="equipmentForm" :rules="equipmentRules" label-width="130px">
            <el-form-item label="行业代码">
              <el-input v-model="equipmentForm.industryCode" placeholder="留空表示共性设备" />
            </el-form-item>
            <el-form-item label="主要工序" prop="processName">
              <el-input v-model="equipmentForm.processName" />
            </el-form-item>
            <el-form-item label="主要设备串" prop="equipmentNamesText">
              <el-input
                v-model="equipmentForm.equipmentNamesText"
                type="textarea"
                :rows="4"
                placeholder="请按顿号分隔，例如：数控车床、加工中心、检测设备"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="equipmentDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveEquipment">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const activeTab = ref('process')
const processLoading = ref(false)
const equipmentLoading = ref(false)
const processRows = ref([])
const equipmentRows = ref([])
const processDialogVisible = ref(false)
const equipmentDialogVisible = ref(false)
const processEditId = ref(null)
const equipmentEditId = ref(null)
const processFormRef = ref()
const equipmentFormRef = ref()
const pageSizeOptions = [10, 20, 50, 100]

const processQuery = reactive({
  industryCode: '',
  processName: ''
})

const equipmentQuery = reactive({
  industryCode: '',
  processName: '',
  equipmentName: ''
})

const processPager = reactive({
  page: 1,
  size: 20,
  total: 0
})

const equipmentPager = reactive({
  page: 1,
  size: 20,
  total: 0
})

const processForm = reactive({
  industryCode: '',
  industryName: '',
  processNamesText: '',
  specialMode: false
})

const equipmentForm = reactive({
  industryCode: '',
  processName: '',
  equipmentNamesText: ''
})

const processRules = {
  industryCode: [{ required: true, message: '请输入行业代码', trigger: 'blur' }],
  industryName: [{ required: true, message: '请输入行业名称', trigger: 'blur' }],
  processNamesText: [{ required: true, message: '请输入主要工序串（分号分隔）', trigger: 'blur' }]
}

const equipmentRules = {
  processName: [{ required: true, message: '请输入主要工序', trigger: 'blur' }],
  equipmentNamesText: [{ required: true, message: '请输入主要设备串（顿号分隔）', trigger: 'blur' }]
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

const loadProcessMappings = async () => {
  processLoading.value = true
  try {
    const res = await http.get('/industry/admin/process-mappings', {
      params: {
        ...cleanParams(processQuery),
        page: processPager.page,
        size: processPager.size
      }
    })
    const data = res.data || {}
    processRows.value = data.records || []
    processPager.total = data.total || 0
    processPager.page = data.page || processPager.page
    processPager.size = data.size || processPager.size
    if (processRows.value.length === 0 && processPager.total > 0 && processPager.page > 1) {
      processPager.page -= 1
      await loadProcessMappings()
    }
  } finally {
    processLoading.value = false
  }
}

const loadEquipmentMappings = async () => {
  equipmentLoading.value = true
  try {
    const res = await http.get('/industry/admin/equipment-mappings', {
      params: {
        ...cleanParams(equipmentQuery),
        page: equipmentPager.page,
        size: equipmentPager.size
      }
    })
    const data = res.data || {}
    equipmentRows.value = data.records || []
    equipmentPager.total = data.total || 0
    equipmentPager.page = data.page || equipmentPager.page
    equipmentPager.size = data.size || equipmentPager.size
    if (equipmentRows.value.length === 0 && equipmentPager.total > 0 && equipmentPager.page > 1) {
      equipmentPager.page -= 1
      await loadEquipmentMappings()
    }
  } finally {
    equipmentLoading.value = false
  }
}

const searchProcessMappings = async () => {
  processPager.page = 1
  await loadProcessMappings()
}

const searchEquipmentMappings = async () => {
  equipmentPager.page = 1
  await loadEquipmentMappings()
}

const onProcessPageChange = async (page) => {
  processPager.page = page
  await loadProcessMappings()
}

const onEquipmentPageChange = async (page) => {
  equipmentPager.page = page
  await loadEquipmentMappings()
}

const onProcessSizeChange = async (size) => {
  processPager.size = size
  processPager.page = 1
  await loadProcessMappings()
}

const onEquipmentSizeChange = async (size) => {
  equipmentPager.size = size
  equipmentPager.page = 1
  await loadEquipmentMappings()
}

const resetProcessForm = () => {
  processEditId.value = null
  processForm.industryCode = ''
  processForm.industryName = ''
  processForm.processNamesText = ''
  processForm.specialMode = false
}

const resetEquipmentForm = () => {
  equipmentEditId.value = null
  equipmentForm.industryCode = ''
  equipmentForm.processName = ''
  equipmentForm.equipmentNamesText = ''
}

const resetProcessQuery = async () => {
  processQuery.industryCode = ''
  processQuery.processName = ''
  processPager.page = 1
  await loadProcessMappings()
}

const resetEquipmentQuery = async () => {
  equipmentQuery.industryCode = ''
  equipmentQuery.processName = ''
  equipmentQuery.equipmentName = ''
  equipmentPager.page = 1
  await loadEquipmentMappings()
}

const openProcessCreate = () => {
  resetProcessForm()
  processDialogVisible.value = true
}

const openProcessEdit = (row) => {
  processEditId.value = row.id
  processForm.industryCode = row.industryCode
  processForm.industryName = row.industryName
  processForm.processNamesText = row.processNamesText
  processForm.specialMode = !!row.specialMode
  processDialogVisible.value = true
}

const openEquipmentCreate = () => {
  resetEquipmentForm()
  equipmentDialogVisible.value = true
}

const openEquipmentEdit = (row) => {
  equipmentEditId.value = row.id
  equipmentForm.industryCode = row.industryCode || ''
  equipmentForm.processName = row.processName
  equipmentForm.equipmentNamesText = row.equipmentNamesText
  equipmentDialogVisible.value = true
}

const saveProcess = async () => {
  const ok = await processFormRef.value?.validate().catch(() => false)
  if (!ok) return
  const payload = {
    industryCode: processForm.industryCode.trim(),
    industryName: processForm.industryName.trim(),
    processNamesText: processForm.processNamesText.trim(),
    specialMode: !!processForm.specialMode
  }
  if (processEditId.value) {
    await http.put(`/industry/admin/process-mappings/${processEditId.value}`, payload)
  } else {
    await http.post('/industry/admin/process-mappings', payload)
  }
  processDialogVisible.value = false
  ElMessage.success('保存成功')
  await loadProcessMappings()
}

const saveEquipment = async () => {
  const ok = await equipmentFormRef.value?.validate().catch(() => false)
  if (!ok) return
  const payload = {
    industryCode: equipmentForm.industryCode.trim() || null,
    processName: equipmentForm.processName.trim(),
    equipmentNamesText: equipmentForm.equipmentNamesText.trim()
  }
  if (equipmentEditId.value) {
    await http.put(`/industry/admin/equipment-mappings/${equipmentEditId.value}`, payload)
  } else {
    await http.post('/industry/admin/equipment-mappings', payload)
  }
  equipmentDialogVisible.value = false
  ElMessage.success('保存成功')
  await loadEquipmentMappings()
}

const removeProcess = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该工序映射？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await http.delete(`/industry/admin/process-mappings/${id}`)
  ElMessage.success('删除成功')
  await loadProcessMappings()
}

const removeEquipment = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该设备映射？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await http.delete(`/industry/admin/equipment-mappings/${id}`)
  ElMessage.success('删除成功')
  await loadEquipmentMappings()
}

onMounted(async () => {
  await loadProcessMappings()
  await loadEquipmentMappings()
})
</script>
