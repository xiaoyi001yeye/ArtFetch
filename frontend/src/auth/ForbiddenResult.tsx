import { Button, Result } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function ForbiddenResult({
  subTitle = '当前账号没有访问该页面所需的权限。',
  loginPath = '/login',
}: {
  subTitle?: string
  loginPath?: string
}) {
  const navigate = useNavigate()
  const { logout } = useAuth()

  const handleLogout = async () => {
    try {
      await logout()
    } finally {
      navigate(loginPath, { replace: true })
    }
  }

  return (
    <Result
      status="403"
      title="没有权限"
      subTitle={subTitle}
      extra={(
        <Button type="primary" icon={<LogoutOutlined />} onClick={handleLogout}>
          退出登录
        </Button>
      )}
    />
  )
}
