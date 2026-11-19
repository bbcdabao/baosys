export const readPreference = (key: string): string | null => {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export const writePreference = (key: string, value: string): void => {
  try {
    localStorage.setItem(key, value)
  } catch {
    // Storage may be unavailable; controls still work in the current page.
  }
}
