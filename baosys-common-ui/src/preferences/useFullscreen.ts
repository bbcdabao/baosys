import { onMounted, onUnmounted, ref } from 'vue'
import { readPreference, writePreference } from './storage'

export const FULLSCREEN_STORAGE_KEY = 'baosys-fullscreen'

export const useFullscreen = (onError: () => void) => {
  const fullscreen = ref(false)
  let leaving = false
  let restorePending = readPreference(FULLSCREEN_STORAGE_KEY) === 'true'
  const update = () => {
    fullscreen.value = Boolean(document.fullscreenElement)
    if (!leaving) writePreference(FULLSCREEN_STORAGE_KEY, String(fullscreen.value))
  }
  const toggleFullscreen = async () => {
    restorePending = false
    const next = !document.fullscreenElement
    // Keep intent even when the browser denies fullscreen; never fake actual state.
    writePreference(FULLSCREEN_STORAGE_KEY, String(next))
    try {
      if (next) await document.documentElement.requestFullscreen()
      else await document.exitFullscreen()
    } catch {
      onError()
    }
  }
  const restore = (event: Event) => {
    if (!event.isTrusted || !restorePending || document.fullscreenElement) return
    // The fullscreen button handles its own click, avoiding a double toggle.
    if (event.target instanceof Element && event.target.closest('[data-fullscreen-toggle]')) return
    restorePending = false
    void document.documentElement.requestFullscreen().catch(() => {
      // Preference remains available for the explicit fullscreen button.
    })
  }
  const storage = (event: StorageEvent) => {
    if (event.key !== FULLSCREEN_STORAGE_KEY && event.key !== null) return
    restorePending = readPreference(FULLSCREEN_STORAGE_KEY) === 'true'
    if (!restorePending && document.fullscreenElement) void document.exitFullscreen().catch(onError)
  }
  const pagehide = () => {
    leaving = true
  }
  const pageshow = () => {
    leaving = false
    fullscreen.value = Boolean(document.fullscreenElement)
    restorePending = readPreference(FULLSCREEN_STORAGE_KEY) === 'true'
  }
  onMounted(() => {
    // Initial non-fullscreen state must not erase a remembered preference.
    fullscreen.value = Boolean(document.fullscreenElement)
    document.addEventListener('fullscreenchange', update)
    document.addEventListener('click', restore)
    window.addEventListener('storage', storage)
    window.addEventListener('pagehide', pagehide)
    window.addEventListener('pageshow', pageshow)
  })
  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', update)
    document.removeEventListener('click', restore)
    window.removeEventListener('storage', storage)
    window.removeEventListener('pagehide', pagehide)
    window.removeEventListener('pageshow', pageshow)
  })
  return { fullscreen, toggleFullscreen }
}
