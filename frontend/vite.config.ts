import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import cameraLauncherPlugin from './dev/camera-plugin.mjs'

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
        target: 'http://127.0.0.1:8088',
        changeOrigin: true,
        rewrite: () => '/video_feed'
      },
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
