import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

import type { ApiResponse } from '@/types/api'

export const API_BASE_URL: string =
  (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.VITE_API_BASE_URL ||
  '/api/v1'

/** Access Token 仅保存在内存（模块变量），不写 localStorage */
let accessToken = ''

export function setAccessToken(token: string): void {
  accessToken = token
}

export function getAccessToken(): string {
  return accessToken
}

export function clearAccessToken(): void {
  accessToken = ''
}

/** 归一化的接口错误 */
export class ApiError extends Error {
  readonly code: string
  readonly httpStatus: number
  readonly traceId: string
  readonly errors?: Record<string, string[]>

  constructor(code: string, message: string, httpStatus: number, traceId: string,
              errors?: Record<string, string[]>) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.traceId = traceId
    this.errors = errors
  }
}

/** 判断响应是否为"令牌过期/无效"的可刷新 401 */
export function isTokenExpired401(status: number, code: string | undefined): boolean {
  return status === 401 && (code === undefined || code === 'AUTH_40102' || code === 'AUTH_40101')
}

/** 判断是否跳过自动刷新（登录/刷新接口自身的 401 不触发刷新） */
export function shouldSkipRefresh(url: string | undefined): boolean {
  if (!url) return true
  return url.includes('/auth/login') || url.includes('/auth/refresh')
}

/** 401 后单飞刷新：并发请求只触发一次 /auth/refresh */
let refreshing: Promise<string> | null = null

export function refreshAccessToken(): Promise<string> {
  if (!refreshing) {
    refreshing = axios
      .post<ApiResponse<{ accessToken: string }>>(
        `${API_BASE_URL}/auth/refresh`,
        {},
        { withCredentials: true },
      )
      .then((res) => {
        const token = res.data?.data?.accessToken
        if (!token) {
          throw new ApiError('AUTH_40102', '刷新会话失败', 401, res.data?.traceId ?? '')
        }
        setAccessToken(token)
        return token
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  withCredentials: true,
})

request.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse
    if (body && typeof body.code === 'string' && body.code !== '0') {
      // HTTP 200 但业务码失败
      throw new ApiError(body.code, body.message, response.status, body.traceId, body.errors)
    }
    return response
  },
  async (error: AxiosError<ApiResponse>) => {
    const status = error.response?.status ?? 0
    const body = error.response?.data
    const code = body?.code
    const config = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    const url = config?.url ?? ''

    // Access Token 过期类 401：单飞刷新后原请求最多重试一次
    if (
      status === 401 &&
      config &&
      !config._retried &&
      !shouldSkipRefresh(url) &&
      isTokenExpired401(status, code)
    ) {
      try {
        const newToken = await refreshAccessToken()
        config._retried = true
        config.headers = { ...config.headers, Authorization: `Bearer ${newToken}` }
        return request(config)
      } catch (refreshError) {
        clearAccessToken()
        redirectToLogin()
        return Promise.reject(normalize(refreshError))
      }
    }

    return Promise.reject(normalize(error))
  },
)

function normalize(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiResponse | undefined
    return new ApiError(
      body?.code ?? 'NETWORK_ERROR',
      body?.message ?? error.message ?? '网络错误',
      error.response?.status ?? 0,
      body?.traceId ?? '',
      body?.errors,
    )
  }
  return new ApiError('UNKNOWN', String(error), 0, '')
}

function redirectToLogin(): void {
  if (typeof window !== 'undefined' && !window.location.pathname.endsWith('/login')) {
    window.location.href = '/login'
  }
}

/** 供 api 层使用的请求方法：返回 data 部分 */
export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.get<ApiResponse<T>>(url, config)
  return res.data.data as T
}

export async function post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.post<ApiResponse<T>>(url, body, config)
  return res.data.data as T
}

export async function patch<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.patch<ApiResponse<T>>(url, body, config)
  return res.data.data as T
}

export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.delete<ApiResponse<T>>(url, config)
  return res.data.data as T
}

/** multipart 上传：Content-Type 由浏览器自动生成（带 boundary） */
export async function upload<T>(url: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  const res = await request.post<ApiResponse<T>>(url, form)
  return res.data.data as T
}

export default request
