import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 告诉 Sass 忽略 @import 弃用警告 (Vue 2.x 风格 .vue <style lang="scss"> 仍需 @import)
        silenceDeprecations: ['legacy-js-api', 'import'],
        // 用现代编译器 API (Vite 5 推荐)
        api: 'modern-compiler'
      }
    }
  },
  server: {
    port: 5174,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:9000',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:9000',
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'vant-vendor': ['vant'],
          'echarts-vendor': ['echarts', 'vue-echarts']
        }
      }
    }
  }
})
