import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { Spin } from 'antd'
import * as api from '../api'
import type { CurrentUser } from '../types'

interface AuthContextValue {
  user: CurrentUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
  hasPermission: (permission: string) => boolean
  hasAnyPermission: (permissions: string[]) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  const refreshUser = useCallback(async () => {
    if (!api.getStoredToken()) {
      setUser(null)
      return
    }
    const current = await api.getCurrentUser()
    setUser(current)
  }, [])

  useEffect(() => {
    refreshUser()
      .catch(() => setUser(null))
      .finally(() => setLoading(false))

    const handleUnauthorized = () => setUser(null)
    window.addEventListener('artfetch:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('artfetch:unauthorized', handleUnauthorized)
  }, [refreshUser])

  const login = useCallback(async (username: string, password: string) => {
    const response = await api.login({ username, password })
    api.setStoredToken(response.tokenValue)
    setUser(response.user)
    return response.user
  }, [])

  const logout = useCallback(async () => {
    try {
      if (api.getStoredToken()) {
        await api.logout()
      }
    } finally {
      api.clearStoredToken()
      setUser(null)
    }
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    login,
    logout,
    refreshUser,
    hasPermission: (permission) => Boolean(user?.permissions.includes(permission)),
    hasAnyPermission: (permissions) => permissions.some((permission) => user?.permissions.includes(permission)),
  }), [user, loading, login, logout, refreshUser])

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin />
      </div>
    )
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
