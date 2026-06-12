import { Button, Card, Form, Input, message, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import ArtFetchMark from '../components/ArtFetchMark'
import { useAuth } from '../auth/AuthContext'
import { permissions } from '../auth/permissions'
import { shouldUseMobileDataView } from '../mobileView'

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as any)?.from?.pathname || '/'

  if (user) {
    return <Navigate to={from} replace />
  }

  const handleFinish = async (values: { username: string; password: string }) => {
    try {
      const current = await login(values.username, values.password)
      message.success('登录成功')
      const target = shouldUseMobileDataView()
        && current.permissions.includes(permissions.artworkView)
        && (from === '/' || from === '/login')
        ? '/m/artworks'
        : from
      navigate(target, { replace: true })
    } catch (e: any) {
      message.error(e.message)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#f5f5f5', padding: 24 }}>
      <Card style={{ width: 380, boxShadow: '0 10px 30px rgba(15, 23, 42, 0.08)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, marginBottom: 24 }}>
          <ArtFetchMark size={34} />
          <Typography.Title level={3} style={{ margin: 0 }}>ArtFetch</Typography.Title>
        </div>
        <Form layout="vertical" onFinish={handleFinish}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="admin" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            登录
          </Button>
        </Form>
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 16, textAlign: 'center' }}>
          首次启动默认账号由后端环境变量初始化
        </Typography.Text>
      </Card>
    </div>
  )
}
