<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { createOwner, getOwner, updateOwner } from '@/api/owner'
import type { OwnerFormPayload, OwnerType } from '@/types/owner'
import { ApiError } from '@/utils/request'

const route = useRoute()
const router = useRouter()

const ownerId = computed(() => {
  const raw = route.params.id
  return raw ? Number(raw) : null
})
const isEdit = computed(() => ownerId.value !== null)

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const version = ref(0)

const form = reactive<OwnerFormPayload>({
  name: '',
  phone: '',
  type: 'LANDLORD' as OwnerType,
  remark: '',
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 64, message: '姓名不能超过 64 字', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function loadOwner(): Promise<void> {
  if (!ownerId.value) return
  loading.value = true
  try {
    const owner = await getOwner(ownerId.value)
    form.name = owner.name
    form.phone = owner.phone
    form.type = owner.type
    form.remark = owner.remark ?? ''
    version.value = owner.version
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载客源失败'))
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload: OwnerFormPayload = {
      ...form,
      remark: form.remark || undefined,
      version: isEdit.value ? version.value : undefined,
    }
    if (isEdit.value && ownerId.value) {
      await updateOwner(ownerId.value, payload)
    } else {
      await createOwner(payload)
    }
    ElMessage.success(isEdit.value ? '保存成功' : '创建成功')
    router.push({ name: 'owner-list' })
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

onMounted(loadOwner)
</script>

<template>
  <div class="page">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <span>{{ isEdit ? `编辑房东/客源 #${ownerId}` : '新建房东/客源' }}</span>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="form">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号，仅管理员可见" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button value="LANDLORD">房东</el-radio-button>
            <el-radio-button value="CUSTOMER_LEAD">客源</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="需求、预算、看房偏好等"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
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
.form {
  max-width: 720px;
}
</style>
