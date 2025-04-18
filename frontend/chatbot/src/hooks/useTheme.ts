import type { GlobalThemeOverrides } from 'naive-ui'
import { darkTheme, lightTheme } from 'naive-ui'
import { computed } from 'vue'

const baseThemeOverrides: GlobalThemeOverrides = {
  common: {
    borderRadius: '6px',
    heightLarge: '40px',
    fontSizeLarge: '18px'
  }
}

const PrimaryColor = '#4a6fa4'

// 暗色主题配置
const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...baseThemeOverrides.common,
    primaryColor: PrimaryColor,
    primaryColorHover: lightenDarkenColor(PrimaryColor, 30),
    primaryColorPressed: lightenDarkenColor(PrimaryColor, -30),
    primaryColorSuppl: getComplementaryColor(PrimaryColor)
  },
  Input: {
    placeholderColor: '#a8aeb8',
    color: '#e6eaf0',
    colorFocus: '#e6eaf0',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    borderHover: '1px solid rgba(74, 111, 164, 0.8)',
    borderFocus: '1px solid rgba(74, 111, 164, 0.8)',
    boxShadowFocus: '0 0 0 2px rgba(74, 111, 164, 0.3)',
    colorDisabled: 'rgba(230, 234, 240, 0.5)',
    placeholderColorDisabled: 'rgba(230, 234, 240, 0.3)'
  },
  Select: {
    peers: {
      InternalSelection: {
        textColor: '#e6eaf0',
        placeholderColor: '#a8aeb8',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        borderHover: '1px solid rgba(74, 111, 164, 0.8)',
        borderFocus: '1px solid rgba(74, 111, 164, 0.8)',
        boxShadowFocus: '0 0 0 2px rgba(74, 111, 164, 0.3)'
      },
      InternalSelectMenu: {
        color: '#e6eaf0',
        optionTextColor: '#e6eaf0',
        optionTextColorActive: PrimaryColor,
        optionTextColorPressed: PrimaryColor,
        optionColorPending: 'rgba(74, 111, 164, 0.3)',
        optionColorActive: 'rgba(74, 111, 164, 0.2)',
        optionCheckColor: PrimaryColor,
        peers: {
          Empty: {
            textColor: 'rgba(230, 234, 240, 0.5)'
          },
          Scrollbar: {
            color: 'rgba(255, 255, 255, 0.2)'
          }
        }
      }
    }
  }
}

export function useTheme() {
  const defaultTheme = computed(() => {
    return darkTheme
  })
  const themeRevert = computed(() => {
    return lightTheme
  })

  const themeOverrides = computed<GlobalThemeOverrides>(() => {
    return darkThemeOverrides
  })

  return {
    defaultTheme,
    themeRevert,
    themeOverrides
  }
}

function lightenDarkenColor(col, amt) {
  let usePound = false

  if (col[0] === '#') {
    col = col.slice(1)
    usePound = true
  }

  const num = parseInt(col, 16)

  let r = (num >> 16) + amt

  if (r > 255) r = 255
  else if (r < 0) r = 0

  let b = ((num >> 8) & 0x00FF) + amt

  if (b > 255) b = 255
  else if (b < 0) b = 0

  let g = (num & 0x0000FF) + amt

  if (g > 255) g = 255
  else if (g < 0) g = 0

  return (usePound ? '#' : '') + (g | (b << 8) | (r << 16)).toString(16)
}

function getComplementaryColor(hex) {
  hex = hex.slice(1) // remove #
  const r = parseInt(hex.substring(0, 2), 16)
  const g = parseInt(hex.substring(2, 4), 16)
  const b = parseInt(hex.substring(4, 6), 16)

  // get the complementary color
  const compR = (255 - r).toString(16).padStart(2, '0')
  const compG = (255 - g).toString(16).padStart(2, '0')
  const compB = (255 - b).toString(16).padStart(2, '0')

  return `#${ compR }${ compG }${ compB }`
}
