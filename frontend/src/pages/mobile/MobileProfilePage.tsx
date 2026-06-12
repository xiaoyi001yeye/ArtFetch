import { Button, Form, Input, message, Modal, Space, Typography } from 'antd'
import { DesktopOutlined, LogoutOutlined, LockOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import { setStoredViewMode } from '../../mobileView'
import * as api from '../../api'

export default function MobileProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [form] = Form.useForm()

  const submitPassword = async (values: { oldPassword: string; newPassword: string }) => {
    try {
      await api.changePassword(values)
      message.success('密码已修改，请重新登录')
      setPasswordOpen(false)
      form.resetFields()
      await logout()
      navigate('/login', { replace: true })
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const switchDesktop = () => {
    setStoredViewMode('desktop')
    navigate('/', { replace: true })
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <MobileDataLayout title="我的账号">
      <section className="mobile-profile-panel">
        <Typography.Text type="secondary">当前账号</Typography.Text>
        <Typography.Title level={4}>{user?.displayName || user?.username}</Typography.Title>
        <div className="mobile-profile-username">{user?.username}</div>
      </section>

      <Space direction="vertical" className="mobile-profile-actions">
        <Button block icon={<LockOutlined />} onClick={() => setPasswordOpen(true)}>修改密码</Button>
        <Button block icon={<DesktopOutlined />} onClick={switchDesktop}>切换到桌面版</Button>
        <Button block danger icon={<LogoutOutlined />} onClick={handleLogout}>退出登录</Button>
      </Space>

      <Modal
        title="修改密码"
        open={passwordOpen}
        onCancel={() => setPasswordOpen(false)}
        onOk={() => form.submit()}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" onFinish={submitPassword}>
          <Form.Item name="oldPassword" label="原密码" rules={[{ required: true, message: '请输入原密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 8, message: '密码至少 8 位' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </MobileDataLayout>
  )
}
