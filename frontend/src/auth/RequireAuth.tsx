import { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { ForbiddenResult } from './ForbiddenResult'

export function RequireAuth({ children, permissions }: { children: ReactNode; permissions?: string[] }) {
  const { user, hasAnyPermission } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (permissions?.length && !hasAnyPermission(permissions)) {
    return <ForbiddenResult />
  }

  return <>{children}</>
}
