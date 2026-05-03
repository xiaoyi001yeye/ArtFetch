import { useEffect, useState } from 'react'
import { Button, Card, Col, Form, Input, message, Row, Select, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import * as api from '../../api'
import type { AuditLog } from '../../types'

type AuditQuery = {
  username?: string
  action?: string
  success?: boolean
}

export default function AuditLogsPage() {
  const [logs, setLogs] = useState<AuditLog[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState<AuditQuery>({})
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()

  const load = async (p = page, nextQuery = query) => {
    setLoading(true)
    try {
      const result = await api.listAuditLogs({ ...nextQuery, page: p, size: 20 })
      setLogs(result.items)
      setTotal(result.total)
      setPage(p)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(0)
  }, [])

  const columns: ColumnsType<AuditLog> = [
    { title: '时间', dataIndex: 'createdAt', width: 170, render: (v) => v?.replace('T', ' ').slice(0, 19) },
    { title: '用户', dataIndex: 'username', width: 130, render: (v) => v || '—' },
    { title: '动作', dataIndex: 'action', width: 210, render: (v) => <Tag color="blue">{v}</Tag> },
    {
      title: '结果',
      dataIndex: 'success',
      width: 90,
      render: (success) => success ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>,
    },
    { title: '资源', width: 180, render: (_, r) => r.resourceType ? `${r.resourceType}${r.resourceId ? ` #${r.resourceId}` : ''}` : '—' },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v) => v ? <Tooltip title={v}>{v}</Tooltip> : '—' },
    { title: 'IP', dataIndex: 'ipAddress', width: 140, render: (v) => v || '—' },
    { title: '错误', dataIndex: 'errorMessage', ellipsis: true, render: (v) => v ? <Tooltip title={v}><Typography.Text type="danger">{v}</Typography.Text></Tooltip> : '—' },
  ]

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col><Typography.Title level={4} style={{ margin: 0 }}>审计日志</Typography.Title></Col>
        <Col><Button icon={<ReloadOutlined />} onClick={() => load()}>刷新</Button></Col>
      </Row>

      <Card style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" onFinish={(values) => { setQuery(values); load(0, values) }}>
          <Form.Item name="username" label="用户">
            <Input allowClear placeholder="用户名" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="action" label="动作">
            <Input allowClear placeholder="action" style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="success" label="结果">
            <Select allowClear placeholder="全部" style={{ width: 120 }}>
              <Select.Option value={true}>成功</Select.Option>
              <Select.Option value={false}>失败</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>检索</Button>
              <Button onClick={() => { form.resetFields(); setQuery({}); load(0, {}) }}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={logs}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total,
            onChange: (p) => load(p - 1),
            showTotal: (t) => `共 ${t} 条日志`,
          }}
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  )
}
