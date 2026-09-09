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
  // 테스트 러너(vitest) — 개발 서버와 같은 변환 파이프라인·별칭을 그대로 쓴다.
  // 설정을 두 벌로 두지 않으려고 vite.config에 함께 둔다.
  //
  // 지금 범위는 순수 로직(utils)이라 DOM이 필요 없어 environment는 node다.
  // `.vue` 컴포넌트를 테스트할 때 jsdom(또는 happy-dom)과 @vue/test-utils를 추가한다.
  test: {
    environment: 'node',
    include: ['src/**/*.spec.js'],
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 백엔드가 서빙하는 정적 자원. BASE_URL이 비어 있어도(=.env 없이도)
      // 개발 서버에서 이미지·샘플이 그대로 보이게 한다.
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/demo-images': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/samples': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 제안서 템플릿 API 프록시
      '/proposal-templates': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 제안서 API 프록시
      '/proposals': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
