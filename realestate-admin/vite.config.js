import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
// https://vite.dev/config/
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    server: {
        port: 5174,
        strictPort: true,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/actuator': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            // W2：房源图片由后端本地存储提供（dev），前端直接同源访问
            '/uploads': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
});
