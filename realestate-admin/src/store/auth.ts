import { defineStore } from 'pinia'
import { ref } from 'vue'

import * as authApi from '@/api/auth'
import { clearAccessToken, getAccessToken, refreshAccessToken, setAccessToken } from '@/utils/request'

import type { LoginResponse, UserVO } from '@/types/api'

/**
 * 认证状态：Access Token 只存内存（Pinia state），
 * 页面刷新后通过 HttpOnly Cookie 的 Refresh 会话恢复。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const restored = ref(false)

  function applyLogin(result: LoginResponse): void {
    setAccessToken(result.accessToken)
    user.value = result.user
    restored.value = true
  }

  async function login(username: string, password: string): Promise<void> {
    const result = await authApi.login({
      username,
      password,
      clientType: 'WEB',
    })
    applyLogin(result)
  }

  /** 页面刷新后恢复会话：调用一次刷新接口（依赖 Cookie） */
  async function restoreSession(): Promise<boolean> {
    if (restored.value || getAccessToken()) {
      restored.value = true
      return true
    }
    try {
      const newToken = await refreshAccessToken()
      setAccessToken(newToken)
      user.value = await authApi.me()
      restored.value = true
      return true
    } catch {
      clearAccessToken()
      user.value = null
      restored.value = true
      return false
    }
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      clearAccessToken()
      user.value = null
    }
  }

  return { user, restored, login, restoreSession, logout, applyLogin }
})
