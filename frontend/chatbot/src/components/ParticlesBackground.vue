<script lang="ts" setup>
import { onMounted, onUnmounted, ref } from 'vue'

// 组件属性
interface Props {
  backgroundColor?: string
  particleColor?: string
  lineColor?: string
  zIndex?: number
}

const props = withDefaults(defineProps<Props>(), {
  backgroundColor: '#0f172a',
  particleColor: '#a0d2eb',
  lineColor: '#a0d2eb',
  zIndex: -1
})

const particlesLoaded = ref(false)

// 加载particles.js脚本
const loadParticlesScript = () => {
  return new Promise<void>((resolve, reject) => {
    // 先尝试检查是否已经加载过
    if (window.particlesJS) {
      particlesLoaded.value = true
      resolve()
      return
    }

    try {
      // 尝试使用动态脚本方式加载本地文件
      const script = document.createElement('script')
      
      // 本地文件路径
      const localPath = '/src/assets/js/particles.min.js'
      
      // 先尝试本地路径
      script.src = localPath
      script.onload = () => {
        console.log('本地particles.js加载成功')
        particlesLoaded.value = true
        resolve()
      }
      script.onerror = () => {
        console.warn('本地particles.js加载失败，尝试从CDN加载')
        
        // 如果本地加载失败，尝试CDN
        script.src = 'https://cdn.jsdelivr.net/particles.js/2.0.0/particles.min.js'
        script.onload = () => {
          console.log('CDN particles.js加载成功')
          particlesLoaded.value = true
          resolve()
        }
        script.onerror = () => {
          console.error('所有particles.js加载尝试都失败了')
          reject(new Error('所有particles.js加载尝试都失败了'))
        }
      }
      
      document.head.appendChild(script)
    } catch (error) {
      reject(error)
    }
  })
}

// 初始化particles
const initParticles = () => {
  if (window.particlesJS) {
    window.particlesJS('particles-js', {
      particles: {
        number: {
          value: 80,
          density: {
            enable: true,
            value_area: 700
          }
        },
        color: {
          value: props.particleColor
        },
        shape: {
          type: 'circle',
          stroke: {
            width: 0,
            color: '#000000'
          },
          polygon: {
            nb_sides: 5
          }
        },
        opacity: {
          value: 0.5,
          random: false,
          anim: {
            enable: false,
            speed: 0.1,
            opacity_min: 0.1,
            sync: false
          }
        },
        size: {
          value: 3,
          random: true,
          anim: {
            enable: false,
            speed: 10,
            size_min: 0.1,
            sync: false
          }
        },
        line_linked: {
          enable: true,
          distance: 150,
          color: props.lineColor,
          opacity: 0.4,
          width: 1
        },
        move: {
          enable: true,
          speed: 2,
          direction: 'none',
          random: false,
          straight: false,
          out_mode: 'out',
          bounce: false,
          attract: {
            enable: false,
            rotateX: 600,
            rotateY: 1200
          }
        }
      },
      interactivity: {
        detect_on: 'canvas',
        events: {
          onhover: {
            enable: true,
            mode: 'grab'
          },
          onclick: {
            enable: true,
            mode: 'push'
          },
          resize: true
        },
        modes: {
          grab: {
            distance: 140,
            line_linked: {
              opacity: 1
            }
          },
          bubble: {
            distance: 400,
            size: 40,
            duration: 2,
            opacity: 8,
            speed: 3
          },
          repulse: {
            distance: 200,
            duration: 0.4
          },
          push: {
            particles_nb: 4
          },
          remove: {
            particles_nb: 2
          }
        }
      },
      retina_detect: true
    })
  }
}

onMounted(async () => {
  try {
    await loadParticlesScript()
    initParticles()
  } catch (error) {
    console.error('Failed to initialize particles background:', error)
  }
})

onUnmounted(() => {
  // 清理工作
  if (window.pJSDom && window.pJSDom.length) {
    window.pJSDom = []
  }
})
</script>

<template>
  <div 
    id="particles-js" 
    class="particles-container"
    :style="{
      backgroundColor: props.backgroundColor,
      zIndex: props.zIndex
    }"
  ></div>
</template>

<style lang="scss" scoped>
.particles-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
</style>

<!-- 声明全局类型 -->
<script lang="ts">
// 为window添加particlesJS方法的类型声明
declare global {
  interface Window {
    particlesJS: any
    pJSDom: any[]
  }
}
</script> 