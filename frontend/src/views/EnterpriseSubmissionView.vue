<template>
  <div class="page-card">
    <h3 class="section-title">企业填报</h3>
    <el-form label-width="130px" :class="['sectioned-form', { 'readonly-appearance': !editable }]">
      <section class="form-section-card">
        <div class="form-section-header">基础信息</div>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="企业名称" required><el-input v-model="form.basicInfo.enterpriseName" :disabled="!editable" @blur="syncIndustryCodeByEnterpriseName"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="统一信用代码"><el-input v-model="form.basicInfo.creditCode" :disabled="!editable"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="所属行业代码" required><el-input v-model="form.basicInfo.industryCode" :disabled="!editable"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="所属行业名称"><el-input v-model="form.basicInfo.industryName" :disabled="!editable"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="企业地址"><el-input v-model="form.basicInfo.address" :disabled="!editable"/></el-form-item></el-col>
        </el-row>
      </section>

      <section class="form-section-card">
        <div class="form-section-header">技改设备投入</div>
        <el-form-item label="主要工序" required>
          <div class="process-box">
            <el-checkbox-group v-model="form.deviceInfo.selectedProcesses" :disabled="!editable" class="process-check-group">
              <el-checkbox v-for="p in processOptions" :key="p" :value="p">{{ p }}</el-checkbox>
            </el-checkbox-group>
            <el-input
              v-if="form.deviceInfo.selectedProcesses?.includes(OTHER_OPTION)"
              v-model="form.deviceInfo.otherProcess"
              class="other-inline-input"
              :disabled="!editable"
              placeholder="请输入其他主要工序（必填）"
            />
            <el-text v-if="!processLoading && processOptions.length === 0" type="info">该行业暂无可选工序</el-text>
          </div>
        </el-form-item>
        <el-form-item label="主要设备" required>
          <div class="process-box">
            <div v-if="industrySpecialMode && equipmentOptions.length > 0" class="process-box">
              <el-checkbox-group
                v-model="form.deviceInfo.selectedEquipments"
                :disabled="!editable || equipmentLoading"
                class="process-check-group"
              >
                <el-checkbox v-for="e in equipmentOptions" :key="e" :value="e">{{ e }}</el-checkbox>
              </el-checkbox-group>
            </div>
            <div v-else-if="equipmentOptions.length > 0" class="equipment-by-process">
              <div class="equipment-matrix-header">
                <div class="equipment-matrix-col">工序</div>
                <div class="equipment-matrix-col">主要设备（可多选）</div>
              </div>
              <div
                v-for="group in equipmentOptionsByProcess"
                :key="group.processName"
                class="equipment-process-row"
              >
                <div class="equipment-process-label">{{ group.processName }}</div>
                <div class="equipment-process-content">
                  <el-checkbox-group
                    v-if="group.options.length > 0"
                    v-model="form.deviceInfo.selectedEquipments"
                    :disabled="!editable || equipmentLoading"
                    class="process-check-group"
                  >
                    <el-checkbox
                      v-for="e in group.options"
                      :key="`${group.processName}_${e}`"
                      :value="e"
                    >
                      {{ e }}
                    </el-checkbox>
                  </el-checkbox-group>
                  <el-text v-else type="info">该工序暂无可选设备</el-text>
                </div>
              </div>
              <div class="equipment-process-row other-row">
                <div class="equipment-process-label">其他</div>
                <div class="equipment-process-content">
                  <el-checkbox-group
                    v-model="form.deviceInfo.selectedEquipments"
                    :disabled="!editable || equipmentLoading"
                    class="process-check-group"
                  >
                    <el-checkbox :value="OTHER_OPTION">其他</el-checkbox>
                  </el-checkbox-group>
                </div>
              </div>
            </div>
            <div v-else class="inline-hint">
              <el-text v-if="equipmentLoading" type="info">设备加载中...</el-text>
              <el-text v-else-if="!industrySpecialMode && !form.deviceInfo.selectedProcesses?.length" type="info">请先勾选主要工序</el-text>
              <el-text v-else type="info">当前工序暂无可选设备</el-text>
            </div>
            <el-input
              v-if="form.deviceInfo.selectedEquipments?.includes(OTHER_OPTION)"
              v-model="form.deviceInfo.otherEquipment"
              class="other-inline-input"
              :disabled="!editable"
              placeholder="请输入其他主要设备（必填）"
            />
          </div>
        </el-form-item>
        <el-form-item label="信息化设备">
          <el-checkbox-group v-model="form.deviceInfo.infoDevices" :disabled="!editable">
            <el-checkbox value="网络设备">网络设备</el-checkbox>
            <el-checkbox value="信息安全设备">信息安全设备</el-checkbox>
            <el-checkbox value="终端设备">终端设备</el-checkbox>
            <el-checkbox value="存储设备">存储设备</el-checkbox>
            <el-checkbox value="机房辅助设备">机房辅助设备</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="材料附件" class="section-upload-item" required>
          <div class="section-upload-card">
            <div class="section-upload-header">
              <div class="section-upload-title">技改设备投入材料</div>
            </div>
            <div class="attachment-remark">
              <span>备注：</span>
              <span>(1) 提供2025投入的固定资产清单（Excel、盖章扫描PDF版各一份）；</span>
              <span>(2) 若2025之后无投入，或2025之后投入中没有数控设备，可从2025之前资产清单中选择数控设备提交（Excel、盖章扫描PDF版各一份）。</span>
            </div>
            <div class="upload-toolbar">
              <el-upload
                :http-request="(options) => uploadFile(options, ATTACHMENT_TYPES.DEVICE)"
                :accept="DEVICE_UPLOAD_ACCEPT"
                :show-file-list="false"
                :disabled="!editable"
              >
                <el-button type="primary" plain>选择文件上传</el-button>
              </el-upload>
            </div>
            <div class="uploaded-list">
              <div v-if="deviceAttachments.length === 0" class="empty-upload">暂无已上传文件</div>
              <div v-for="a in deviceAttachments" :key="a.id" class="uploaded-item">
                <el-tag>{{ a.originalFileName }}</el-tag>
                <el-button v-if="canPreviewAttachment(a)" type="primary" link @click="preview(a.id, a.originalFileName)">在线预览</el-button>
                <el-button type="success" link @click="download(a.id, a.originalFileName)">下载</el-button>
                <el-button v-if="editable" type="danger" link @click="removeAttachment(a.id)">删除</el-button>
              </div>
            </div>
          </div>
        </el-form-item>
      </section>

      <section class="form-section-card">
        <div class="form-section-header">数字化系统</div>
        <el-form-item label="系统投入" required>
          <el-checkbox-group v-model="form.digitalInfo.digitalSystems" :disabled="!editable">
            <el-checkbox v-for="s in digitalOptions" :key="s.id || s.optionName" :value="s.optionName">{{ s.optionName }}</el-checkbox>
          </el-checkbox-group>
          <el-input
            v-if="digitalOtherOption && form.digitalInfo.digitalSystems?.includes(digitalOtherOption)"
            v-model="form.digitalInfo.otherSystem"
            class="other-inline-input"
            :disabled="!editable"
            placeholder="请输入其他数字化系统（必填）"
          />
        </el-form-item>
        <el-form-item label="材料附件" class="section-upload-item" required>
          <div class="section-upload-card">
            <div class="section-upload-header">
              <div class="section-upload-title">数字化系统材料</div>
            </div>
            <div class="attachment-remark">备注：证明材料，请将对应数字化系统截图，加盖单位公章，扫描成PDF上传</div>
            <div class="upload-toolbar">
              <el-upload
                :http-request="(options) => uploadFile(options, ATTACHMENT_TYPES.DIGITAL)"
                :accept="PDF_UPLOAD_ACCEPT"
                :show-file-list="false"
                :disabled="!editable"
              >
                <el-button type="primary" plain>选择文件上传</el-button>
              </el-upload>
            </div>
            <div class="uploaded-list">
              <div v-if="digitalAttachments.length === 0" class="empty-upload">暂无已上传文件</div>
              <div v-for="a in digitalAttachments" :key="a.id" class="uploaded-item">
                <el-tag>{{ a.originalFileName }}</el-tag>
                <el-button v-if="canPreviewAttachment(a)" type="primary" link @click="preview(a.id, a.originalFileName)">在线预览</el-button>
                <el-button type="success" link @click="download(a.id, a.originalFileName)">下载</el-button>
                <el-button v-if="editable" type="danger" link @click="removeAttachment(a.id)">删除</el-button>
              </div>
            </div>
          </div>
        </el-form-item>
      </section>

      <section class="form-section-card">
        <div class="form-section-header">研发工具</div>
        <el-form-item label="工具投入" required>
          <el-checkbox-group v-model="form.rdToolInfo.rdTools" :disabled="!editable">
            <el-checkbox v-for="t in rdToolOptions" :key="t.id || t.optionName" :value="t.optionName">{{ t.optionName }}</el-checkbox>
          </el-checkbox-group>
          <el-input
            v-if="rdToolOtherOption && form.rdToolInfo.rdTools?.includes(rdToolOtherOption)"
            v-model="form.rdToolInfo.otherTool"
            class="other-inline-input"
            :disabled="!editable"
            placeholder="请输入其他研发设计软件（必填）"
          />
        </el-form-item>
        <el-form-item label="材料附件" class="section-upload-item" required>
          <div class="section-upload-card">
            <div class="section-upload-header">
              <div class="section-upload-title">研发工具材料</div>
            </div>
            <div class="attachment-remark">备注：证明材料，请将对应研发工具系统截图，加盖单位公章，扫描成PDF上传</div>
            <div class="upload-toolbar">
              <el-upload
                :http-request="(options) => uploadFile(options, ATTACHMENT_TYPES.RD_TOOL)"
                :accept="PDF_UPLOAD_ACCEPT"
                :show-file-list="false"
                :disabled="!editable"
              >
                <el-button type="primary" plain>选择文件上传</el-button>
              </el-upload>
            </div>
            <div class="uploaded-list">
              <div v-if="rdAttachments.length === 0" class="empty-upload">暂无已上传文件</div>
              <div v-for="a in rdAttachments" :key="a.id" class="uploaded-item">
                <el-tag>{{ a.originalFileName }}</el-tag>
                <el-button v-if="canPreviewAttachment(a)" type="primary" link @click="preview(a.id, a.originalFileName)">在线预览</el-button>
                <el-button type="success" link @click="download(a.id, a.originalFileName)">下载</el-button>
                <el-button v-if="editable" type="danger" link @click="removeAttachment(a.id)">删除</el-button>
              </div>
            </div>
          </div>
        </el-form-item>
      </section>

      <el-form-item v-if="showEnterpriseActions" class="action-row">
        <div class="action-bar">
          <div class="action-buttons">
            <el-button class="save-btn" :loading="saving" :disabled="!editable" @click="save">保存</el-button>
            <el-button class="submit-btn" :loading="submitting" :disabled="!editable" @click="submit">提交审批</el-button>
          </div>
          <el-tag class="status-chip" :type="statusTagType">当前状态：{{ statusLabel }}</el-tag>
        </div>
      </el-form-item>

      <el-form-item v-if="showAdminActions" class="action-row">
        <div class="action-bar">
          <div class="action-buttons">
            <el-button @click="cancelAdminEdit">取消编辑</el-button>
            <el-button v-if="showAdminSaveButton" class="save-btn" :loading="saving" @click="save">保存修改</el-button>
            <el-button class="submit-btn" :loading="submitting" @click="submit">提交修改</el-button>
          </div>
          <el-tag class="status-chip" :type="statusTagType">当前状态：{{ statusLabel }}</el-tag>
        </div>
      </el-form-item>

      <section v-if="showReviewSection" class="form-section-card approval-section-card">
        <div class="form-section-header">审批处理</div>
        <div v-if="showReadonlyReviewMeta" class="review-readonly-meta">
          处理结果：{{ reviewActionLabel }}<span class="meta-divider">|</span>处理时间：{{ reviewHandledAtText }}
        </div>
        <el-form-item label="审批意见" :required="canReviewInDetail">
          <el-input
            :model-value="reviewCommentText"
            @update:model-value="onApprovalCommentChange"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            :disabled="!canReviewInDetail"
            :placeholder="canReviewInDetail ? '请输入审批意见（必填）' : '审批意见'"
          />
        </el-form-item>
        <el-form-item v-if="canReviewInDetail" class="action-row">
          <div class="approval-action-buttons">
            <el-button class="approve-btn" :loading="approvingAction === 'approve'" @click="handleReview('approve')">通过</el-button>
            <el-button class="reject-btn" :loading="approvingAction === 'reject'" @click="handleReview('reject')">驳回</el-button>
          </div>
        </el-form-item>
      </section>
    </el-form>

    <el-dialog v-model="previewVisible" title="附件预览" width="80%" top="4vh" @closed="clearPreview">
      <div style="height:70vh; overflow:auto">
        <img v-if="previewType === 'image'" :src="previewUrl" style="max-width:100%" />
        <iframe v-else-if="previewType === 'iframe'" :src="previewUrl" style="width:100%; height:68vh; border:none" />
        <div v-else style="padding:16px">
          当前文件类型不支持浏览器内预览，请下载查看。
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const processOptions = ref([])
const equipmentOptions = ref([])
const equipmentOptionsByProcess = ref([])
const industrySpecialMode = ref(false)
const processEquipmentQueryMap = ref({})
const processLoading = ref(false)
const equipmentLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const formStatus = ref('DRAFT')
const submissionId = ref(null)
const attachments = ref([])
const dirty = ref(false)
const suppressDirty = ref(false)
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const previewVisible = ref(false)
const previewUrl = ref('')
const previewType = ref('iframe')
const approvalComment = ref('')
const approvingAction = ref('')
const adminEditMode = ref(false)
const reviewHandled = ref(false)
const reviewTaskDetail = ref(null)
const detailReviewActionLabel = ref('')
const detailReviewHandledAt = ref('')
const editableStatuses = ['DRAFT', 'RETURNED', 'REJECTED']
const isEnterpriseUser = computed(() => auth.roles.includes('ENTERPRISE_USER'))
const isApproverUser = computed(() => auth.roles.includes('APPROVER_ADMIN') || auth.roles.includes('SYS_ADMIN'))
const canAdminEditCurrent = computed(() =>
  isApproverUser.value &&
  isDetailView.value &&
  ['APPROVED', 'RETURNED', 'REJECTED', 'SUBMITTED', 'UNDER_REVIEW'].includes(formStatus.value)
)
const showAdminActions = computed(() =>
  isApproverUser.value &&
  isDetailView.value &&
  adminEditMode.value
)
const showAdminSaveButton = computed(() => !['APPROVED', 'RETURNED', 'REJECTED'].includes(formStatus.value))
const editable = computed(() =>
  (isEnterpriseUser.value && editableStatuses.includes(formStatus.value))
  || (isApproverUser.value && adminEditMode.value)
)
const showEnterpriseActions = computed(() => isEnterpriseUser.value)
const isDetailView = computed(() => Boolean(route.query.id || route.params.id))
const reviewTaskId = computed(() => {
  const raw = Array.isArray(route.query.taskId) ? route.query.taskId[0] : route.query.taskId
  const taskId = Number(raw)
  if (!Number.isFinite(taskId) || taskId <= 0) {
    return null
  }
  return taskId
})
const canReviewInDetail = computed(() =>
  isApproverUser.value &&
  !adminEditMode.value &&
  !reviewHandled.value &&
  reviewTaskId.value !== null &&
  ['SUBMITTED', 'UNDER_REVIEW'].includes(formStatus.value)
)
const canReadonlyReviewInDetail = computed(() =>
  isApproverUser.value &&
  reviewTaskId.value !== null &&
  reviewTaskDetail.value?.taskStatus === 'DONE' &&
  !canReviewInDetail.value
)
const canReadonlyReviewForApprover = computed(() =>
  isApproverUser.value &&
  !canReviewInDetail.value &&
  !canReadonlyReviewInDetail.value &&
  !!(detailReviewActionLabel.value || approvalComment.value || detailReviewHandledAt.value)
)
const canReadonlyReviewForEnterprise = computed(() =>
  isEnterpriseUser.value &&
  formStatus.value !== 'DRAFT'
)
const showReviewSection = computed(() =>
  canReviewInDetail.value ||
  canReadonlyReviewInDetail.value ||
  canReadonlyReviewForApprover.value ||
  canReadonlyReviewForEnterprise.value
)
const showReadonlyReviewMeta = computed(() =>
  canReadonlyReviewInDetail.value || canReadonlyReviewForApprover.value || canReadonlyReviewForEnterprise.value
)
const reviewActionLabel = computed(() => {
  if (canReadonlyReviewInDetail.value || canReviewInDetail.value) {
    const action = reviewTaskDetail.value?.action
    if (action === 'APPROVE') return '通过'
    if (action === 'REJECT') return '驳回'
    if (action === 'RETURN') return '退回修改'
  }
  if (detailReviewActionLabel.value) return detailReviewActionLabel.value
  if (formStatus.value === 'APPROVED') return '通过'
  if (formStatus.value === 'REJECTED') return '驳回'
  if (formStatus.value === 'RETURNED') return '退回修改'
  if (formStatus.value === 'SUBMITTED' || formStatus.value === 'UNDER_REVIEW') return '审批中'
  return '-'
})
const reviewHandledAtText = computed(() => {
  if (canReadonlyReviewInDetail.value || canReviewInDetail.value) {
    return reviewTaskDetail.value?.handledAt || '-'
  }
  return detailReviewHandledAt.value || '-'
})
const reviewCommentText = computed(() => {
  if (approvalComment.value) {
    return approvalComment.value
  }
  if (!canReviewInDetail.value && reviewActionLabel.value === '通过') {
    return '通过'
  }
  return ''
})
const OTHER_OPTION = '其他'
const statusLabelMap = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  UNDER_REVIEW: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  RETURNED: '退回修改'
}
const statusTagTypeMap = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  UNDER_REVIEW: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  RETURNED: 'warning'
}
const statusLabel = computed(() => statusLabelMap[formStatus.value] || formStatus.value || '-')
const statusTagType = computed(() => statusTagTypeMap[formStatus.value] || 'info')
const ATTACHMENT_TYPES = {
  DEVICE: 'DEVICE_PROOF',
  DIGITAL: 'DIGITAL_PROOF',
  RD_TOOL: 'RD_TOOL_PROOF',
  LEGACY: 'PROOF'
}
const DEVICE_UPLOAD_ACCEPT = '.xls,.xlsx,.pdf'
const PDF_UPLOAD_ACCEPT = '.pdf'
const DEVICE_ALLOWED_EXT = new Set(['xls', 'xlsx', 'pdf'])
const deviceAttachments = computed(() => (attachments.value || []).filter((a) =>
  [ATTACHMENT_TYPES.DEVICE, ATTACHMENT_TYPES.LEGACY].includes(a.attachmentType)
))
const digitalAttachments = computed(() => (attachments.value || []).filter((a) =>
  a.attachmentType === ATTACHMENT_TYPES.DIGITAL
))
const rdAttachments = computed(() => (attachments.value || []).filter((a) =>
  a.attachmentType === ATTACHMENT_TYPES.RD_TOOL
))

const digitalOptions = ref([])
const rdToolOptions = ref([])
const digitalOtherOption = ref('')
const rdToolOtherOption = ref('')

const form = reactive({
  basicInfo: {},
  deviceInfo: { selectedProcesses: [], selectedEquipments: [], infoDevices: [], otherProcess: '', otherEquipment: '', otherInfoDevice: '' },
  digitalInfo: { digitalSystems: [], otherSystem: '' },
  rdToolInfo: { rdTools: [], otherTool: '' }
})

const normalizeOptionRows = (rows) => {
  if (!Array.isArray(rows)) {
    return []
  }
  return rows
    .filter((item) => item && `${item.optionName || ''}`.trim())
    .map((item) => ({
      id: item.id ?? null,
      optionName: `${item.optionName}`.trim(),
      sortNo: Number.isFinite(Number(item.sortNo)) ? Number(item.sortNo) : null,
      otherOption: item.otherOption === true
    }))
}

const onApprovalCommentChange = (value) => {
  approvalComment.value = value
}

const resolveOtherOptionName = (rows) => {
  const other = (rows || []).find((item) => item.otherOption === true)
  return other?.optionName || ''
}

const loadSelectableOptions = async () => {
  const [digitalRes, rdRes] = await Promise.all([
    http.get('/submission/options/digital-systems'),
    http.get('/submission/options/rd-tools')
  ])
  digitalOptions.value = normalizeOptionRows(digitalRes.data)
  rdToolOptions.value = normalizeOptionRows(rdRes.data)
  digitalOtherOption.value = resolveOtherOptionName(digitalOptions.value)
  rdToolOtherOption.value = resolveOtherOptionName(rdToolOptions.value)
}

const loadCurrent = async () => {
  const detailId = route.query.id || route.params.id
  if (detailId) {
    const res = await http.get(`/submissions/${detailId}`)
    await fillForm(res.data)
    return
  }
  const res = await http.get('/submissions/current')
  await fillForm(res.data)
}

const loadReviewTaskDetail = async () => {
  if (!isApproverUser.value || reviewTaskId.value === null) {
    reviewTaskDetail.value = null
    return
  }
  const res = await http.get(`/approvals/${reviewTaskId.value}`)
  reviewTaskDetail.value = res.data || null
  if (reviewTaskDetail.value?.taskStatus === 'DONE') {
    approvalComment.value = reviewTaskDetail.value?.comment || ''
  }
}

const syncIndustryCodeByEnterpriseName = async () => {
  if (!isEnterpriseUser.value || !editable.value) {
    return
  }
  const enterpriseName = `${form.basicInfo.enterpriseName || ''}`.trim()
  if (!enterpriseName) {
    form.basicInfo.industryCode = ''
    form.basicInfo.industryName = ''
    return
  }
  const res = await http.get('/enterprise/industry-info', { params: { enterpriseName } })
  const industryInfo = res.data || {}
  const matchedCode = `${industryInfo.industryCode || ''}`.trim()
  const matchedName = `${industryInfo.industryName || ''}`.trim()
  form.basicInfo.industryCode = matchedCode
  form.basicInfo.industryName = matchedName
}

const syncIndustryNameByIndustryCode = async () => {
  const industryCode = `${form.basicInfo.industryCode || ''}`.trim()
  if (!industryCode) {
    form.basicInfo.industryName = ''
    return
  }
  const res = await http.get('/enterprise/industry-name', { params: { industryCode } })
  form.basicInfo.industryName = `${res.data || ''}`.trim()
}

const listEquals = (a = [], b = []) => {
  if (a.length !== b.length) {
    return false
  }
  return a.every((x, idx) => x === b[idx])
}

const fillForm = async (data) => {
  suppressDirty.value = true
  submissionId.value = data.submissionId
  formStatus.value = data.status
  detailReviewActionLabel.value = data.reviewActionLabel || ''
  detailReviewHandledAt.value = data.reviewHandledAt || ''
  approvalComment.value = data.reviewComment || ''
  form.basicInfo = data.basicInfo || {}
  form.deviceInfo = {
    selectedProcesses: data.deviceInfo?.selectedProcesses || [],
    selectedEquipments: data.deviceInfo?.selectedEquipments || [],
    infoDevices: data.deviceInfo?.infoDevices || [],
    otherProcess: data.deviceInfo?.otherProcess || '',
    otherEquipment: data.deviceInfo?.otherEquipment || '',
    otherInfoDevice: data.deviceInfo?.otherInfoDevice || ''
  }
  form.digitalInfo = {
    digitalSystems: data.digitalInfo?.digitalSystems || [],
    otherSystem: data.digitalInfo?.otherSystem || ''
  }
  form.rdToolInfo = {
    rdTools: data.rdToolInfo?.rdTools || [],
    otherTool: data.rdToolInfo?.otherTool || ''
  }
  attachments.value = data.attachments || []
  if (!`${form.basicInfo.industryCode || ''}`.trim()) {
    await syncIndustryCodeByEnterpriseName()
  }
  await loadProcesses()
  await loadEquipments()
  dirty.value = false
  suppressDirty.value = false
}

const appendOtherOption = (options) => {
  const values = Array.isArray(options) ? [...options] : []
  if (!values.includes(OTHER_OPTION)) {
    values.push(OTHER_OPTION)
  }
  return values
}

const normalizeTextOption = (value) => `${value || ''}`.trim()

const extractBaseProcessName = (processName) => {
  const normalized = normalizeTextOption(processName)
  if (!normalized) {
    return ''
  }
  const englishIdx = normalized.indexOf('(')
  const chineseIdx = normalized.indexOf('（')
  const cutIndexes = [englishIdx, chineseIdx].filter((idx) => idx >= 0)
  if (cutIndexes.length === 0) {
    return normalized
  }
  return normalized.slice(0, Math.min(...cutIndexes)).trim()
}

const buildProcessDisplayMeta = (rawProcesses) => {
  const normalizedProcesses = []
  const seen = new Set()
  for (const item of rawProcesses || []) {
    const normalized = normalizeTextOption(item)
    if (!normalized || seen.has(normalized)) {
      continue
    }
    seen.add(normalized)
    normalizedProcesses.push(normalized)
  }

  const exactSet = new Set(normalizedProcesses)
  const specificMap = new Map()
  normalizedProcesses.forEach((processName) => {
    const baseName = extractBaseProcessName(processName)
    if (!baseName || baseName === processName || !exactSet.has(baseName)) {
      return
    }
    const specifics = specificMap.get(baseName) || []
    specifics.push(processName)
    specificMap.set(baseName, specifics)
  })

  const hiddenBaseSet = new Set(specificMap.keys())
  const redirectMap = {}
  specificMap.forEach((specifics, baseName) => {
    if (specifics.length === 1) {
      redirectMap[baseName] = specifics[0]
    }
  })

  const displayProcesses = []
  const queryMap = {}
  const emitted = new Set()
  normalizedProcesses.forEach((processName) => {
    if (hiddenBaseSet.has(processName)) {
      const specifics = specificMap.get(processName) || []
      specifics.forEach((specificName) => {
        if (emitted.has(specificName)) {
          return
        }
        emitted.add(specificName)
        displayProcesses.push(specificName)
        queryMap[specificName] = [processName, specificName]
      })
      return
    }
    const baseName = extractBaseProcessName(processName)
    if (baseName && baseName !== processName && hiddenBaseSet.has(baseName)) {
      return
    }
    if (emitted.has(processName)) {
      return
    }
    emitted.add(processName)
    displayProcesses.push(processName)
    queryMap[processName] = [processName]
  })

  return {
    processes: displayProcesses,
    queryMap,
    redirectMap
  }
}

const mappingIndustryCode = () => {
  const rawCode = `${form.basicInfo.industryCode || ''}`.trim()
  if (!rawCode) {
    return ''
  }
  return rawCode.slice(0, 3)
}

const loadProcesses = async () => {
  const industryCode = mappingIndustryCode()
  if (!industryCode) {
    processOptions.value = []
    industrySpecialMode.value = false
    processEquipmentQueryMap.value = {}
    if (form.deviceInfo.selectedProcesses?.length) {
      form.deviceInfo.selectedProcesses = []
    }
    return
  }
  processLoading.value = true
  try {
    const res = await http.get('/industry/processes', { params: { industryCode } })
    const processData = res.data || {}
    industrySpecialMode.value = !!processData.specialMode
    const processMeta = buildProcessDisplayMeta(processData.processes || [])
    processEquipmentQueryMap.value = processMeta.queryMap
    processOptions.value = appendOtherOption(processMeta.processes)
    const filtered = []
    for (const processName of form.deviceInfo.selectedProcesses || []) {
      const mappedName = processMeta.redirectMap[processName] || processName
      if (processOptions.value.includes(mappedName) && !filtered.includes(mappedName)) {
        filtered.push(mappedName)
      }
    }
    if (!listEquals(filtered, form.deviceInfo.selectedProcesses || [])) {
      form.deviceInfo.selectedProcesses = filtered
    }
  } finally {
    processLoading.value = false
  }
}

const loadEquipments = async () => {
  const industryCode = mappingIndustryCode()
  if (!industryCode || (!industrySpecialMode.value && !form.deviceInfo.selectedProcesses?.length)) {
    equipmentOptions.value = []
    equipmentOptionsByProcess.value = []
    if (form.deviceInfo.selectedEquipments?.length) {
      form.deviceInfo.selectedEquipments = []
    }
    return
  }
  equipmentLoading.value = true
  const all = new Set()
  const groups = []
  try {
    if (industrySpecialMode.value) {
      const res = await http.get('/industry/equipments', { params: { industryCode } })
      const options = appendOtherOption(Array.from(new Set((res.data || []).filter((x) => `${x || ''}`.trim()))))
      equipmentOptions.value = options
      equipmentOptionsByProcess.value = []
      const filtered = (form.deviceInfo.selectedEquipments || []).filter((e) => options.includes(e))
      if (!listEquals(filtered, form.deviceInfo.selectedEquipments || [])) {
        form.deviceInfo.selectedEquipments = filtered
      }
      return
    }
    const selectedProcesses = (form.deviceInfo.selectedProcesses || []).filter((processName) => processName !== OTHER_OPTION)
    const groupResults = await Promise.all(selectedProcesses.map(async (processName) => {
      const queryProcessNames = processEquipmentQueryMap.value[processName] || [processName]
      const responses = await Promise.all(queryProcessNames.map((queryProcessName) =>
        http.get('/industry/equipments', { params: { industryCode, processName: queryProcessName } })
      ))
      const mergedOptions = []
      const seenOptions = new Set()
      responses.forEach((res) => {
        ;(res.data || []).forEach((option) => {
          const normalized = normalizeTextOption(option)
          if (!normalized || seenOptions.has(normalized)) {
            return
          }
          seenOptions.add(normalized)
          mergedOptions.push(normalized)
        })
      })
      return { processName, options: mergedOptions }
    }))
    for (const group of groupResults) {
      groups.push(group)
      group.options.forEach((x) => all.add(x))
    }
    equipmentOptionsByProcess.value = groups
    equipmentOptions.value = appendOtherOption([...all])
    const filtered = (form.deviceInfo.selectedEquipments || []).filter((e) =>
      equipmentOptions.value.includes(e)
    )
    if (!listEquals(filtered, form.deviceInfo.selectedEquipments || [])) {
      form.deviceInfo.selectedEquipments = filtered
    }
  } finally {
    equipmentLoading.value = false
  }
}

watch(
  () => form.basicInfo.industryCode,
  async (val, oldVal) => {
    const next = `${val || ''}`.trim()
    const prev = `${oldVal || ''}`.trim()
    if (next === prev) return
    await syncIndustryNameByIndustryCode()
    await loadProcesses()
    await loadEquipments()
  }
)

watch(
  () => form.deviceInfo.selectedProcesses,
  async () => {
    if (!form.deviceInfo.selectedProcesses?.includes(OTHER_OPTION)) {
      form.deviceInfo.otherProcess = ''
    }
    if (industrySpecialMode.value) {
      return
    }
    await loadEquipments()
  },
  { deep: true }
)

watch(
  () => form.deviceInfo.selectedEquipments,
  () => {
    if (!form.deviceInfo.selectedEquipments?.includes(OTHER_OPTION)) {
      form.deviceInfo.otherEquipment = ''
    }
  },
  { deep: true }
)

watch(
  () => form.digitalInfo.digitalSystems,
  () => {
    if (
      digitalOtherOption.value
      && !form.digitalInfo.digitalSystems?.includes(digitalOtherOption.value)
    ) {
      form.digitalInfo.otherSystem = ''
    }
  },
  { deep: true }
)

watch(
  () => form.rdToolInfo.rdTools,
  () => {
    if (
      rdToolOtherOption.value
      && !form.rdToolInfo.rdTools?.includes(rdToolOtherOption.value)
    ) {
      form.rdToolInfo.otherTool = ''
    }
  },
  { deep: true }
)

watch(
  () => route.query.taskId,
  async () => {
    reviewHandled.value = false
    reviewTaskDetail.value = null
    approvalComment.value = ''
    await loadReviewTaskDetail()
  }
)

watch(
  () => [route.query.mode, formStatus.value, route.params.id, route.query.id],
  () => {
    adminEditMode.value = route.query.mode === 'edit' && canAdminEditCurrent.value
  },
  { immediate: true }
)

watch(
  form,
  () => {
    if (!suppressDirty.value) {
      dirty.value = true
    }
  },
  { deep: true }
)

const validateOtherInputs = () => {
  if (form.deviceInfo.selectedProcesses?.includes(OTHER_OPTION) && !`${form.deviceInfo.otherProcess || ''}`.trim()) {
    ElMessage.warning('已勾选主要工序“其他”，请填写具体内容')
    return false
  }
  if (form.deviceInfo.selectedEquipments?.includes(OTHER_OPTION) && !`${form.deviceInfo.otherEquipment || ''}`.trim()) {
    ElMessage.warning('已勾选主要设备“其他”，请填写具体内容')
    return false
  }
  if (
    digitalOtherOption.value
    && form.digitalInfo.digitalSystems?.includes(digitalOtherOption.value)
    && !`${form.digitalInfo.otherSystem || ''}`.trim()
  ) {
    ElMessage.warning('已勾选数字化系统“其他”，请填写具体内容')
    return false
  }
  if (
    rdToolOtherOption.value
    && form.rdToolInfo.rdTools?.includes(rdToolOtherOption.value)
    && !`${form.rdToolInfo.otherTool || ''}`.trim()
  ) {
    ElMessage.warning('已勾选研发工具“其他研发设计类软件”，请填写具体内容')
    return false
  }
  return true
}

const validateRequiredBasicInfo = () => {
  const missing = []
  if (!`${form.basicInfo.enterpriseName || ''}`.trim()) {
    missing.push('企业名称')
  }
  if (!`${form.basicInfo.industryCode || ''}`.trim()) {
    missing.push('所属行业代码')
  }
  if (missing.length > 0) {
    ElMessage.warning(`请先填写必填项：${missing.join('、')}`)
    return false
  }
  return true
}

const validateRequiredAttachments = () => {
  const missing = []
  if (deviceAttachments.value.length === 0) {
    missing.push('技改设备投入材料')
  } else {
    const hasExcel = deviceAttachments.value.some((a) => isExcelAttachment(a))
    const hasPdf = deviceAttachments.value.some((a) => isPdfAttachment(a))
    if (!hasExcel || !hasPdf) {
      missing.push('技改设备投入材料（需至少1份Excel和1份PDF）')
    }
  }
  if (digitalAttachments.value.length === 0) {
    missing.push('数字化系统材料')
  } else if (!digitalAttachments.value.some((a) => isPdfAttachment(a))) {
    missing.push('数字化系统材料（仅支持PDF）')
  }
  if (rdAttachments.value.length === 0) {
    missing.push('研发工具材料')
  } else if (!rdAttachments.value.some((a) => isPdfAttachment(a))) {
    missing.push('研发工具材料（仅支持PDF）')
  }
  if (missing.length > 0) {
    ElMessage.warning(`请先上传必填材料：${missing.join('、')}`)
    return false
  }
  return true
}

const validateRequiredSelections = () => {
  const missing = []
  if (!form.deviceInfo.selectedProcesses?.length) {
    missing.push('主要工序')
  }
  if (!form.deviceInfo.selectedEquipments?.length) {
    missing.push('主要设备')
  }
  if (!form.digitalInfo.digitalSystems?.length) {
    missing.push('系统投入')
  }
  if (!form.rdToolInfo.rdTools?.length) {
    missing.push('工具投入')
  }
  if (missing.length > 0) {
    ElMessage.warning(`请先填写必填项：${missing.join('、')}`)
    return false
  }
  return true
}

const buildSubmissionPayload = () => ({
  submissionId: submissionId.value,
  reportYear: new Date().getFullYear(),
  basicInfo: form.basicInfo,
  deviceInfo: form.deviceInfo,
  digitalInfo: form.digitalInfo,
  rdToolInfo: form.rdToolInfo
})

const startAdminEdit = () => {
  if (!canAdminEditCurrent.value) {
    return
  }
  router.push({
    path: route.path,
    query: {
      ...route.query,
      mode: 'edit'
    }
  })
}

const cancelAdminEdit = async () => {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('已修改当前填报信息，确定放弃本次编辑吗？', '取消编辑', {
        type: 'warning',
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑'
      })
    } catch {
      return
    }
  }
  const nextQuery = { ...route.query }
  delete nextQuery.mode
  await router.replace({
    path: route.path,
    query: nextQuery
  })
  adminEditMode.value = false
  await loadCurrent()
}

const save = async () => {
  if (!validateRequiredBasicInfo()) {
    return false
  }
  if (!validateOtherInputs()) {
    return false
  }
  saving.value = true
  try {
    const payload = buildSubmissionPayload()
    const res = isApproverUser.value && adminEditMode.value
      ? await http.post(`/approvals/submissions/${submissionId.value}/save-edit`, payload)
      : await http.post('/submissions/save', payload)
    await fillForm(res.data)
    ElMessage.success(isApproverUser.value && adminEditMode.value ? '修改已保存' : '保存成功')
    return true
  } catch (e) {
    return false
  } finally {
    saving.value = false
  }
}

const submit = async () => {
  if (isApproverUser.value && adminEditMode.value) {
    submitting.value = true
    try {
      if (!validateRequiredBasicInfo()) {
        return
      }
      if (!validateOtherInputs()) {
        return
      }
      if (!validateRequiredSelections()) {
        return
      }
      if (!validateRequiredAttachments()) {
        return
      }
      if (!submissionId.value || dirty.value) {
        const saved = await save()
        if (!saved) {
          ElMessage.warning('自动保存失败，请检查后重试')
          return
        }
      }
      if (!submissionId.value) {
        ElMessage.warning('保存后未找到填报单，请重试')
        return
      }
      await ElMessageBox.confirm('您已修改该企业的填报信息，确定提交？', '提交修改', {
        type: 'warning',
        confirmButtonText: '确定提交',
        cancelButtonText: '取消'
      })
      const res = await http.post(`/approvals/submissions/${submissionId.value}/submit-edit`)
      const nextQuery = { ...route.query }
      delete nextQuery.mode
      await router.replace({
        path: route.path,
        query: nextQuery
      })
      adminEditMode.value = false
      await fillForm(res.data)
      ElMessage.success('修改已提交，当前状态保持已通过')
      return
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        throw error
      }
      return
    } finally {
      submitting.value = false
    }
  }
  if (!editable.value) {
    ElMessage.warning('当前状态不可编辑，无法再次提交')
    return
  }
  submitting.value = true
  try {
    if (!validateRequiredBasicInfo()) {
      return
    }
    if (!validateOtherInputs()) {
      return
    }
    if (!validateRequiredSelections()) {
      return
    }
    if (!validateRequiredAttachments()) {
      return
    }
    if (!submissionId.value || dirty.value) {
      const saved = await save()
      if (!saved) {
        ElMessage.warning('自动保存失败，请检查后重试')
        return
      }
    }
    if (!submissionId.value) {
      ElMessage.warning('保存后未生成填报单，请重试')
      return
    }
    await http.post('/submissions/submit', { submissionId: submissionId.value })
    ElMessage.success('已提交审批')
    await loadCurrent()
  } finally {
    submitting.value = false
  }
}

const handleReview = async (action) => {
  if (!canReviewInDetail.value || reviewTaskId.value === null) {
    ElMessage.warning('当前审批任务不可处理')
    return
  }
  const comment = approvalComment.value?.trim()
  if (!comment) {
    ElMessage.warning('请先填写审批意见')
    return
  }
  approvingAction.value = action
  try {
    await http.post(`/approvals/${reviewTaskId.value}/${action}`, { comment })
    reviewHandled.value = true
    ElMessage.success(action === 'approve' ? '审批通过' : '已驳回')
    await loadReviewTaskDetail()
    await loadCurrent()
  } finally {
    approvingAction.value = ''
  }
}

const uploadFile = async (options, attachmentType) => {
  if (!editable.value) {
    ElMessage.warning('当前状态不可编辑，无法上传附件')
    return
  }
  if (
    [ATTACHMENT_TYPES.DEVICE, ATTACHMENT_TYPES.LEGACY].includes(attachmentType)
    && !isAllowedDeviceFile(options?.file)
  ) {
    ElMessage.warning('技改设备投入材料仅支持Excel(.xls/.xlsx)和PDF(.pdf)格式')
    return
  }
  if (attachmentType === ATTACHMENT_TYPES.DIGITAL && !isAllowedPdfFile(options?.file)) {
    ElMessage.warning('数字化系统材料仅支持PDF(.pdf)格式')
    return
  }
  if (attachmentType === ATTACHMENT_TYPES.RD_TOOL && !isAllowedPdfFile(options?.file)) {
    ElMessage.warning('研发工具材料仅支持PDF(.pdf)格式')
    return
  }
  if (!submissionId.value || dirty.value) {
    const saved = await save()
    if (!saved || !submissionId.value) {
      ElMessage.warning('自动保存失败，请先保存后再上传')
      return
    }
  }
  const fd = new FormData()
  fd.append('submissionId', submissionId.value)
  fd.append('attachmentType', attachmentType)
  fd.append('file', options.file)
  await http.post('/files/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  await loadCurrent()
  ElMessage.success('上传成功')
}

const extOfFileName = (name = '') => {
  const idx = name.lastIndexOf('.')
  if (idx < 0 || idx === name.length - 1) return ''
  return name.slice(idx + 1).toLowerCase()
}

const isPdfAttachment = (a) => {
  const ext = extOfFileName(a?.originalFileName || '')
  if (ext === 'pdf') return true
  const ct = `${a?.contentType || ''}`.toLowerCase()
  return ct.includes('pdf')
}

const isExcelAttachment = (a) => {
  const ext = extOfFileName(a?.originalFileName || '')
  if (ext === 'xls' || ext === 'xlsx') return true
  const ct = `${a?.contentType || ''}`.toLowerCase()
  return ct.includes('spreadsheet') || ct.includes('ms-excel') || ct.includes('officedocument.spreadsheetml')
}

const isAllowedDeviceFile = (file) => {
  const ext = extOfFileName(file?.name || '')
  return DEVICE_ALLOWED_EXT.has(ext)
}

const isAllowedPdfFile = (file) => {
  const ext = extOfFileName(file?.name || '')
  return ext === 'pdf'
}

const canPreviewAttachment = (attachment) => !isExcelAttachment(attachment)

const removeAttachment = async (id) => {
  try {
    await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除附件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  if (dirty.value || !submissionId.value) {
    const saved = await save()
    if (!saved || !submissionId.value) {
      ElMessage.warning('自动保存失败，请先保存后再删除附件')
      return
    }
  }
  await http.delete(`/files/${id}`)
  await loadCurrent()
  ElMessage.success('附件已删除')
}

const download = async (id, fileName) => {
  const res = await http.get(`/files/${id}/download`, { responseType: 'blob' })
  const blob = new Blob([res], { type: 'application/octet-stream' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName || '附件'
  a.click()
  window.URL.revokeObjectURL(url)
}

const preview = async (id) => {
  const blob = await http.get(`/files/${id}/preview`, { responseType: 'blob' })
  const contentType = blob.type || ''
  if (contentType.startsWith('image/')) {
    previewType.value = 'image'
  } else if (
    contentType.includes('pdf') ||
    contentType.startsWith('text/') ||
    contentType.includes('html')
  ) {
    previewType.value = 'iframe'
  } else {
    previewType.value = 'unsupported'
  }
  previewUrl.value = window.URL.createObjectURL(blob)
  previewVisible.value = true
}

const clearPreview = () => {
  if (previewUrl.value) {
    window.URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
}

onMounted(async () => {
  await loadSelectableOptions()
  await loadCurrent()
  await loadReviewTaskDetail()
})
</script>

<style scoped>
.sectioned-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

:deep(.readonly-appearance .el-checkbox.is-disabled) {
  opacity: 1;
}

:deep(.readonly-appearance .el-checkbox.is-disabled .el-checkbox__label) {
  color: var(--el-text-color-regular);
}

:deep(.readonly-appearance .el-checkbox__input.is-disabled .el-checkbox__inner) {
  background: #fff;
  border-color: var(--el-border-color);
}

:deep(.readonly-appearance .el-checkbox__input.is-disabled.is-checked .el-checkbox__inner) {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

:deep(.readonly-appearance .el-checkbox__input.is-disabled.is-checked .el-checkbox__inner::after) {
  border-color: #fff;
}

:deep(.readonly-appearance .el-input.is-disabled .el-input__wrapper) {
  background: #fff;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

:deep(.readonly-appearance .el-input.is-disabled .el-input__inner) {
  color: var(--el-text-color-regular);
  -webkit-text-fill-color: var(--el-text-color-regular);
}

:deep(.readonly-appearance .el-upload .el-button.is-disabled) {
  color: var(--el-color-primary);
  border-color: #a0cfff;
  background: #ecf5ff;
  opacity: 1;
}

.form-section-card {
  border: 1px solid #dbe5f2;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(16, 61, 127, 0.06);
  padding: 14px 16px 6px;
}

.form-section-header {
  position: relative;
  margin-bottom: 12px;
  padding-left: 10px;
  line-height: 22px;
  font-size: 18px;
  font-weight: 700;
  color: #1f4f8e;
}

.form-section-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 1px;
  width: 4px;
  height: 20px;
  background: linear-gradient(180deg, #2f74ce 0%, #1f56a8 100%);
}

:deep(.form-section-card .el-form-item) {
  margin-bottom: 14px;
}

:deep(.form-section-card .el-form-item:last-child) {
  margin-bottom: 10px;
}

.process-box {
  width: 100%;
}

.equipment-by-process {
  display: flex;
  flex-direction: column;
  border: 1px solid #d9e3f1;
  background: #fff;
  border-radius: 2px;
  overflow: hidden;
}

.equipment-matrix-header {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  background: #f4f8ff;
  border-bottom: 1px solid #e2ebf7;
}

.equipment-matrix-col {
  padding: 9px 12px;
  font-size: 13px;
  font-weight: 700;
  color: #355880;
}

.equipment-matrix-col:first-child {
  border-right: 1px solid #e2ebf7;
}

.equipment-process-row {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  border-bottom: 1px solid #edf2fa;
}

.equipment-process-row:last-child {
  border-bottom: none;
}

.equipment-process-label {
  min-height: 44px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #274f7f;
  line-height: 1.5;
  border-right: 1px solid #edf2fa;
  background: #fbfdff;
  word-break: break-word;
}

.equipment-process-content {
  min-height: 44px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
}

.equipment-process-content .process-check-group {
  width: 100%;
}

.equipment-process-row.other-row .equipment-process-label {
  color: #6a7483;
}

.process-check-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  min-height: 34px;
}

.inline-hint {
  min-height: 34px;
  display: flex;
  align-items: center;
}

.other-inline-input {
  width: 420px;
  max-width: 100%;
  margin-top: 8px;
}

.section-upload-item {
  margin-top: 4px;
}

.section-upload-card {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #d7e2f0;
  background: #f7faff;
}

.section-upload-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.section-upload-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f4f8e;
}

.attachment-remark {
  margin-bottom: 10px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.6;
  color: #84580f;
  background: #fff9ef;
  border: 1px solid #f2d8a8;
  border-left: 3px solid #e5b45b;
}

.attachment-remark span {
  display: block;
}

.upload-toolbar {
  margin-bottom: 8px;
}

.uploaded-list {
  margin-top: 2px;
}

.empty-upload {
  color: #8a95a3;
  font-size: 13px;
  line-height: 28px;
}

.uploaded-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px 8px;
  margin-bottom: 4px;
}

.uploaded-item :deep(.el-tag) {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-row {
  margin-top: 6px;
  margin-bottom: 0;
}

.action-bar {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 14px;
}

.approval-section-card {
  padding-bottom: 12px;
}

.review-readonly-meta {
  margin: 0 0 10px;
  padding: 8px 10px;
  font-size: 13px;
  color: #4d5f7a;
  background: #f5f8fe;
  border: 1px solid #d8e4f5;
}

.meta-divider {
  margin: 0 8px;
  color: #9aacbf;
}

.approval-action-buttons {
  display: flex;
  align-items: center;
  gap: 14px;
}

:deep(.approve-btn.el-button),
:deep(.reject-btn.el-button) {
  min-width: 118px;
  height: 42px;
  padding: 0 20px;
  border-radius: 0;
  font-size: 16px;
  font-weight: 700;
  box-shadow: none;
  background-image: none;
}

:deep(.approve-btn.el-button) {
  border: 1px solid #2d72cb;
  background: #3988e8;
  color: #fff;
}

:deep(.approve-btn.el-button:hover) {
  border-color: #245fae;
  background: #2f79d1;
  color: #fff;
}

:deep(.reject-btn.el-button) {
  border: 1px solid #cf4040;
  background: #e75151;
  color: #fff;
}

:deep(.reject-btn.el-button:hover) {
  border-color: #b93838;
  background: #d94747;
  color: #fff;
}

:deep(.save-btn.el-button),
:deep(.submit-btn.el-button) {
  min-width: 118px;
  height: 42px;
  padding: 0 20px;
  border-radius: 0;
  font-size: 16px;
  font-weight: 700;
  box-shadow: none;
  background-image: none;
}

:deep(.save-btn.el-button) {
  border: 1px solid #d07a14;
  background: #f29a2e;
  color: #fff;
}

:deep(.save-btn.el-button:hover) {
  border-color: #be6c0d;
  background: #e88d1f;
  color: #fff;
}

:deep(.submit-btn.el-button) {
  border: 1px solid #2d72cb;
  background: #3988e8;
  color: #fff;
}

:deep(.submit-btn.el-button:hover) {
  border-color: #245fae;
  background: #2f79d1;
  color: #fff;
}

:deep(.save-btn.el-button.is-disabled),
:deep(.submit-btn.el-button.is-disabled) {
  opacity: 0.65;
  color: #fff;
}

.status-chip {
  height: 36px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .section-upload-card {
    padding: 10px 12px;
  }

  .form-section-card {
    padding: 12px 12px 4px;
  }

  .form-section-header {
    font-size: 17px;
    margin-bottom: 10px;
  }
}

@media (max-width: 900px) {
  .equipment-matrix-header {
    display: none;
  }

  .equipment-process-row {
    grid-template-columns: 1fr;
  }

  .equipment-process-label {
    border-right: none;
    border-bottom: 1px solid #edf2fa;
  }
}
</style>
