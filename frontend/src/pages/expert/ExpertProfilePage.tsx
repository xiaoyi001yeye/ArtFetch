import { useState } from 'react'
import { Button, Card, Descriptions, Form, Input, message, Modal, Typography } from 'antd'
import { LockOutlined, LogoutOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import * as api from '../../api'

export default function ExpertProfilePage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  const changePassword = async (values: { oldPassword: string; newPassword: string }) => {
    try {
      await api.changePassword(values)
      message.success('密码已修改，请重新登录')
      await logout()
      navigate('/expert/login', { replace: true })
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const exit = async () => {
    await logout()
    navigate('/expert/login', { replace: true })
  }

  return (
    <div className="expert-mobile-stack">
      <Typography.Title level={3} style={{ margin: 0 }}>我的</Typography.Title>
      <Card className="expert-mobile-card">
        <Descriptions column={1}>
          <Descriptions.Item label="账号">{user?.username}</Descriptions.Item>
          <Descriptions.Item label="姓名">{user?.displayName || '—'}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Button className="expert-mobile-touch" icon={<LockOutlined />} onClick={() => setOpen(true)}>修改密码</Button>
      <Button className="expert-mobile-touch" danger icon={<LogoutOutlined />} onClick={exit}>退出登录</Button>
      <Modal title="修改密码" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} okText="保存">
        <Form form={form} layout="vertical" onFinish={changePassword}>
          <Form.Item name="oldPassword" label="原密码" rules={[{ required: true, message: '请输入原密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 8, message: '密码至少 8 位' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
