import { computed } from 'vue'
import { useWindowSize } from '@vueuse/core'

export function useBasicLayout() {
  const { width } = useWindowSize()

  const isMobile = computed(() => width.value <= 768)
  const isDesktop = computed(() => width.value > 768)

  return {
    isMobile,
    isDesktop,
  }
} 