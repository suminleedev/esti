import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    proxy: {
      // 프록시: 프론트의 /catalog → 백엔드 http://localhost:8080/catalog
      '/catalog': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // (선택) 샘플 파일 정적 제공도 8080 쪽에 두는 경우
      '/product_catalog_sample.xlsx': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
