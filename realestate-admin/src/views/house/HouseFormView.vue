<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import { UploadAjaxError } from 'element-plus/es/components/upload/src/ajax'
import { Plus, UploadFilled } from '@element-plus/icons-vue'

import { uploadImage } from '@/api/file'
import { addHouseMedia, createHouse, getHouse, removeHouseMedia, updateHouse } from '@/api/house'
import { listOwners } from '@/api/owner'
import type { HouseFormPayload, HouseType } from '@/types/house'
import type { OwnerVO } from '@/types/owner'
import { ApiError } from '@/utils/request'

interface ImageItem {
  id?: number
  url: string
}

const route = useRoute()
const router = useRouter()

const houseId = computed(() => {
  const raw = route.params.id
  return raw ? Number(raw) : null
})
const isEdit = computed(() => houseId.value !== null)
const MAX_MEDIA = 20

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const owners = ref<OwnerVO[]>([])
const images = ref<ImageItem[]>([])
const version = ref(0)

const form = reactive<HouseFormPayload>({
  title: '',
  houseType: 'RENT' as HouseType,
  community: '',
  region: '',
  address: '',
  price: 3000,
  area: 80,
  rooms: 2,
  bathrooms: 1,
  floor: '',
  description: '',
  ownerId: undefined,
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  houseType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  community: [{ required: true, message: '请输入小区', trigger: 'blur' }],
  region: [{ required: true, message: '请输入区域', trigger: 'blur' }],
  address: [{ required: true, message: '请输入地址', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  area: [{ required: true, message: '请输入面积', trigger: 'blur' }],
  rooms: [{ required: true, message: '请输入室', trigger: 'blur' }],
  bathrooms: [{ required: true, message: '请输入卫', trigger: 'blur' }],
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function loadOwners(): Promise<void> {
  try {
    const result = await listOwners({ page: 1, size: 100 })
    owners.value = result.items
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载客源列表失败'))
  }
}

async function loadHouse(): Promise<void> {
  if (!houseId.value) return
  loading.value = true
  try {
    const detail = await getHouse(houseId.value)
    form.title = detail.title
    form.houseType = detail.houseType
    form.community = detail.community
    form.region = detail.region
    form.address = detail.address
    form.price = detail.price
    form.area = detail.area
    form.rooms = detail.rooms
    form.bathrooms = detail.bathrooms
    form.floor = detail.floor ?? ''
    form.description = detail.description ?? ''
    form.ownerId = detail.ownerId ?? undefined
    version.value = detail.version
    images.value = detail.media.filter((m) => m.mediaType === 'IMAGE').map((m) => ({ id: m.id, url: m.url }))
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载房源失败'))
  } finally {
    loading.value = false
  }
}

/** el-upload 自定义上传：先落盘拿到 url，保存时再关联到房源 */
async function handleUpload(options: UploadRequestOptions): Promise<void> {
  if (images.value.length >= MAX_MEDIA) {
    ElMessage.warning(`最多 ${MAX_MEDIA} 张图片`)
    options.onError(new UploadAjaxError('media limit', 0, 'POST', '/files/upload'))
    return
  }
  uploading.value = true
  try {
    const result = await uploadImage(options.file as File)
    images.value.push({ url: result.url })
    options.onSuccess(result)
  } catch (error) {
    const message = errorMessage(error, '图片上传失败')
    options.onError(new UploadAjaxError(message, 0, 'POST', '/files/upload'))
    ElMessage.error(message)
  } finally {
    uploading.value = false
  }
}

async function removeImage(item: ImageItem): Promise<void> {
  if (item.id && houseId.value) {
    try {
      await ElMessageBox.confirm('从房源中移除该图片？', '移除确认', { type: 'warning' })
    } catch {
      return
    }
    try {
      await removeHouseMedia(houseId.value, item.id)
      ElMessage.success('已移除')
    } catch (error) {
      ElMessage.error(errorMessage(error, '移除失败'))
      return
    }
  }
  images.value = images.value.filter((i) => i !== item)
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload: HouseFormPayload = {
      ...form,
      floor: form.floor || undefined,
      description: form.description || undefined,
      ownerId: form.ownerId || undefined,
      version: isEdit.value ? version.value : undefined,
    }
    const saved = isEdit.value && houseId.value
      ? await updateHouse(houseId.value, payload)
      : await createHouse(payload)

    // 只关联本次新上传的图片（已有媒体带 id，不重复提交）
    const pending = images.value.filter((i) => !i.id)
    for (let index = 0; index < pending.length; index += 1) {
      await addHouseMedia(saved.id, {
        url: pending[index].url,
        mediaType: 'IMAGE',
        sort: index,
      })
    }

    ElMessage.success(isEdit.value ? '保存成功' : '创建成功，状态为待发布')
    router.push({ name: 'house-detail', params: { id: saved.id } })
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

function cancel(): void {
  router.back()
}

onMounted(() => {
  loadOwners()
  loadHouse()
})
</script>

<template>
  <div class="page">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? `编辑房源 #${houseId}` : '新建房源' }}</span>
          <el-tag v-if="isEdit" type="info">版本 {{ version }}</el-tag>
          <el-tag v-else type="warning">新建后为待发布</el-tag>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标题" prop="title">
              <el-input v-model="form.title" maxlength="120" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="类型" prop="houseType">
              <el-radio-group v-model="form.houseType">
                <el-radio-button value="RENT">出租</el-radio-button>
                <el-radio-button value="SALE">出售</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="房东/客源">
              <el-select v-model="form.ownerId" placeholder="可不选" clearable filterable style="width: 100%">
                <el-option
                  v-for="owner in owners"
                  :key="owner.id"
                  :label="`${owner.name}（${owner.phone}）`"
                  :value="owner.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="小区" prop="community">
              <el-input v-model="form.community" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区域" prop="region">
              <el-input v-model="form.region" maxlength="60" placeholder="如 浦东新区" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="楼层">
              <el-input v-model="form.floor" maxlength="32" placeholder="如 3/18" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" maxlength="255" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="价格(元)" prop="price">
              <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="面积(㎡)" prop="area">
              <el-input-number v-model="form.area" :min="0.01" :precision="2" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="室" prop="rooms">
              <el-input-number v-model="form.rooms" :min="0" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="卫" prop="bathrooms">
              <el-input-number v-model="form.bathrooms" :min="0" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="5000"
            show-word-limit
            placeholder="房源亮点、配套、看房时间等"
          />
        </el-form-item>

        <el-form-item label="图片">
          <div class="media">
            <div v-for="item in images" :key="item.url" class="media-item">
              <el-image :src="item.url" fit="cover" class="media-img" :preview-src-list="[item.url]" preview-teleported />
              <el-button class="media-remove" type="danger" size="small" circle @click="removeImage(item)">
                ×
              </el-button>
            </div>
            <el-upload
              v-if="images.length < MAX_MEDIA"
              :show-file-list="false"
              :http-request="handleUpload"
              accept="image/jpeg,image/png,image/gif,image/webp"
            >
              <div class="upload-btn" v-loading="uploading">
                <el-icon :size="22"><Plus v-if="!uploading" /><UploadFilled v-else /></el-icon>
                <span>上传</span>
              </div>
            </el-upload>
          </div>
          <div class="hint">支持 JPG/PNG/GIF/WEBP，单张不超过 10MB，最多 {{ MAX_MEDIA }} 张</div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
          <el-button @click="cancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  gap: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.form {
  max-width: 1100px;
}
.media {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.media-item {
  position: relative;
  width: 104px;
  height: 104px;
}
.media-img {
  width: 100%;
  height: 100%;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-light);
}
.media-remove {
  position: absolute;
  top: -8px;
  right: -8px;
}
.upload-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 104px;
  height: 104px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
}
.upload-btn:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.hint {
  width: 100%;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
