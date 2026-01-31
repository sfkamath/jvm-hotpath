import { defineConfig } from 'vite';
import path from 'node:path';

export default defineConfig({
  root: path.resolve(__dirname),
  define: {
    'process.env.NODE_ENV': '"production"',
    'process.env': '{}',
    process: '{}'
  },
  resolve: {
    alias: {
      vue: 'vue/dist/vue.esm-bundler.js'
    }
  },
  build: {
    target: 'es2020',
    outDir: path.resolve(__dirname, 'dist'),
    emptyOutDir: true,
    lib: {
      entry: path.resolve(__dirname, 'src/main.ts'),
      formats: ['iife'],
      name: 'JvmHotpathReport',
      fileName: () => 'report-app.js'
    },
    rollupOptions: {
      output: {
        entryFileNames: 'report-app.js',
        chunkFileNames: '[name].js',
        assetFileNames: '[name].[ext]',
        inlineDynamicImports: true
      }
    }
  }
});
