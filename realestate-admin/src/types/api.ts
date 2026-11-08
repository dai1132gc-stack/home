/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: string
  message: string
  data: T | null
  errors?: Record<string, string[]>
  traceId: string
  timestamp: string
}

/** 当前用户信息 */
export interface UserVO {
  id: number
  username: string
  nickname: string | null
  role: 'ADMIN' | 'CUSTOMER'
  status: string
  createdAt: string
}

/** 登录/刷新响应（WEB 端 refreshToken 为空，走 HttpOnly Cookie） */
export interface LoginResponse {
  accessToken: string
  refreshToken?: string
  tokenType: string
  expiresIn: number
  user: UserVO
}

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
  clientType: 'WEB' | 'MINI'
}
