import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import cameraLauncherPlugin from './dev/camera-plugin.mjs'

const backendTarget = process.env.CLASSROOM_API_PROXY || 'http://127.0.0.1:8080'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), cameraLauncherPlugin(path.resolve(__dirname, '..'))],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    host: '127.0.0.1',
    strictPort: true,
    proxy: {
      '/api/visual/video-feed': {
        target: `http://127.0.0.1:${process.env.CLASSROOM_MONITOR_PORT || 8088}`,
        changeOrigin: true,
        rewrite: () => '/video_feed'
      },
      '/api': {
        target: backendTarget,
        changeOrigin: true
      },
      '/uploads': {
        target: backendTarget,
        changeOrigin: true
      }
    }
  }
})
