import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

const isSsoPage = () => location.pathname === '/sso'

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('ctu_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const payload = res.data
    if (payload && typeof payload.code !== 'undefined' && payload.code !== 0) {
      if (!isSsoPage()) {
        ElMessage.error(payload.message || '请求失败')
      }
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return payload
  },
  (err) => {
    const status = err?.response?.status
    if (status === 401) {
      localStorage.removeItem('ctu_token')
      if (location.pathname !== '/login' && location.pathname !== '/sso') {
        location.href = '/login'
      }
    }
    if (!isSsoPage()) {
      ElMessage.error(err?.response?.data?.message || err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default http
