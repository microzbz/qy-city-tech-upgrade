<template>
  <div class="login-page">
    <div class="login-shell">
      <div class="login-left">
        <h1>新型技改城市平台</h1>
        <p>企业技术改造填报系统</p>
      </div>
      <div class="login-card">
        <h2>账号登录</h2>
        <el-form :model="form" @keyup.enter="onLogin">
          <el-form-item>
            <el-input v-model="form.username" placeholder="用户名" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" show-password placeholder="密码" />
          </el-form-item>
          <el-button type="primary" class="full-btn" :loading="loading" @click="onLogin">登录</el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()

const resolveLoginTarget = () => {
  if (auth.roles.includes('ENTERPRISE_USER')) {
    return '/enterprise/my-submissions'
  }
  const firstMenuPath = (auth.menus || []).find((m) => m?.path)?.path
  return firstMenuPath || '/common/notices'
}

const onLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form)
    router.push(resolveLoginTarget())
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(90deg, #e8f2ff 0%, #f6efe6 55%, #fde7c8 100%);
}

.login-shell {
  width: min(720px, 94vw);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 30px;
}

.login-left {
  color: #f18100;
  width: 100%;
  text-align: center;
}

.login-left h1 {
  font-size: 42px;
  margin: 0 0 20px;
}

.login-left p {
  margin: 0 0 20px;
  font-size: 18px;
  color: #486286;
}

.login-card {
  width: min(460px, 100%);
  background: #fff;
  border-radius: 14px;
  padding: 36px;
  box-shadow: 0 16px 40px rgba(220, 106, 0, 0.18);
}

.login-card h2 {
  margin: 0 0 24px;
  color: #174080;
}

.full-btn {
  width: 100%;
  border: 1px solid #e8780a;
  background: linear-gradient(180deg, #f5a639 0%, #f2941f 100%);
  color: #fff;
}

.full-btn:hover,
.full-btn:focus {
  border-color: #e8780a;
  background: linear-gradient(180deg, #f7b14d 0%, #f49f2c 100%);
  color: #fff;
}

@media (max-width: 900px) {
  .login-shell {
    width: min(560px, 94vw);
    gap: 20px;
  }

  .login-left h1 {
    font-size: 36px;
  }

  .login-card {
    width: 100%;
    margin: 0;
  }
}
</style>
