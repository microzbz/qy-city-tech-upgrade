import { defineStore } from 'pinia'
import http from '../api/http'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('ctu_token') || '',
    userInfo: null,
    roles: [],
    menus: []
  }),
  getters: {
    isLogin: (state) => !!state.token
  },
  actions: {
    async login(payload) {
      const res = await http.post('/auth/login', payload)
      this.token = res.data.accessToken
      localStorage.setItem('ctu_token', this.token)
      await this.fetchMe()
    },
    async ssoLogin(payload) {
      const res = await http.post('/auth/sso-login', payload)
      this.token = res.data.accessToken
      localStorage.setItem('ctu_token', this.token)
      await this.fetchMe()
    },
    async fetchMe() {
      if (!this.token) return
      const res = await http.get('/auth/me')
      this.userInfo = res.data.userInfo
      this.roles = res.data.roles || []
      this.menus = res.data.menus || []
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.menus = []
      localStorage.removeItem('ctu_token')
    }
  }
})
