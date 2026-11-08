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
        {
          path: 'houses',
          name: 'house-list',
          component: () => import('@/views/house/HouseListView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '房源管理' },
        },
        {
          path: 'houses/new',
          name: 'house-create',
          component: () => import('@/views/house/HouseFormView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '新建房源' },
        },
        {
          path: 'houses/:id',
          name: 'house-detail',
          component: () => import('@/views/house/HouseDetailView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '房源详情' },
        },
        {
          path: 'houses/:id/edit',
          name: 'house-edit',
          component: () => import('@/views/house/HouseFormView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '编辑房源' },
        },
        {
          path: 'owners',
          name: 'owner-list',
          component: () => import('@/views/owner/OwnerListView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '客源管理' },
        },
        {
          path: 'owners/new',
          name: 'owner-create',
          component: () => import('@/views/owner/OwnerFormView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '新建房东/客源' },
        },
        {
          path: 'owners/:id',
          name: 'owner-detail',
          component: () => import('@/views/owner/OwnerDetailView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '客源跟进' },
        },
        {
          path: 'owners/:id/edit',
          name: 'owner-edit',
          component: () => import('@/views/owner/OwnerFormView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '编辑房东/客源' },
        },
        {
          path: 'appointments',
          name: 'appointment-list',
          component: () => import('@/views/appointment/AppointmentListView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '预约管理' },
        },
        {
          path: 'contracts',
          name: 'contract-list',
          component: () => import('@/views/contract/ContractListView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '合同管理' },
        },
        {
          path: 'contracts/new',
          name: 'contract-create',
          component: () => import('@/views/contract/ContractFormView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '起草合同' },
        },
        {
          path: 'contracts/:id',
          name: 'contract-detail',
          component: () => import('@/views/contract/ContractDetailView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '合同详情' },
        },
        {
          path: 'repairs',
          name: 'repair-list',
          component: () => import('@/views/repair/RepairListView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '报修管理' },
        },
        {
          path: 'contents/guides',
          name: 'content-guides',
          component: () => import('@/views/content/GuideManageView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '内容管理' },
        },
        {
          path: 'contents/policies',
          name: 'content-policies',
          component: () => import('@/views/content/PolicyManageView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '政策文件' },
        },
        {
          path: 'calculator',
          name: 'calculator',
          component: () => import('@/views/calculator/CalculatorView.vue'),
          meta: { requiresAuth: true, roles: ['ADMIN'], title: '测算工具' },
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
