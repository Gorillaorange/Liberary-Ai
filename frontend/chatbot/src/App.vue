<script lang="ts" setup>
import NaiveProvider from './NaiveProvider.vue'
import { dateZhCN, zhCN } from 'naive-ui'

const { defaultTheme, themeOverrides } = useTheme()

defineOptions({
  name: 'App'
})
useCopyCode()
</script>

<template>
  <NConfigProvider
    class="h-full"
    :locale="zhCN"
    :date-locale="dateZhCN"
    :theme="defaultTheme"
    :theme-overrides="themeOverrides"
  >
    <NaiveProvider>
      <RouterView />
    </NaiveProvider>
  </NConfigProvider>
</template>

<style lang="scss">
@use "@/styles/index";

/* 全局覆盖样式 */
:deep(.n-base-select-menu) {
  background-color: var(--primary-dark) !important;
  border: 1px solid var(--card-border-dark) !important;
  color: var(--text-light) !important;
}

:deep(.n-base-select-option) {
  color: var(--text-light) !important;
  background-color: transparent !important;
  
  &:hover, &.n-base-select-option--pending {
    background-color: rgba(74, 111, 164, 0.3) !important;
  }
  
  &.n-base-select-option--selected {
    color: var(--accent-color) !important;
    background-color: rgba(74, 111, 164, 0.2) !important;
  }
}

:deep(.n-base-select-menu__action) {
  color: var(--text-light) !important;
  border-top: 1px solid var(--card-border-dark) !important;
}

/* 覆盖基于DOM结构的选择器 */
:deep([class*="n-base-select-menu"]) {
  background-color: var(--primary-dark) !important;
}

:deep([class*="n-base-select-option--selected"]) {
  background-color: rgba(74, 111, 164, 0.2) !important;
}
</style>
