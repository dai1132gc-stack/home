import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'

import 'element-plus/dist/index.css'
import './assets/main.css'

const app = createApp(App)

// W2 修复：W1 仅引入了 Element Plus 样式，未注册组件，导致 el-* 组件无法渲染
app.use(ElementPlus, { locale: zhCn })
app.use(createPinia())
app.use(router)

app.mount('#app')
