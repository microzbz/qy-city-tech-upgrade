<template>
  <div class="page-card">
    <h3 class="section-title">填报选项管理</h3>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="数字化系统选项" name="digital">
        <el-form inline>
          <el-form-item label="选项名称">
            <el-input v-model="digitalQuery.optionName" placeholder="选项关键词" clearable />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-select v-model="digitalQuery.enabled" clearable placeholder="全部" style="width: 120px">
              <el-option label="启用" :value="true" />
              <el-option label="禁用" :value="false" />
            </el-select>
          </el-form-item>
          <el-button type="primary" @click="searchDigital">查询</el-button>
          <el-button @click="resetDigitalQuery">重置</el-button>
          <el-button type="success" @click="openCreateDigital">新增选项</el-button>
        </el-form>

        <el-table :data="digitalRows" border v-loading="digitalLoading" style="margin-top: 10px">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="optionName" label="选项名称" min-width="320" />
          <el-table-column prop="sortNo" label="序号" width="90" />
          <el-table-column label="是否其他" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.otherOption ? 'warning' : 'info'">
                {{ scope.row.otherOption ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'danger'">
                {{ scope.row.enabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="180">
            <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="openEditDigital(scope.row)">编辑</el-button>
              <el-button type="danger" link @click="removeDigital(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-pager">
          <el-pagination
            v-model:current-page="digitalPager.page"
            v-model:page-size="digitalPager.size"
            :total="digitalPager.total"
            :page-sizes="pageSizeOptions"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="onDigitalSizeChange"
            @current-change="onDigitalPageChange"
          />
        </div>

        <el-dialog v-model="digitalDialogVisible" :title="digitalEditId ? '编辑数字化系统选项' : '新增数字化系统选项'" width="560px">
          <el-form ref="digitalFormRef" :model="digitalForm" :rules="optionRules" label-width="100px">
            <el-form-item label="选项名称" prop="optionName">
              <el-input v-model="digitalForm.optionName" maxlength="255" show-word-limit />
            </el-form-item>
            <el-form-item label="序号" prop="sortNo">
              <el-input-number v-model="digitalForm.sortNo" :min="1" :step="1" />
            </el-form-item>
            <el-form-item label="是否其他">
              <el-switch v-model="digitalForm.otherOption" />
            </el-form-item>
            <el-form-item label="是否启用">
              <el-switch v-model="digitalForm.enabled" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="digitalDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveDigital">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="研发工具选项" name="rdTool">
        <el-form inline>
          <el-form-item label="选项名称">
            <el-input v-model="rdToolQuery.optionName" placeholder="选项关键词" clearable />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-select v-model="rdToolQuery.enabled" clearable placeholder="全部" style="width: 120px">
              <el-option label="启用" :value="true" />
              <el-option label="禁用" :value="false" />
            </el-select>
          </el-form-item>
          <el-button type="primary" @click="searchRdTool">查询</el-button>
          <el-button @click="resetRdToolQuery">重置</el-button>
          <el-button type="success" @click="openCreateRdTool">新增选项</el-button>
        </el-form>

        <el-table :data="rdToolRows" border v-loading="rdToolLoading" style="margin-top: 10px">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="optionName" label="选项名称" min-width="320" />
          <el-table-column prop="sortNo" label="序号" width="90" />
          <el-table-column label="是否其他" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.otherOption ? 'warning' : 'info'">
                {{ scope.row.otherOption ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'danger'">
                {{ scope.row.enabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="180">
            <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="openEditRdTool(scope.row)">编辑</el-button>
              <el-button type="danger" link @click="removeRdTool(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-pager">
          <el-pagination
            v-model:current-page="rdToolPager.page"
            v-model:page-size="rdToolPager.size"
            :total="rdToolPager.total"
            :page-sizes="pageSizeOptions"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="onRdToolSizeChange"
            @current-change="onRdToolPageChange"
          />
        </div>

        <el-dialog v-model="rdToolDialogVisible" :title="rdToolEditId ? '编辑研发工具选项' : '新增研发工具选项'" width="560px">
          <el-form ref="rdToolFormRef" :model="rdToolForm" :rules="optionRules" label-width="100px">
            <el-form-item label="选项名称" prop="optionName">
              <el-input v-model="rdToolForm.optionName" maxlength="255" show-word-limit />
            </el-form-item>
            <el-form-item label="序号" prop="sortNo">
              <el-input-number v-model="rdToolForm.sortNo" :min="1" :step="1" />
            </el-form-item>
            <el-form-item label="是否其他">
              <el-switch v-model="rdToolForm.otherOption" />
            </el-form-item>
            <el-form-item label="是否启用">
              <el-switch v-model="rdToolForm.enabled" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="rdToolDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveRdTool">保存</el-button>
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

const activeTab = ref('digital')
const pageSizeOptions = [10, 20, 50, 100]

const digitalLoading = ref(false)
const rdToolLoading = ref(false)
const digitalRows = ref([])
const rdToolRows = ref([])

const digitalQuery = reactive({
  optionName: '',
  enabled: null
})
const rdToolQuery = reactive({
  optionName: '',
  enabled: null
})

const digitalPager = reactive({
  page: 1,
  size: 20,
  total: 0
})
const rdToolPager = reactive({
  page: 1,
  size: 20,
  total: 0
})

const digitalDialogVisible = ref(false)
const rdToolDialogVisible = ref(false)
const digitalEditId = ref(null)
const rdToolEditId = ref(null)
const digitalFormRef = ref()
const rdToolFormRef = ref()

const digitalForm = reactive({
  optionName: '',
  sortNo: 10,
  otherOption: false,
  enabled: true
})
const rdToolForm = reactive({
  optionName: '',
  sortNo: 10,
  otherOption: false,
  enabled: true
})

const optionRules = {
  optionName: [{ required: true, message: '请输入选项名称', trigger: 'blur' }],
  sortNo: [{ required: true, message: '请输入序号', trigger: 'change' }]
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

const formatDateTime = (v) => {
  if (!v) return '-'
  return `${v}`.replace('T', ' ')
}

const resetDigitalForm = () => {
  digitalForm.optionName = ''
  digitalForm.sortNo = 10
  digitalForm.otherOption = false
  digitalForm.enabled = true
}

const resetRdToolForm = () => {
  rdToolForm.optionName = ''
  rdToolForm.sortNo = 10
  rdToolForm.otherOption = false
  rdToolForm.enabled = true
}

const loadDigital = async () => {
  digitalLoading.value = true
  try {
    const res = await http.get('/submission/options/admin/digital-systems', {
      params: {
        ...cleanParams(digitalQuery),
        page: digitalPager.page,
        size: digitalPager.size
      }
    })
    const data = res.data || {}
    digitalRows.value = data.records || []
    digitalPager.total = data.total || 0
    digitalPager.page = data.page || digitalPager.page
    digitalPager.size = data.size || digitalPager.size
  } finally {
    digitalLoading.value = false
  }
}

const loadRdTool = async () => {
  rdToolLoading.value = true
  try {
    const res = await http.get('/submission/options/admin/rd-tools', {
      params: {
        ...cleanParams(rdToolQuery),
        page: rdToolPager.page,
        size: rdToolPager.size
      }
    })
    const data = res.data || {}
    rdToolRows.value = data.records || []
    rdToolPager.total = data.total || 0
    rdToolPager.page = data.page || rdToolPager.page
    rdToolPager.size = data.size || rdToolPager.size
  } finally {
    rdToolLoading.value = false
  }
}

const searchDigital = async () => {
  digitalPager.page = 1
  await loadDigital()
}

const searchRdTool = async () => {
  rdToolPager.page = 1
  await loadRdTool()
}

const resetDigitalQuery = async () => {
  digitalQuery.optionName = ''
  digitalQuery.enabled = null
  digitalPager.page = 1
  await loadDigital()
}

const resetRdToolQuery = async () => {
  rdToolQuery.optionName = ''
  rdToolQuery.enabled = null
  rdToolPager.page = 1
  await loadRdTool()
}

const onDigitalSizeChange = async () => {
  digitalPager.page = 1
  await loadDigital()
}

const onDigitalPageChange = async () => {
  await loadDigital()
}

const onRdToolSizeChange = async () => {
  rdToolPager.page = 1
  await loadRdTool()
}

const onRdToolPageChange = async () => {
  await loadRdTool()
}

const openCreateDigital = () => {
  digitalEditId.value = null
  resetDigitalForm()
  digitalDialogVisible.value = true
}

const openEditDigital = (row) => {
  digitalEditId.value = row.id
  digitalForm.optionName = row.optionName
  digitalForm.sortNo = row.sortNo
  digitalForm.otherOption = !!row.otherOption
  digitalForm.enabled = !!row.enabled
  digitalDialogVisible.value = true
}

const openCreateRdTool = () => {
  rdToolEditId.value = null
  resetRdToolForm()
  rdToolDialogVisible.value = true
}

const openEditRdTool = (row) => {
  rdToolEditId.value = row.id
  rdToolForm.optionName = row.optionName
  rdToolForm.sortNo = row.sortNo
  rdToolForm.otherOption = !!row.otherOption
  rdToolForm.enabled = !!row.enabled
  rdToolDialogVisible.value = true
}

const saveDigital = async () => {
  const valid = await digitalFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = {
    optionName: `${digitalForm.optionName || ''}`.trim(),
    sortNo: digitalForm.sortNo,
    otherOption: digitalForm.otherOption,
    enabled: digitalForm.enabled
  }
  if (digitalEditId.value) {
    await http.put(`/submission/options/admin/digital-systems/${digitalEditId.value}`, payload)
  } else {
    await http.post('/submission/options/admin/digital-systems', payload)
  }
  digitalDialogVisible.value = false
  ElMessage.success('保存成功')
  await loadDigital()
}

const saveRdTool = async () => {
  const valid = await rdToolFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = {
    optionName: `${rdToolForm.optionName || ''}`.trim(),
    sortNo: rdToolForm.sortNo,
    otherOption: rdToolForm.otherOption,
    enabled: rdToolForm.enabled
  }
  if (rdToolEditId.value) {
    await http.put(`/submission/options/admin/rd-tools/${rdToolEditId.value}`, payload)
  } else {
    await http.post('/submission/options/admin/rd-tools', payload)
  }
  rdToolDialogVisible.value = false
  ElMessage.success('保存成功')
  await loadRdTool()
}

const removeDigital = async (id) => {
  try {
    await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除选项', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await http.delete(`/submission/options/admin/digital-systems/${id}`)
  ElMessage.success('删除成功')
  await loadDigital()
}

const removeRdTool = async (id) => {
  try {
    await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除选项', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await http.delete(`/submission/options/admin/rd-tools/${id}`)
  ElMessage.success('删除成功')
  await loadRdTool()
}

onMounted(async () => {
  await loadDigital()
  await loadRdTool()
})
</script>

<style scoped>
.table-pager {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
</style>
