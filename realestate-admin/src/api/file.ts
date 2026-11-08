import { upload } from '@/utils/request'

export interface UploadResult {
  url: string
  contentType: string
  size: number
}

/** 上传图片（ADMIN）：返回相对路径 /uploads/...，与后端同源代理 */
export function uploadImage(file: File): Promise<UploadResult> {
  return upload<UploadResult>('/files/upload', file)
}
