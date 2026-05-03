import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Col, Form, Input, message, Modal, Popconfirm, Row, Select, Space, Table, Tag, Typography } from 'antd'
import { EditOutlined, KeyOutlined, PlusOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import * as api from '../../api'
import type { AuthRole, AuthUser } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'

type UserFormValues = {
  username: string
  password?: string
  displayName: string
  email?: string
  phone?: string
  roles: string[]
}

export default function UsersPage() {
  const { hasPermission } = useAuth()
  const [users, setUsers] = useState<AuthUser[]>([])
  const [roles, setRoles] = useState<AuthRole[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AuthUser | null>(null)
  const [roleEditing, setRoleEditing] = useState<AuthUser | null>(null)
  const [resetting, setResetting] = useState<AuthUser | null>(null)
  const [form] = Form.useForm<UserFormValues>()
  const [roleForm] = Form.useForm<{ roles: string[] }>()
  const [passwordForm] = Form.useForm<{ newPassword: string }>()

  const loadUsers = async (p = page) => {
    setLoading(true)
    try {
      const result = await api.listUsers(p, 20)
      setUsers(result.items)
      setTotal(result.total)
      setPage(p)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadUsers(0)
    api.listRoles(0, 200).then((r) => setRoles(r.items)).catch(() => {})
  }, [])

  const roleOptions = useMemo(() => roles.map((role) => ({ value: role.code, label: `${role.name} (${role.code})` })), [roles])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (user: AuthUser) => {
    setEditing(user)
    form.setFieldsValue({
      username: user.username,
      displayName: user.displayName,
      email: user.email,
      phone: user.phone,
      roles: user.roles,
    })
    setModalOpen(true)
  }

  const submitUser = async (values: UserFormValues) => {
    try {
      if (editing) {
        await api.updateUser(editing.id, values)
        message.success('用户已更新')
      } else {
        await api.createUser({
          username: values.username,
          password: values.password || '',
          displayName: values.displayName,
          email: values.email,
          phone: values.phone,
          roles: values.roles,
        })
        message.success('用户已创建')
      }
      setModalOpen(false)
      loadUsers()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const submitRoles = async (values: { roles: string[] }) => {
    if (!roleEditing) return
    try {
      await api.updateUserRoles(roleEditing.id, values.roles)
      message.success('角色已更新，用户需要重新登录')
      setRoleEditing(null)
      loadUsers()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const submitPassword = async (values: { newPassword: string }) => {
    if (!resetting) return
    try {
      await api.resetUserPassword(resetting.id, values.newPassword)
      message.success('密码已重置')
      setResetting(null)
      passwordForm.resetFields()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const columns: ColumnsType<AuthUser> = [
    { title: '用户名', dataIndex: 'username', width: 150 },
    { title: '显示名称', dataIndex: 'displayName', width: 150 },
    { title: '邮箱', dataIndex: 'email', render: (v) => v || '—' },
    { title: '手机', dataIndex: 'phone', width: 130, render: (v) => v || '—' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) => status === 'ENABLED' ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '角色',
      dataIndex: 'roles',
      render: (items: string[]) => <Space size={4} wrap>{items.map((role) => <Tag key={role} color="blue">{role}</Tag>)}</Space>,
    },
    {
      title: '最近登录',
      dataIndex: 'lastLoginAt',
      width: 170,
      render: (v) => v?.replace('T', ' ').slice(0, 19) || '—',
    },
    {
      title: '操作',
      width: 300,
      render: (_, record) => (
        <Space>
          {hasPermission(permissions.userUpdate) && (
            <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          )}
          {hasPermission(permissions.userUpdate) && (
            <Button
              size="small"
              icon={<SafetyCertificateOutlined />}
              onClick={() => {
                setRoleEditing(record)
                roleForm.setFieldsValue({ roles: record.roles })
              }}
            >
              角色
            </Button>
          )}
          {hasPermission(permissions.userUpdate) && (
            <Button size="small" icon={<KeyOutlined />} onClick={() => setResetting(record)}>重置密码</Button>
          )}
          {hasPermission(permissions.userDisable) && (
            <Popconfirm
              title={record.status === 'ENABLED' ? '确认停用该用户？' : '确认启用该用户？'}
              onConfirm={async () => {
                await api.updateUserStatus(record.id, record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
                message.success('状态已更新')
                loadUsers()
              }}
            >
              <Button size="small" danger={record.status === 'ENABLED'}>
                {record.status === 'ENABLED' ? '停用' : '启用'}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col><Typography.Title level={4} style={{ margin: 0 }}>用户管理</Typography.Title></Col>
        <Col>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadUsers()}>刷新</Button>
            {hasPermission(permissions.userCreate) && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建用户</Button>}
          </Space>
        </Col>
      </Row>

      <Card>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={users}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total,
            onChange: (p) => loadUsers(p - 1),
            showTotal: (t) => `共 ${t} 个用户`,
          }}
        />
      </Card>

      <Modal title={editing ? '编辑用户' : '新建用户'} open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} okText="保存">
        <Form form={form} layout="vertical" onFinish={submitUser}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input disabled={Boolean(editing)} />
          </Form.Item>
          {!editing && (
            <Form.Item name="password" label="初始密码" rules={[{ required: true, message: '请输入初始密码' }, { min: 8, message: '密码至少 8 位' }]}>
              <Input.Password />
            </Form.Item>
          )}
          <Form.Item name="displayName" label="显示名称" rules={[{ required: true, message: '请输入显示名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱"><Input /></Form.Item>
          <Form.Item name="phone" label="手机号"><Input /></Form.Item>
          {!editing && (
            <Form.Item name="roles" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
              <Select mode="multiple" options={roleOptions} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal title="分配角色" open={Boolean(roleEditing)} onCancel={() => setRoleEditing(null)} onOk={() => roleForm.submit()} okText="保存">
        <Form form={roleForm} layout="vertical" onFinish={submitRoles}>
          <Form.Item name="roles" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select mode="multiple" options={roleOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="重置密码" open={Boolean(resetting)} onCancel={() => setResetting(null)} onOk={() => passwordForm.submit()} okText="重置">
        <Form form={passwordForm} layout="vertical" onFinish={submitPassword}>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 8, message: '密码至少 8 位' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
