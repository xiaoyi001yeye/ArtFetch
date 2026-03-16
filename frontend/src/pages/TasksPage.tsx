import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Badge,
  Button,
  Card,
  Col,
  Form,
  Input,
  message,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import {
  CaretRightOutlined,
  DeleteOutlined,
  PauseOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'
import * as api from '../api'
import type { Task, TaskStatus } from '../types'

const STATUS_CONFIG: Record<TaskStatus, { color: string; label: string; badge: 'success' | 'processing' | 'warning' | 'error' | 'default' }> = {
  PENDING:   { color: 'default',   label: '待启动', badge: 'default' },
  RUNNING:   { color: 'blue',      label: '运行中', badge: 'processing' },
  PAUSED:    { color: 'orange',    label: '已暂停', badge: 'warning' },
  COMPLETED: { color: 'green',     label: '已完成', badge: 'success' },
  FAILED:    { color: 'red',       label: '失败',   badge: 'error' },
  CANCELLED: { color: 'default',   label: '已取消', badge: 'default' },
}

export default function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()
  const timerRef = useRef<number>()

  const loadTasks = useCallback(async (p = page) => {
    setLoading(true)
    try {
      const result = await api.listTasks(p, 20)
      setTasks(result.items)
      setTotal(result.total)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    loadTasks()
    // 每5秒自动刷新，更新运行中任务的进度
    timerRef.current = window.setInterval(() => loadTasks(), 5000)
    return () => clearInterval(timerRef.current)
  }, [loadTasks])

  const handleCreate = async (values: { name: string; keyword: string }) => {
    setSubmitting(true)
    try {
      await api.createTask(values)
      message.success('任务创建成功')
      setModalOpen(false)
      form.resetFields()
      loadTasks(0)
      setPage(0)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  const handleAction = async (action: () => Promise<Task>, successMsg: string) => {
    try {
      await action()
      message.success(successMsg)
      loadTasks()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const columns: ColumnsType<Task> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
    },
    {
      title: '任务名称',
      dataIndex: 'name',
      render: (name, record) => (
        <Link to={`/artworks?taskId=${record.id}`}>{name}</Link>
      ),
    },
    {
      title: '关键词',
      dataIndex: 'keyword',
      render: (kw) => <Tag color="purple">{kw}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (status: TaskStatus, record) => {
        const cfg = STATUS_CONFIG[status]
        return (
          <Tooltip title={record.errorMessage}>
            <Badge status={cfg.badge} text={<Tag color={cfg.color}>{cfg.label}</Tag>} />
          </Tooltip>
        )
      },
    },
    {
      title: '进度',
      width: 180,
      render: (_, record) => {
        if (record.totalPages === 0) return <span style={{ color: '#aaa' }}>—</span>
        const pct = Math.round((record.currentPage / record.totalPages) * 100)
        return (
          <Tooltip title={`第 ${record.currentPage + 1} / ${record.totalPages} 页`}>
            <Progress percent={pct} size="small" status={record.status === 'FAILED' ? 'exception' : undefined} />
          </Tooltip>
        )
      },
    },
    {
      title: '已抓取',
      dataIndex: 'artworkCount',
      width: 90,
      render: (count) => <Typography.Text strong>{count}</Typography.Text>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (t) => t?.replace('T', ' ').slice(0, 19),
    },
    {
      title: '操作',
      width: 200,
      render: (_, record) => (
        <Space>
          {record.status === 'PENDING' && (
            <Button size="small" type="primary" icon={<CaretRightOutlined />}
              onClick={() => handleAction(() => api.startTask(record.id), '任务已启动')}>
              启动
            </Button>
          )}
          {record.status === 'RUNNING' && (
            <Button size="small" icon={<PauseOutlined />}
              onClick={() => handleAction(() => api.pauseTask(record.id), '任务已暂停')}>
              暂停
            </Button>
          )}
          {record.status === 'PAUSED' && (
            <Button size="small" type="primary" icon={<CaretRightOutlined />}
              onClick={() => handleAction(() => api.resumeTask(record.id), '任务已恢复')}>
              恢复
            </Button>
          )}
          {(record.status === 'RUNNING' || record.status === 'PAUSED') && (
            <Popconfirm title="确认取消该任务？" onConfirm={() => handleAction(() => api.cancelTask(record.id), '任务已取消')}>
              <Button size="small" danger icon={<StopOutlined />}>取消</Button>
            </Popconfirm>
          )}
          {(record.status === 'FAILED' || record.status === 'CANCELLED' || record.status === 'COMPLETED') && (
            <Button size="small" type="primary" icon={<CaretRightOutlined />}
              onClick={() => handleAction(() => api.startTask(record.id), '任务已重新启动')}>
              重试
            </Button>
          )}
          <Popconfirm title="确认删除该任务及其所有数据？" onConfirm={() => handleAction(async () => { await api.deleteTask(record.id); return {} as Task }, '任务已删除')}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Typography.Title level={4} style={{ margin: 0 }}>检索任务管理</Typography.Title>
        </Col>
        <Col>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadTasks()}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
              新建任务
            </Button>
          </Space>
        </Col>
      </Row>

      <Card>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={tasks}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total,
            onChange: (p) => { setPage(p - 1); loadTasks(p - 1) },
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>

      <Modal
        title="新建检索任务"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields() }}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        okText="创建并保存"
      >
        <Form form={form} layout="vertical" onFinish={handleCreate} style={{ marginTop: 16 }}>
          <Form.Item label="任务名称" name="name" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="例如：印象派艺术品检索" />
          </Form.Item>
          <Form.Item label="检索关键词" name="keyword" rules={[{ required: true, message: '请输入检索关键词' }]}>
            <Input placeholder="例如：monet, impressionism, oil painting" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
