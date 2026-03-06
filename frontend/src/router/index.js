import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/sso', component: () => import('../views/SsoCallbackView.vue') },
  {
    path: '/',
    component: () => import('../views/LayoutView.vue'),
    children: [
      { path: '', redirect: '/enterprise/submission' },
      { path: '/enterprise/submission', component: () => import('../views/EnterpriseSubmissionView.vue'), meta: { roles: ['ENTERPRISE_USER'] } },
      { path: '/enterprise/my-submissions', component: () => import('../views/MySubmissionsView.vue'), meta: { roles: ['ENTERPRISE_USER'] } },
      { path: '/approvals/submission-view/:id', component: () => import('../views/EnterpriseSubmissionView.vue'), meta: { roles: ['APPROVER_ADMIN', 'SYS_ADMIN'] } },
      { path: '/approvals/todo', component: () => import('../views/ApprovalTodoView.vue'), meta: { roles: ['APPROVER_ADMIN', 'SYS_ADMIN'] } },
      { path: '/approvals/done', component: () => import('../views/ApprovalDoneView.vue'), meta: { roles: ['APPROVER_ADMIN', 'SYS_ADMIN'] } },
      { path: '/admin/workflow', component: () => import('../views/WorkflowTemplateView.vue'), meta: { roles: ['SYS_ADMIN'] } },
      { path: '/admin/users', component: () => import('../views/UserManagementView.vue'), meta: { roles: ['APPROVER_ADMIN', 'SYS_ADMIN'] } },
      { path: '/admin/industry-mappings', component: () => import('../views/IndustryMappingAdminView.vue'), meta: { roles: ['SYS_ADMIN'] } },
      { path: '/admin/submission-options', component: () => import('../views/SubmissionOptionAdminView.vue'), meta: { roles: ['SYS_ADMIN'] } },
      { path: '/admin/audit-logs', component: () => import('../views/AuditLogView.vue'), meta: { roles: ['APPROVER_ADMIN', 'SYS_ADMIN'] } },
      { path: '/common/notices', component: () => import('../views/NoticeView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/sso') {
    return true
  }
  if (to.path === '/login') {
    if (auth.isLogin) return '/'
    return true
  }
  if (!auth.isLogin) return '/login'
  if (!auth.userInfo) {
    await auth.fetchMe()
  }
  const requiredRoles = to.meta.roles || []
  if (!requiredRoles.length) return true
  const ok = requiredRoles.some((r) => auth.roles.includes(r))
  return ok ? true : '/common/notices'
})

export default router
