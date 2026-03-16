<template>
  <pre v-if="errorMessage" class="error-text">{{ errorMessage }}</pre>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const errorMessage = ref('')

const resolveLoginTarget = () => {
  if (auth.roles.includes('ENTERPRISE_USER')) {
    return '/enterprise/my-submissions'
  }
  const firstMenuPath = (auth.menus || []).find((m) => m?.path)?.path
  return firstMenuPath || '/common/notices'
}

const readEcspCode = () => {
  const hash = window.location.hash || ''
  if (hash.startsWith('#?')) {
    return new URLSearchParams(hash.slice(2)).get('ecspcode')
  }
  const hashQueryIdx = hash.indexOf('?')
  if (hashQueryIdx >= 0) {
    return new URLSearchParams(hash.slice(hashQueryIdx + 1)).get('ecspcode')
  }
  return new URLSearchParams(window.location.search || '').get('ecspcode')
}

const mask = (text) => {
  if (!text) return '***'
  if (text.length <= 8) return `${text[0]}***${text[text.length - 1]}`
  return `${text.slice(0, 4)}***${text.slice(-4)}`
}

onMounted(async () => {
  console.info('[SSO][FE] callback mounted', {
    href: window.location.href,
    hash: window.location.hash,
    search: window.location.search
  })
  if (auth.isLogin) {
    try {
      if (!auth.userInfo) {
        console.info('[SSO][FE] existing token detected, validating current session')
        await auth.fetchMe()
      }
      if (auth.userInfo) {
        const target = resolveLoginTarget()
        console.info('[SSO][FE] already logged in, skipping sso', {
          userId: auth.userInfo?.userId,
          username: auth.userInfo?.username,
          roles: auth.roles,
          target
        })
        router.replace(target)
        return
      }
    } catch (err) {
      console.warn('[SSO][FE] existing session validation failed, continue sso', {
        message: err?.response?.data?.message || err?.message
      })
    }
  }
  const ecspcode = readEcspCode()
  console.info('[SSO][FE] ecspcode extracted', {
    exists: !!ecspcode,
    len: ecspcode ? ecspcode.length : 0,
    masked: mask(ecspcode || '')
  })
  if (!ecspcode) {
    errorMessage.value = '未获取到ecspcode，无法完成单点登录'
    console.error('[SSO][FE] missing ecspcode')
    return
  }
  try {
    console.info('[SSO][FE] calling /api/auth/sso-login')
    await auth.ssoLogin({ token: ecspcode })
    console.info('[SSO][FE] sso-login success', {
      userId: auth.userInfo?.userId,
      username: auth.userInfo?.username,
      roles: auth.roles
    })
    const target = resolveLoginTarget()
    console.info('[SSO][FE] redirecting', { target })
    router.replace(target)
  } catch (err) {
    errorMessage.value = err?.response?.data?.message || err?.message || '单点登录失败，请重试'
    console.error('[SSO][FE] sso-login failed', {
      message: errorMessage.value,
      status: err?.response?.status,
      data: err?.response?.data
    })
  }
})
</script>

<style scoped>
.error-text {
  margin: 16px;
  color: #d40000;
  white-space: pre-wrap;
  font-size: 14px;
}
</style>
