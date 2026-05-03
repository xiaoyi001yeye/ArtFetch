import { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Result } from 'antd'
import { useAuth } from './AuthContext'

export function RequireAuth({ children, permissions }: { children: ReactNode; permissions?: string[] }) {
  const { user, hasAnyPermission } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (permissions?.length && !hasAnyPermission(permissions)) {
    return (
      <Result
        status="403"
        title="没有权限"
        subTitle="当前账号没有访问该页面所需的权限。"
      />
    )
  }

  return <>{children}</>
}
