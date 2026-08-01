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
        // 用现代编译器 API (Vite 5 推荐)
        api: 'modern-compiler',
        // 安静所有 Sass 弃用警告 (legacy-js-api / import / global-builtin / color-functions)
        silenceDeprecations: [
          'legacy-js-api',
          'import',
          'global-builtin',
          'color-functions'
        ],
        // 把 src/styles 加进 sass 搜索路径, 兼容老的 @import 'agent-theme' 写法
        loadPaths: [fileURLToPath(new URL('./src/styles', import.meta.url))]
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
