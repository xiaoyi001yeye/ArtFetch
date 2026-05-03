import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Checkbox, Col, Form, Input, message, Modal, Popconfirm, Row, Space, Table, Tag, Typography } from 'antd'
import { EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import * as api from '../../api'
import type { AuthPermission, AuthRole } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { permissions as permissionCodes } from '../../auth/permissions'

type RoleFormValues = {
  code: string
  name: string
  description?: string
  permissions: string[]
}

export default function RolesPage() {
  const { hasPermission } = useAuth()
  const [roles, setRoles] = useState<AuthRole[]>([])
  const [permissions, setPermissions] = useState<AuthPermission[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AuthRole | null>(null)
  const [form] = Form.useForm<RoleFormValues>()

  const load = async () => {
    setLoading(true)
    try {
      const [roleResult, permissionResult] = await Promise.all([api.listRoles(0, 200), api.listPermissions()])
      setRoles(roleResult.items)
      setPermissions(permissionResult)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const groupedPermissions = useMemo(() => {
    const groups = new Map<string, AuthPermission[]>()
    permissions.forEach((permission) => {
      const list = groups.get(permission.module) || []
      list.push(permission)
      groups.set(permission.module, list)
    })
    return Array.from(groups.entries())
  }, [permissions])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (role: AuthRole) => {
    setEditing(role)
    form.setFieldsValue({
      code: role.code,
      name: role.name,
      description: role.description,
      permissions: role.permissions,
    })
    setModalOpen(true)
  }

  const submitRole = async (values: RoleFormValues) => {
    try {
      if (editing) {
        await api.updateRole(editing.id, { name: values.name, description: values.description })
        await api.updateRolePermissions(editing.id, values.permissions || [])
        message.success('角色已更新')
      } else {
        await api.createRole({
          code: values.code,
          name: values.name,
          description: values.description,
          permissions: values.permissions || [],
        })
        message.success('角色已创建')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const columns: ColumnsType<AuthRole> = [
    { title: '角色编码', dataIndex: 'code', width: 150, render: (v) => <Tag color="blue">{v}</Tag> },
    { title: '角色名称', dataIndex: 'name', width: 160 },
    { title: '描述', dataIndex: 'description', render: (v) => v || '—' },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 100,
      render: (enabled) => enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '权限数',
      dataIndex: 'permissions',
      width: 90,
      render: (items: string[]) => items.length,
    },
    {
      title: '内置',
      dataIndex: 'builtIn',
      width: 90,
      render: (builtIn) => builtIn ? <Tag color="purple">内置</Tag> : <Tag>自定义</Tag>,
    },
    {
      title: '操作',
      width: 190,
      render: (_, record) => (
        <Space>
          {hasPermission(permissionCodes.roleUpdate) && (
            <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          )}
          {hasPermission(permissionCodes.roleDisable) && (
            <Popconfirm
              title={record.enabled ? '确认停用该角色？' : '确认启用该角色？'}
              onConfirm={async () => {
                await api.updateRoleStatus(record.id, !record.enabled)
                message.success('状态已更新')
                load()
              }}
            >
              <Button size="small" danger={record.enabled}>
                {record.enabled ? '停用' : '启用'}
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
        <Col><Typography.Title level={4} style={{ margin: 0 }}>角色权限</Typography.Title></Col>
        <Col>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
            {hasPermission(permissionCodes.roleCreate) && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建角色</Button>}
          </Space>
        </Col>
      </Row>

      <Card>
        <Table rowKey="id" columns={columns} dataSource={roles} loading={loading} pagination={false} />
      </Card>

      <Modal
        title={editing ? '编辑角色' : '新建角色'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText="保存"
        width={760}
      >
        <Form form={form} layout="vertical" onFinish={submitRole}>
          <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input disabled={Boolean(editing)} placeholder="例如 DATA_OPERATOR" />
          </Form.Item>
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="permissions" label="权限">
            <Checkbox.Group style={{ width: '100%' }}>
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                {groupedPermissions.map(([module, items]) => (
                  <Card key={module} size="small" title={module}>
                    <Row gutter={[8, 8]}>
                      {items.map((permission) => (
                        <Col span={12} key={permission.code}>
                          <Checkbox value={permission.code}>{permission.name} ({permission.code})</Checkbox>
                        </Col>
                      ))}
                    </Row>
                  </Card>
                ))}
              </Space>
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
