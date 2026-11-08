import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/store/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        {
          path: '',
          redirect: '/dashboard',
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '工作台' },
        },
      ],
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/error/ForbiddenView.vue'),
      meta: { public: true, title: '无权限' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/error/NotFoundView.vue'),
      meta: { public: true, title: '页面不存在' },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  document.title = to.meta.title ? `${String(to.meta.title)} · 房产中介管理系统` : '房产中介管理系统'

  if (to.meta.public) {
    return true
  }

  // 未恢复过会话（如页面刷新）：先尝试用 Cookie 恢复
  const ok = await auth.restoreSession()
  if (!ok) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 角色校验（前端仅控制展示，接口端由 Spring Security 最终校验）
  const roles = (to.meta.roles as string[] | undefined) ?? []
  if (roles.length > 0 && auth.user && !roles.includes(auth.user.role)) {
    return { name: 'forbidden' }
  }
  return true
})

export default router
