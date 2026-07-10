<template>
  <div class="layout-root">
    <header class="header-shell">
      <div class="brand-row">
        <div class="brand-left">
          <div class="brand-copy">
            <div class="brand-title">新型技改城市试点平台</div>
          </div>
        </div>

        <div class="header-actions">
          <nav class="top-nav-inline">
            <a
              v-for="m in topMenus"
              :key="m.path"
              href="#"
              class="top-nav-item"
              :class="{ active: isActive(m.path) }"
              @click.prevent="go(m.path)"
            >
              {{ m.name }}
            </a>
          </nav>

          <div class="brand-right">
            <button
              v-if="isEnterpriseUser"
              type="button"
              class="user-name user-name-button"
              @click="openEnterpriseDialog"
            >
              {{ auth.userInfo?.displayName }}
            </button>
            <span v-else class="user-name">{{ auth.userInfo?.displayName }}</span>
            <el-button class="logout-btn" @click="logout">退出</el-button>
          </div>
        </div>
      </div>
    </header>

    <div class="main-wrap">
      <main class="content">
        <router-view />
      </main>
    </div>

    <el-dialog
      v-model="enterpriseDialogVisible"
      title="企业信息"
      width="min(640px, 92vw)"
      @closed="resetEnterpriseDialog"
    >
      <el-skeleton v-if="enterpriseLoading" :rows="5" animated />
      <el-form
        v-else
        ref="enterpriseFormRef"
        :model="enterpriseForm"
        :rules="enterpriseRules"
        label-width="140px"
        class="enterprise-profile-form"
      >
        <el-form-item label="企业名称">
          <el-input :model-value="enterpriseProfile.enterpriseName || ''" disabled />
        </el-form-item>
        <el-form-item label="统一信用代码">
          <el-input :model-value="enterpriseProfile.creditCode || ''" disabled />
        </el-form-item>
        <el-form-item label="法定代表人">
          <el-input :model-value="enterpriseProfile.legalPerson || ''" disabled />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input :model-value="enterpriseProfile.contactPhone || ''" disabled />
        </el-form-item>
        <el-form-item label="推送消息联系人" prop="contactName">
          <el-input v-model.trim="enterpriseForm.contactName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="联系人身份证号" prop="contactCertNo">
          <el-input
            :model-value="contactCertNoDisplayValue"
            @focus="contactCertNoEditing = true"
            @blur="contactCertNoEditing = false"
            @update:model-value="onContactCertNoInput"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enterpriseDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="enterpriseSaving"
          :disabled="enterpriseLoading"
          @click="saveEnterpriseContact"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import http from '../api/http'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const topMenus = computed(() => auth.menus || [])
const isEnterpriseUser = computed(() => auth.roles.includes('ENTERPRISE_USER'))
const enterpriseDialogVisible = ref(false)
const enterpriseLoading = ref(false)
const enterpriseSaving = ref(false)
const enterpriseFormRef = ref(null)
const enterpriseProfile = ref({})
const contactCertNoEditing = ref(false)
const enterpriseForm = reactive({
  contactName: '',
  contactCertNo: ''
})
const validateContactCertNoRequired = (_rule, value, callback) => {
  if (!`${value || ''}`.trim()) {
    callback(new Error('请输入联系人身份证号'))
    return
  }
  callback()
}
const enterpriseRules = {
  contactName: [{ required: true, message: '请输入推送消息联系人', trigger: 'blur' }],
  contactCertNo: [{ required: true, validator: validateContactCertNoRequired, trigger: 'blur' }]
}
const contactCertNoDisplayValue = computed(() => {
  if (contactCertNoEditing.value) {
    return enterpriseForm.contactCertNo
  }
  return maskContactCertNo(enterpriseForm.contactCertNo)
})

const go = (path) => {
  router.push(path)
}

const isActive = (path) => route.path === path

const loadEnterpriseProfile = async () => {
  enterpriseLoading.value = true
  try {
    const res = await http.get('/enterprise/profile/current')
    enterpriseProfile.value = res.data || {}
    enterpriseForm.contactName = enterpriseProfile.value.contactName || ''
    enterpriseForm.contactCertNo = enterpriseProfile.value.contactCertNo || ''
  } finally {
    enterpriseLoading.value = false
  }
}

const openEnterpriseDialog = async () => {
  enterpriseDialogVisible.value = true
  await loadEnterpriseProfile()
}

const resetEnterpriseDialog = () => {
  contactCertNoEditing.value = false
  enterpriseFormRef.value?.clearValidate()
}

const maskContactCertNo = (value) => {
  const certNo = `${value || ''}`
  if (certNo.length <= 10) {
    return certNo
  }
  return `${certNo.slice(0, 6)}********${certNo.slice(-4)}`
}

const onContactCertNoInput = (value) => {
  enterpriseForm.contactCertNo = `${value || ''}`
}

const saveEnterpriseContact = async () => {
  try {
    await enterpriseFormRef.value?.validate()
  } catch {
    return
  }
  enterpriseSaving.value = true
  try {
    const res = await http.put('/enterprise/profile/current/contact', {
      contactName: enterpriseForm.contactName,
      contactCertNo: enterpriseForm.contactCertNo
    })
    enterpriseProfile.value = res.data || {}
    enterpriseForm.contactName = enterpriseProfile.value.contactName || ''
    enterpriseForm.contactCertNo = enterpriseProfile.value.contactCertNo || ''
    ElMessage.success('保存成功')
    enterpriseDialogVisible.value = false
  } finally {
    enterpriseSaving.value = false
  }
}

const logout = () => {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-root {
  min-height: 100vh;
  background: linear-gradient(180deg, #eff4fb 0%, #f6f8fc 40%, #f3f6fb 100%);
}

.header-shell {
  position: sticky;
  top: 0;
  z-index: 30;
  box-shadow: 0 6px 18px rgba(22, 47, 91, 0.12);
}

.brand-row {
  height: 76px;
  padding: 0 12px 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(90deg, #e8f2ff 0%, #f6efe6 55%, #fde7c8 100%);
  border-bottom: 1px solid #d9e2f1;
}

.brand-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-logo {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: linear-gradient(145deg, #ff8f1f 0%, #f36a00 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 20px;
  box-shadow: 0 6px 12px rgba(220, 106, 0, 0.28);
}

.brand-title {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 1px;
  color: #f18100;
}

.brand-sub {
  margin-top: 2px;
  color: #486286;
  font-size: 13px;
}

.header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.brand-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  max-width: 220px;
  height: 36px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  border-radius: 4px;
  border: 1px solid #f1c689;
  background: #fff8ef;
  color: #9e5d0b;
  font-weight: 700;
  font-size: 16px;
}

.user-name-button {
  appearance: none;
  cursor: pointer;
  font-family: inherit;
}

.user-name-button:hover {
  border-color: #e8780a;
  background: #fff1de;
  color: #8f4f05;
}

.top-nav-inline {
  height: 76px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  overflow-x: auto;
  white-space: nowrap;
}

.top-nav-item {
  height: 100%;
  min-width: 110px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  text-decoration: none;
  background: linear-gradient(180deg, #f5a639 0%, #f2941f 100%);
  border-left: 1px solid rgba(255, 255, 255, 0.16);
  transition: background-color .2s ease;
}

.top-nav-item:hover {
  background: linear-gradient(180deg, #f7b14d 0%, #f49f2c 100%);
}

.top-nav-item.active {
  background: linear-gradient(180deg, #f07e10 0%, #ea7408 100%);
}

:deep(.logout-btn.el-button) {
  height: 36px;
  padding: 0 14px;
  border-radius: 4px;
  border: 1px solid #e8780a;
  background: linear-gradient(180deg, #f5a639 0%, #f2941f 100%);
  color: #fff;
  font-weight: 700;
}

:deep(.logout-btn.el-button:hover) {
  border-color: #e8780a;
  background: linear-gradient(180deg, #f7b14d 0%, #f49f2c 100%);
  color: #fff;
}

.main-wrap {
  padding: 14px;
  min-height: calc(100vh - 76px);
}

.content {
  flex: 1;
  min-width: 0;
}

.enterprise-profile-form {
  padding-right: 8px;
}

@media (max-width: 1100px) {
  .brand-title {
    font-size: 20px;
  }

  .brand-sub {
    display: none;
  }

  .top-nav-item {
    min-width: 94px;
    font-size: 14px;
    padding: 0 14px;
  }

  .user-name {
    font-size: 14px;
    height: 32px;
  }

  :deep(.logout-btn.el-button) {
    height: 32px;
    padding: 0 12px;
  }
}

@media (max-width: 900px) {
  .brand-row {
    height: 64px;
    padding: 0 12px;
  }

  .brand-title {
    font-size: 18px;
  }

  .top-nav-inline { height: 64px; }

  .main-wrap {
    padding: 10px;
    min-height: calc(100vh - 64px);
  }
}
</style>
