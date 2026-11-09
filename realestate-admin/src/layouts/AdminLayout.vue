<script setup lang="ts">
import { ArrowDown, Calendar, Document, DocumentCopy, House, MagicStick, OfficeBuilding, Tools, TrendCharts, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

async function handleLogout(): Promise<void> {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">
        <el-icon :size="20"><House /></el-icon>
        <span>房产中介管理系统</span>
      </div>
      <el-menu router :default-active="$route.path" :default-openeds="['/contents']" class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/houses">
          <el-icon><OfficeBuilding /></el-icon>
          <span>房源管理</span>
        </el-menu-item>
        <el-menu-item index="/owners">
          <el-icon><User /></el-icon>
          <span>客源管理</span>
        </el-menu-item>
        <el-menu-item index="/appointments">
          <el-icon><Calendar /></el-icon>
          <span>预约管理</span>
        </el-menu-item>
        <el-menu-item index="/contracts">
          <el-icon><Document /></el-icon>
          <span>合同管理</span>
        </el-menu-item>
        <el-menu-item index="/repairs">
          <el-icon><Tools /></el-icon>
          <span>报修管理</span>
        </el-menu-item>
        <el-sub-menu index="/contents">
          <template #title>
            <el-icon><DocumentCopy /></el-icon>
            <span>内容管理</span>
          </template>
          <el-menu-item index="/contents/guides">教程与公告</el-menu-item>
          <el-menu-item index="/contents/policies">政策文件</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/calculator">
          <el-icon><TrendCharts /></el-icon>
          <span>测算工具</span>
        </el-menu-item>
        <el-menu-item index="/ai">
          <el-icon><MagicStick /></el-icon>
          <span>智能服务</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div></div>
        <el-dropdown v-if="auth.user" @command="handleLogout">
          <span class="user-chip">
            {{ auth.user.nickname || auth.user.username }}
            <el-tag size="small" type="primary">{{ auth.user.role }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  border-right: 1px solid var(--el-border-color-light);
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  font-weight: 600;
  font-size: 15px;
}
.menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
}
.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
}
.main {
  background: var(--el-fill-color-lighter);
}
</style>
