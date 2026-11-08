<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '@/store/auth'
import { ApiError, get } from '@/utils/request'

interface SmokeMessage {
  message: string
  username: string
}

const auth = useAuthStore()
const adminCheck = ref('')
const checking = ref(false)

onMounted(async () => {
  // 冒烟验证：调用受 ADMIN 保护的接口确认会话有效
  checking.value = true
  try {
    const result = await get<SmokeMessage>('/smoke/admin')
    adminCheck.value = result.message
  } catch (error) {
    adminCheck.value = error instanceof ApiError ? `失败：${error.message}` : '验证失败'
  } finally {
    checking.value = false
  }
})

function copyTrace(): void {
  ElMessage.info('W1 阶段暂无更多功能，请等待 W2 房源模块')
}
</script>

<template>
  <div class="dashboard">
    <el-card>
      <template #header>
        <span>工作台</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">{{ auth.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ auth.user?.role }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ auth.user?.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账号状态">{{ auth.user?.status }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        class="smoke"
        :title="checking ? '正在验证会话…' : `会话验证：${adminCheck || '未执行'}`"
        :type="adminCheck.startsWith('失败') ? 'error' : 'success'"
        :closable="false"
      />
    </el-card>
    <el-card class="todo">
      <template #header>
        <span>待办模块（W2 起）</span>
      </template>
      <el-space wrap>
        <el-tag>房源管理</el-tag>
        <el-tag>客源管理</el-tag>
        <el-tag>预约带看</el-tag>
        <el-tag>合同管理</el-tag>
        <el-tag>报修处理</el-tag>
        <el-tag>政策公告</el-tag>
      </el-space>
      <el-button class="hint-btn" text type="primary" @click="copyTrace">了解更多</el-button>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  display: grid;
  gap: 16px;
}
.smoke {
  margin-top: 16px;
}
.todo {
  margin-top: 0;
}
.hint-btn {
  margin-top: 12px;
}
</style>
