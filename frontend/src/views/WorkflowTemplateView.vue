<template>
  <div class="page-card">
    <h3 class="section-title">流程模板管理</h3>
    <div class="tips-line">同一业务类型可配置多个模板，但同一时刻仅能启用一个模板。</div>

    <div class="toolbar-line">
      <el-button type="primary" @click="createNewTemplate">新建模板</el-button>
    </div>

    <el-divider />
    <h4>已存在模板</h4>
    <el-table :data="templates" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="templateName" label="模板名称" />
      <el-table-column prop="businessType" label="业务类型" width="140" />
      <el-table-column label="启用状态" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.active ? 'success' : 'info'">{{ scope.row.active ? '已启用' : '未启用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审批开关" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.approvalEnabled ? 'success' : 'warning'">{{ scope.row.approvalEnabled ? '已启用' : '已关闭' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="节点数" width="100">
        <template #default="scope">{{ scope.row.nodes?.length || 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="scope">
          <el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button>
          <el-button v-if="!scope.row.active" type="success" link @click="activate(scope.row.id)">设为启用</el-button>
          <el-button type="danger" link @click="removeTemplate(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑审批模板' : '新建审批模板'" width="1080px" destroy-on-close>
      <el-form inline>
        <el-form-item label="模板名称">
          <el-input v-model="form.templateName" placeholder="请输入模板名称" style="width: 280px" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.active" />
        </el-form-item>
        <el-form-item label="启用审批">
          <el-switch v-model="form.approvalEnabled" />
        </el-form-item>
        <el-button type="primary" @click="addNode">新增节点</el-button>
      </el-form>

      <el-table :data="form.nodes" border>
        <el-table-column prop="nodeSeq" label="顺序" width="120">
          <template #default="scope">
            <el-input-number v-model="scope.row.nodeSeq" :min="1" />
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="节点名称">
          <template #default="scope">
            <el-input v-model="scope.row.nodeName" placeholder="如：科室审批" />
          </template>
        </el-table-column>
        <el-table-column prop="roleCode" label="审批角色" width="220">
          <template #default="scope">
            <el-select v-model="scope.row.roleCode" style="width: 180px">
              <el-option label="审批管理员" value="APPROVER_ADMIN" />
              <el-option label="系统管理员" value="SYS_ADMIN" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button type="danger" link @click="removeNode(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="success" @click="save">保存模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const templates = ref([])
const editId = ref(null)
const dialogVisible = ref(false)
const form = reactive({
  templateName: '',
  businessType: 'SUBMISSION',
  active: true,
  approvalEnabled: true,
  nodes: []
})

const buildNode = (seq) => ({
  nodeSeq: seq,
  nodeName: '',
  roleCode: 'APPROVER_ADMIN',
  allowApprove: true,
  allowReject: true,
  allowReturn: true
})

const resetForm = () => {
  editId.value = null
  form.templateName = ''
  form.businessType = 'SUBMISSION'
  form.active = true
  form.approvalEnabled = true
  form.nodes = [buildNode(1)]
}

const load = async () => {
  const res = await http.get('/workflow/templates', { params: { businessType: 'SUBMISSION' } })
  templates.value = res.data || []
}

const createNewTemplate = () => {
  resetForm()
  dialogVisible.value = true
}

const addNode = () => {
  const nextSeq = form.nodes.length ? Math.max(...form.nodes.map((n) => Number(n.nodeSeq) || 0)) + 1 : 1
  form.nodes.push(buildNode(nextSeq))
}

const removeNode = (idx) => {
  form.nodes.splice(idx, 1)
  if (!form.nodes.length) {
    form.nodes.push(buildNode(1))
  }
}

const validateForm = () => {
  if (!form.templateName || !form.templateName.trim()) {
    ElMessage.error('模板名称不能为空')
    return false
  }
  if (!form.nodes.length) {
    ElMessage.error('请至少配置一个审批节点')
    return false
  }

  const seqSet = new Set()
  for (let i = 0; i < form.nodes.length; i += 1) {
    const node = form.nodes[i]
    const seq = Number(node.nodeSeq)
    if (!Number.isInteger(seq) || seq <= 0) {
      ElMessage.error(`第 ${i + 1} 个节点顺序必须为正整数`)
      return false
    }
    if (seqSet.has(seq)) {
      ElMessage.error(`节点顺序不能重复：${seq}`)
      return false
    }
    seqSet.add(seq)

    if (!node.nodeName || !node.nodeName.trim()) {
      ElMessage.error(`第 ${i + 1} 个节点名称不能为空`)
      return false
    }
    if (!node.roleCode) {
      ElMessage.error(`第 ${i + 1} 个节点审批角色不能为空`)
      return false
    }
  }
  return true
}

const normalizedNodes = () =>
  [...form.nodes]
    .map((n) => ({
      nodeSeq: Number(n.nodeSeq),
      nodeName: n.nodeName?.trim(),
      roleCode: n.roleCode,
      allowApprove: n.allowApprove !== false,
      allowReject: n.allowReject !== false,
      allowReturn: n.allowReturn !== false
    }))
    .sort((a, b) => a.nodeSeq - b.nodeSeq)

const save = async () => {
  if (!validateForm()) return
  const payload = {
    templateName: form.templateName.trim(),
    businessType: 'SUBMISSION',
    active: form.active,
    approvalEnabled: form.approvalEnabled !== false,
    nodes: normalizedNodes()
  }
  if (editId.value) {
    await http.put(`/workflow/templates/${editId.value}`, payload)
  } else {
    await http.post('/workflow/templates', payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
  resetForm()
}

const openEdit = (row) => {
  editId.value = row.id
  form.templateName = row.templateName || ''
  form.businessType = row.businessType || 'SUBMISSION'
  form.active = !!row.active
  form.approvalEnabled = row.approvalEnabled !== false
  form.nodes = JSON.parse(JSON.stringify(row.nodes || []))
  if (!form.nodes.length) {
    form.nodes = [buildNode(1)]
  }
  dialogVisible.value = true
}

const activate = async (id) => {
  await http.post(`/workflow/templates/${id}/activate`)
  ElMessage.success('已切换启用模板')
  await load()
}

const removeTemplate = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.templateName}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await http.delete(`/workflow/templates/${row.id}`)
  ElMessage.success('删除成功')
  await load()
}

onMounted(async () => {
  resetForm()
  await load()
})
</script>

<style scoped>
.tips-line {
  margin-bottom: 10px;
  color: #65758b;
  font-size: 13px;
}

.toolbar-line {
  margin-bottom: 6px;
}
</style>
