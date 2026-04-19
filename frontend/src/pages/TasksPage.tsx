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
  Select,
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
import type { Task, TaskStatus, TaskType } from '../types'

const STATUS_CONFIG: Record<TaskStatus, { color: string; label: string; badge: 'success' | 'processing' | 'warning' | 'error' | 'default' }> = {
  PENDING:   { color: 'default',   label: '待启动', badge: 'default' },
  RUNNING:   { color: 'blue',      label: '运行中', badge: 'processing' },
  PAUSED:    { color: 'orange',    label: '已暂停', badge: 'warning' },
  COMPLETED: { color: 'green',     label: '已完成', badge: 'success' },
  FAILED:    { color: 'red',       label: '失败',   badge: 'error' },
  CANCELLED: { color: 'default',   label: '已取消', badge: 'default' },
}

const TASK_TYPE_CONFIG: Record<TaskType, { color: string; label: string }> = {
  SEARCH: { color: 'blue', label: '检索任务' },
  ORIGINAL_IMAGE: { color: 'gold', label: '补原图任务' },
  HD_IMAGE: { color: 'geekblue', label: '补超清图任务' },
  TRANSACTION_PRICE: { color: 'magenta', label: '补成交价任务' },
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

  const selectedTaskType = Form.useWatch('taskType', form) as TaskType | undefined

  const handleCreate = async (values: { name: string; keyword?: string; taskType: TaskType; targetTaskId?: number }) => {
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

  const formatMs = (ms?: number) => {
    if (!ms) return '—'
    if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`
    return `${ms}ms`
  }

  const formatPercent = (ratio?: number) => {
    if (ratio == null) return '—'
    return `${(ratio * 100).toFixed(1)}%`
  }

  const formatRate = (value?: number) => {
    if (!value) return '—'
    return `${value.toFixed(1)}条/分`
  }

  const formatDuration = (ms?: number | null) => {
    if (ms == null) return '—'
    if (ms <= 0) return '0分'

    const totalMinutes = Math.ceil(ms / 60000)
    const days = Math.floor(totalMinutes / (24 * 60))
    const hours = Math.floor((totalMinutes % (24 * 60)) / 60)
    const minutes = totalMinutes % 60

    if (days > 0) {
      return hours > 0 ? `${days}天${hours}小时` : `${days}天`
    }
    if (hours > 0) {
      return minutes > 0 ? `${hours}小时${minutes}分` : `${hours}小时`
    }
    return `${minutes}分`
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
      title: '类型',
      dataIndex: 'taskType',
      width: 110,
      render: (taskType: TaskType) => {
        const cfg = TASK_TYPE_CONFIG[taskType] || TASK_TYPE_CONFIG.SEARCH
        return <Tag color={cfg.color}>{cfg.label}</Tag>
      },
    },
    {
      title: '关键词',
      render: (_, record) => (
        <Space size={4} wrap>
          <Tag color="purple">{record.keyword}</Tag>
          {(record.taskType === 'ORIGINAL_IMAGE' || record.taskType === 'HD_IMAGE' || record.taskType === 'TRANSACTION_PRICE') && record.targetTaskId && (
            <Tag color="cyan">
              目标任务 #{record.targetTaskId}{record.targetTaskName ? ` ${record.targetTaskName}` : ''}
            </Tag>
          )}
        </Space>
      ),
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
        const isSupplementTask =
          record.taskType === 'ORIGINAL_IMAGE' || record.taskType === 'HD_IMAGE' || record.taskType === 'TRANSACTION_PRICE'
        return (
          <Tooltip title={isSupplementTask
            ? `已处理 ${record.currentPage} / ${record.totalPages} 条`
            : `第 ${record.currentPage + 1} / ${record.totalPages} 页`}
          >
            <Progress percent={pct} size="small" status={record.status === 'FAILED' ? 'exception' : undefined} />
          </Tooltip>
        )
      },
    },
    {
      title: '已抓取',
      width: 90,
      render: (_, record) => (
        <Typography.Text strong>
          {record.taskType === 'ORIGINAL_IMAGE' || record.taskType === 'HD_IMAGE'
            ? record.totalFetched
            : record.artworkCount}
        </Typography.Text>
      ),
    },
    {
      title: '性能',
      width: 260,
      render: (_, record) => (record.taskType === 'ORIGINAL_IMAGE' || record.taskType === 'HD_IMAGE' || record.taskType === 'TRANSACTION_PRICE') ? (
        <Space direction="vertical" size={0}>
          <Typography.Text type="secondary">
            目标 {record.targetTaskName || (record.targetTaskId ? `任务 #${record.targetTaskId}` : '—')}
          </Typography.Text>
          <Typography.Text type="secondary">
            已处理 {record.currentPage || 0} / {record.totalPages || 0}
          </Typography.Text>
          <Typography.Text type="secondary">
            {record.taskType === 'TRANSACTION_PRICE'
              ? `已补充 ${record.totalFetched || 0}`
              : `已下载 ${record.totalFetched || 0}`}
          </Typography.Text>
          <Typography.Text type="secondary">
            并发 {record.detailFetchConcurrency || 1} / 吞吐 {formatRate(record.lastPageItemsPerMinute)}
          </Typography.Text>
          <Typography.Text type="secondary">
            预计剩余 {formatDuration(record.estimatedRemainingMs)}
          </Typography.Text>
        </Space>
      ) : (
        <Tooltip title={record.concurrencyAdvice || '暂无建议'}>
          <Space direction="vertical" size={0}>
            <Typography.Text type="secondary">
              并发 {record.detailFetchConcurrency || 1} / 待重试 {record.pendingFailureCount || 0}
            </Typography.Text>
            <Typography.Text type="secondary">
              均值 {formatMs(record.avgDetailLatencyMs)} / P95 {formatMs(record.p95DetailLatencyMs)}
            </Typography.Text>
            <Typography.Text type="secondary">
              失败率 {formatPercent(record.detailFailureRate)} / 吞吐 {formatRate(record.lastPageItemsPerMinute)}
            </Typography.Text>
            <Typography.Text type="secondary">
              预计剩余 {formatDuration(record.estimatedRemainingMs)}
            </Typography.Text>
          </Space>
        </Tooltip>
      ),
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
          <Typography.Title level={4} style={{ margin: 0 }}>任务管理</Typography.Title>
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
        title="新建任务"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields() }}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        okText="创建并保存"
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          style={{ marginTop: 16 }}
          initialValues={{ taskType: 'SEARCH' satisfies TaskType }}
        >
          <Form.Item label="任务类型" name="taskType" rules={[{ required: true, message: '请选择任务类型' }]}>
            <Select
              options={[
                { value: 'SEARCH', label: '检索任务' },
                { value: 'ORIGINAL_IMAGE', label: '补充原始图片任务' },
                { value: 'HD_IMAGE', label: '补充超清无损图任务' },
                { value: 'TRANSACTION_PRICE', label: '补充成交价任务' },
              ]}
            />
          </Form.Item>
          <Form.Item label="任务名称" name="name" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder={
              selectedTaskType === 'ORIGINAL_IMAGE'
                ? '例如：张大千原图补充'
                : selectedTaskType === 'HD_IMAGE'
                  ? '例如：张大千超清无损图补充'
                : selectedTaskType === 'TRANSACTION_PRICE'
                  ? '例如：张大千成交价补充'
                  : '例如：印象派艺术品检索'
            } />
          </Form.Item>
          {selectedTaskType === 'ORIGINAL_IMAGE' || selectedTaskType === 'HD_IMAGE' || selectedTaskType === 'TRANSACTION_PRICE' ? (
            <Form.Item
              label="目标检索任务"
              name="targetTaskId"
              rules={[{ required: true, message: '请选择目标检索任务' }]}
            >
              <Select
                placeholder="选择一个已存在的检索任务"
                options={tasks
                  .filter((task) => task.taskType === 'SEARCH')
                  .map((task) => ({
                    value: task.id,
                    label: `#${task.id} ${task.name}`,
                  }))}
              />
            </Form.Item>
          ) : (
            <Form.Item label="检索关键词" name="keyword" rules={[{ required: true, message: '请输入检索关键词' }]}>
              <Input placeholder="例如：monet, impressionism, oil painting" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  )
}
