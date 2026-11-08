<template>
  <el-result icon="warning" title="403" sub-title="抱歉，您没有权限访问该页面。">
    <template #extra>
      <el-button type="primary" @click="$router.push('/dashboard')">返回工作台</el-button>
    </template>
  </el-result>
</template>
