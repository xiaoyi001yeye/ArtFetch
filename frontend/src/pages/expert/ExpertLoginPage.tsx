import { Button, Card, Form, Input, message, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Navigate, useNavigate } from 'react-router-dom'
import ArtFetchMark from '../../components/ArtFetchMark'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import '../../styles/expert-mobile.css'

export default function ExpertLoginPage() {
  const { user, login, logout } = useAuth()
  const navigate = useNavigate()

  if (user?.permissions.includes(permissions.evaluationReviewAssignedView)) {
    return <Navigate to="/expert/projects" replace />
  }

  const handleFinish = async (values: { username: string; password: string }) => {
    try {
      const current = await login(values.username, values.password)
      if (!current.permissions.includes(permissions.evaluationReviewAssignedView)) {
        await logout()
        message.error('当前账号未开通专家评估权限')
        return
      }
      message.success('登录成功')
      navigate('/expert/projects', { replace: true })
    } catch (e: any) {
      message.error(e.message)
    }
  }

  return (
    <div className="expert-mobile-login">
      <Card className="expert-mobile-login-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12, marginBottom: 8 }}>
          <ArtFetchMark size={40} />
          <Typography.Title level={3} style={{ margin: 0 }}>ArtFetch</Typography.Title>
        </div>
        <Typography.Title level={4} style={{ margin: '0 0 24px', textAlign: 'center', fontWeight: 500 }}>
          专家评估
        </Typography.Title>
        <Form layout="vertical" onFinish={handleFinish}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} autoComplete="username" placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" placeholder="请输入密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>登录</Button>
        </Form>
      </Card>
    </div>
  )
}
