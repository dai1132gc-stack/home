import { get, post } from '@/utils/request'

import type { LoginRequest, LoginResponse, UserVO } from '@/types/api'

/** 账号密码登录（Web 端，Refresh Token 由 HttpOnly Cookie 承载） */
export function login(payload: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/login', payload)
}

/** 刷新令牌（依赖 HttpOnly Cookie） */
export function refresh(): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/refresh', {})
}

/** 退出登录 */
export function logout(): Promise<void> {
  return post<void>('/auth/logout', {})
}

/** 当前用户信息 */
export function me(): Promise<UserVO> {
  return get<UserVO>('/auth/me')
}
