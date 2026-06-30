import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const device = ref<'desktop' | 'mobile'>('desktop')
  const size = ref<'default' | 'large' | 'small'>('default')
  const theme = ref<'light' | 'dark'>('light')

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setDevice(d: 'desktop' | 'mobile'): void {
    device.value = d
  }

  function setSize(s: 'default' | 'large' | 'small'): void {
    size.value = s
  }

  function toggleTheme(): void {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    document.documentElement.classList.toggle('dark', theme.value === 'dark')
  }

  return {
    sidebarCollapsed,
    device,
    size,
    theme,
    toggleSidebar,
    setDevice,
    setSize,
    toggleTheme,
  }
})
