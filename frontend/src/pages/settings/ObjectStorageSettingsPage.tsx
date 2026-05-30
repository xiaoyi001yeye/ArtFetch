import { useEffect, useState } from 'react'
import { Button, Col, Form, Input, message, Modal, Popconfirm, Row, Segmented, Space, Switch, Table, Tag, Typography } from 'antd'
import { CheckCircleOutlined, CloudServerOutlined, EditOutlined, PlusOutlined, ReloadOutlined, ToolOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import * as api from '../../api'
import type { ObjectStorageConfig, ObjectStorageConfigPayload } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'

const REGION_ENDPOINTS: Record<string, string> = {
  'cn-beijing': 'tos-cn-beijing.volces.com',
  'cn-shanghai': 'tos-cn-shanghai.volces.com',
  'cn-guangzhou': 'tos-cn-guangzhou.volces.com',
  'cn-hongkong': 'tos-cn-hongkong.volces.com',
}

type FormValues = ObjectStorageConfigPayload

export default function ObjectStorageSettingsPage() {
  const { hasPermission } = useAuth()
  const canManage = hasPermission(permissions.objectStorageManage)
  const [configs, setConfigs] = useState<ObjectStorageConfig[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ObjectStorageConfig | null>(null)
  const [saving, setSaving] = useState(false)
  const [testingId, setTestingId] = useState<number>()
  const [editingLoadingId, setEditingLoadingId] = useState<number>()
  const [form] = Form.useForm<FormValues>()

  const load = async () => {
    setLoading(true)
    try {
      setConfigs(await api.listObjectStorageConfigs())
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const openCreate = () => {
    setEditing(null)
    form.setFieldsValue({
      name: '火山TOS生产配置',
      provider: 'VOLCENGINE_TOS',
      region: 'cn-beijing',
      endpoint: REGION_ENDPOINTS['cn-beijing'],
      bucket: '',
      pathPrefix: 'artfetch/hd-images/prod',
      accessKey: '',
      secretKey: '',
      networkType: 'PUBLIC',
      uploadEnabled: false,
      migrateEnabled: true,
    } as any)
    setModalOpen(true)
  }

  const openEdit = async (record: ObjectStorageConfig) => {
    setEditingLoadingId(record.id)
    try {
      const detail = await api.getObjectStorageConfigForEdit(record.id)
      setEditing(detail)
      form.setFieldsValue({
        name: detail.name,
        endpoint: detail.endpoint,
        region: detail.region,
        bucket: detail.bucket,
        pathPrefix: detail.pathPrefix,
        accessKey: detail.accessKey || '',
        secretKey: detail.secretKey || '',
        publicBaseUrl: detail.publicBaseUrl,
        networkType: detail.networkType,
        uploadEnabled: detail.uploadEnabled,
        migrateEnabled: detail.migrateEnabled,
      } as any)
      setModalOpen(true)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setEditingLoadingId(undefined)
    }
  }

  const submit = async (values: FormValues) => {
    const payload = { ...values, accessKey: values.accessKey?.trim(), secretKey: values.secretKey?.trim() }
    setSaving(true)
    try {
      if (editing) {
        await api.updateObjectStorageConfig(editing.id, payload)
      } else {
        await api.createObjectStorageConfig(payload)
      }
      message.success('对象存储配置已保存')
      setModalOpen(false)
      load()
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  const test = async (id: number) => {
    setTestingId(id)
    try {
      const result = await api.testObjectStorageConfig(id)
      if (result.success) {
        message.success(result.message)
      } else {
        message.error(result.message)
      }
      load()
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setTestingId(undefined)
    }
  }

  const enable = async (id: number) => {
    try {
      await api.enableObjectStorageConfig(id)
      message.success('已启用对象存储配置')
      load()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const disable = async (id: number) => {
    try {
      await api.disableObjectStorageConfig(id)
      message.success('已禁用对象存储配置')
      load()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const columns: ColumnsType<ObjectStorageConfig> = [
    { title: '名称', dataIndex: 'name', width: 180, render: (v, r) => <Space><CloudServerOutlined />{v}{r.enabled && <Tag color="green">启用</Tag>}</Space> },
    { title: '服务商', dataIndex: 'provider', width: 130, render: () => <Tag color="volcano">火山引擎 TOS</Tag> },
    { title: 'Region', dataIndex: 'region', width: 130 },
    { title: 'Endpoint', dataIndex: 'endpoint', ellipsis: true },
    { title: 'Bucket', dataIndex: 'bucket', width: 160 },
    { title: '路径前缀', dataIndex: 'pathPrefix', width: 220, render: (v) => v || '—' },
    { title: 'AK', dataIndex: 'accessKeyMasked', width: 130 },
    {
      title: '开关',
      width: 170,
      render: (_, r) => (
        <Space size={4} wrap>
          {r.uploadEnabled ? <Tag color="blue">新图上传</Tag> : <Tag>新图本地</Tag>}
          {r.migrateEnabled ? <Tag color="purple">允许迁移</Tag> : <Tag>禁迁移</Tag>}
        </Space>
      ),
    },
    {
      title: '测试',
      width: 180,
      render: (_, r) => r.lastTestStatus
        ? <Tag color={r.lastTestStatus === 'SUCCESS' ? 'green' : 'red'}>{r.lastTestMessage || r.lastTestStatus}</Tag>
        : '未测试',
    },
    {
      title: '操作',
      fixed: 'right',
      width: 250,
      render: (_, record) => (
        <Space>
          {canManage && <Button size="small" icon={<EditOutlined />} loading={editingLoadingId === record.id} onClick={() => openEdit(record)}>编辑</Button>}
          {canManage && <Button size="small" icon={<ToolOutlined />} loading={testingId === record.id} onClick={() => test(record.id)}>测试</Button>}
          {canManage && (record.enabled ? (
            <Popconfirm title="禁用当前对象存储配置？" onConfirm={() => disable(record.id)}>
              <Button size="small">禁用</Button>
            </Popconfirm>
          ) : (
            <Button size="small" type="primary" icon={<CheckCircleOutlined />} onClick={() => enable(record.id)}>启用</Button>
          ))}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Typography.Title level={4} style={{ margin: 0 }}>对象存储配置</Typography.Title>
        </Col>
        <Col>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
            {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增配置</Button>}
          </Space>
        </Col>
      </Row>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={configs}
        loading={loading}
        pagination={false}
        scroll={{ x: 1400 }}
      />

      <Modal
        title={editing ? '编辑火山 TOS 配置' : '新增火山 TOS 配置'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText="保存"
        confirmLoading={saving}
        width={760}
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="配置名称" rules={[{ required: true, message: '请输入配置名称' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="服务商">
                <Input value="火山引擎 TOS" disabled />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="region" label="Region" rules={[{ required: true, message: '请输入 Region' }]}>
                <Input
                  placeholder="cn-beijing"
                  onBlur={(e) => {
                    const region = e.target.value.trim()
                    if (!editing && REGION_ENDPOINTS[region]) {
                      form.setFieldValue('endpoint', REGION_ENDPOINTS[region])
                    }
                  }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="networkType" label="网络类型" rules={[{ required: true }]}>
                <Segmented block options={[{ label: '外网', value: 'PUBLIC' }, { label: '内网', value: 'INTERNAL' }]} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="endpoint" label="Endpoint" rules={[{ required: true, message: '请输入 Endpoint' }]}>
                <Input placeholder="tos-cn-beijing.volces.com" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="bucket" label="Bucket" rules={[{ required: true, message: '请输入 Bucket' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="pathPrefix" label="路径前缀">
                <Input placeholder="artfetch/hd-images/prod" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="accessKey"
                label="Access Key"
                rules={editing ? [] : [{ required: true, message: '请输入 Access Key' }]}
                extra={editing ? '留空则保留原 Access Key' : undefined}
              >
                <Input.Password />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="secretKey"
                label="Secret Key"
                rules={editing ? [] : [{ required: true, message: '请输入 Secret Key' }]}
                extra={editing ? '留空则保留原 Secret Key' : undefined}
              >
                <Input.Password />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="publicBaseUrl" label="公开访问域名/CDN（可选）">
                <Input placeholder="第一期仅保存备用，不直接暴露给前端" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="uploadEnabled" label="允许新高清图上传对象存储" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="migrateEnabled" label="允许迁移任务使用" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  )
}
