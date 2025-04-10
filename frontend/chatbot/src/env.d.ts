/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 环境变量类型
interface ImportMetaEnv {
  VITE_API_BASE_URL: string
  readonly VITE_BASE_API: string
  readonly VITE_SPARK_KEY: string
}

// 添加particles.js类型声明
interface Window {
  particlesJS: any
  pJSDom: any[]
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
