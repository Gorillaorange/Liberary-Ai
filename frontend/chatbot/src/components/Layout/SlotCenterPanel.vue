<script lang="ts" setup>
interface Props {
  loading?: boolean
}
withDefaults(
  defineProps<Props>(),
  {
    loading: false
  }
)
</script>

<template>
  <LayoutSlotFrame
    :class="[
      'bg-no-repeat bg-cover bg-center',
    ]"
  >
    <template #center>
      <div
        class="center-container panel-shadow"
        overflow-hidden
      >
        <n-spin
          w-full
          h-full
          content-class="w-full h-full flex"
          :show="loading"
          :rotate="false"
          class="transparent-bg"
          :style="{
            '--n-opacity-spinning': '0'
          }"
        >
          <template #icon>
            <div class="i-svg-spinners:pulse-3"></div>
          </template>
          <section
            v-if="$slots.left"
            flex="~ col"
            w-280
            h-full
            overflow-hidden
            class="left-section"
          >
            <slot name="left"></slot>
          </section>
          <section
            flex="1"
            h-full
            overflow-hidden
            class="main-section"
          >
            <slot name="default"></slot>
          </section>
        </n-spin>
      </div>
    </template>
    <template #bottom>
      <NavigationNavFooter />
    </template>
  </LayoutSlotFrame>
</template>

<style lang="scss" scoped>
.panel-shadow {
  --shadow: 0px 0px 0px 0px rgba(0, 0, 0, 0);
  box-shadow: var(--shadow);
  width: 94%;
  max-width: 1680px;
  height: 94%;
  margin: 0 auto;
  border-radius: 12px;
  overflow: hidden;
}

.transparent-bg {
  background-color: transparent !important;
}

.left-section {
  background-color: transparent;
}

.main-section {
  background-color: transparent;
}

.center-container {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

:deep(.n-base-selection) {
  background-color: rgba(38, 50, 71, 0.6) !important;
  border: 1px solid var(--card-border-dark) !important;
  
  .n-base-selection-input {
    color: var(--text-light) !important;
  }
  
  .n-base-selection-tags__wrapper {
    background-color: transparent !important;
  }
  
  .n-base-selection__placeholder {
    color: var(--text-light-secondary) !important;
  }
}

:deep(.n-base-selection:hover),
:deep(.n-base-selection--active) {
  border-color: var(--accent-color) !important;
  box-shadow: 0 0 0 2px rgba(74, 111, 164, 0.3) !important;
}

:deep(.n-spin) {
  color: var(--text-light) !important;
}
</style>
