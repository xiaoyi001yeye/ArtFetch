export type ArtFetchViewMode = 'auto' | 'mobile' | 'desktop'

export const VIEW_MODE_STORAGE_KEY = 'artfetch.viewMode'

export const getStoredViewMode = (): ArtFetchViewMode => {
  const value = localStorage.getItem(VIEW_MODE_STORAGE_KEY)
  return value === 'mobile' || value === 'desktop' || value === 'auto' ? value : 'auto'
}

export const setStoredViewMode = (mode: ArtFetchViewMode) => {
  localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode)
}

export const clearStoredViewMode = () => {
  localStorage.removeItem(VIEW_MODE_STORAGE_KEY)
}

export const isMobileViewport = () => {
  if (typeof window === 'undefined') return false
  return window.matchMedia('(max-width: 768px)').matches
}

export const shouldUseMobileDataView = () => {
  const mode = getStoredViewMode()
  if (mode === 'desktop') return false
  if (mode === 'mobile') return true
  return isMobileViewport()
}
