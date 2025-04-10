import { defineStore } from 'pinia'
import { store } from '@/store'
import { ref, reactive } from 'vue'

export const useAppStore = defineStore('app-store', () => {
  const config = reactive({
    collapsed: false
  })

  function toggleSidebarCollapsed() {
    config.collapsed = !config.collapsed
  }

  return {
    config,
    toggleSidebarCollapsed
  }
})

export function useAppStoreWithOut() {
  return useAppStore(store)
}
