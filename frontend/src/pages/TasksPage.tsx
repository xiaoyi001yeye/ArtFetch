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
import { useAuth } from '../auth/AuthContext'
import { permissions } from '../auth/permissions'

type CreateTaskValues = {
  name: string
  keyword?: string
  keywordsText?: string
  taskType: TaskType
  targetTaskId?: number
}

const STATUS_CONFIG: Record<TaskStatus, { color: string; label: string; badge: 'success' | 'processing' | 'warning' | 'error' | 'default' }> = {
  PENDING: { color: 'default', label: '待启动', badge: 'default' },
  RUNNING: { color: 'blue', label: '运行中', badge: 'processing' },
  PAUSED: { color: 'orange', label: '已暂停', badge: 'warning' },
  COMPLETED: { color: 'green', label: '已完成', badge: 'success' },
  FAILED: { color: 'red', label: '失败', badge: 'error' },
  CANCELLED: { color: 'default', label: '已取消', badge: 'default' },
}

const TASK_TYPE_CONFIG: Record<TaskType, { color: string; label: string }> = {
  SEARCH: { color: 'blue', label: '检索任务' },
  SEARCH_BATCH: { color: 'purple', label: '批量检索任务' },
  ORIGINAL_IMAGE: { color: 'gold', label: '补原图任务' },
  HD_IMAGE: { color: 'geekblue', label: '补超清图任务' },
  TRANSACTION_PRICE: { color: 'magenta', label: '补成交价任务' },
  DESCRIPTION: { color: 'cyan', label: '补描述任务' },
}

const SUPPLEMENT_TASK_TYPES: TaskType[] = ['ORIGINAL_IMAGE', 'HD_IMAGE', 'TRANSACTION_PRICE', 'DESCRIPTION']

const parseBatchKeywords = (value?: string) =>
  (value || '')
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)

const isSupplementTaskType = (taskType?: TaskType) =>
  taskType != null && SUPPLEMENT_TASK_TYPES.includes(taskType)

const buildSearchTaskLabel = (task: Task) =>
  task.parentTaskName
    ? `#${task.id} ${task.parentTaskName} / ${task.keyword}`
    : `#${task.id} ${task.name}`

export default function TasksPage() {
  const { hasPermission } = useAuth()
  const [tasks, setTasks] = useState<Task[]>([])
  const [searchTasks, setSearchTasks] = useState<Task[]>([])
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

  const loadSearchTasks = useCallback(async () => {
    try {
      const result = await api.listTasks(0, 500)
      setSearchTasks(result.items.filter((task) => task.taskType === 'SEARCH'))
    } catch {
    }
  }, [])

  useEffect(() => {
    loadTasks()
    loadSearchTasks()
    timerRef.current = window.setInterval(() => loadTasks(), 5000)
    return () => clearInterval(timerRef.current)
  }, [loadSearchTasks, loadTasks])

  const selectedTaskType = Form.useWatch('taskType', form) as TaskType | undefined
  const searchTaskOptions = searchTasks
    .map((task) => ({
      value: task.id,
      label: buildSearchTaskLabel(task),
    }))

  const handleCreate = async (values: CreateTaskValues) => {
    const payload: Parameters<typeof api.createTask>[0] = {
      name: values.name.trim(),
      taskType: values.taskType,
    }

    if (values.taskType === 'SEARCH_BATCH') {
      payload.keywords = parseBatchKeywords(values.keywordsText)
    } else if (isSupplementTaskType(values.taskType)) {
      payload.targetTaskId = values.targetTaskId
    } else {
      payload.keyword = values.keyword?.trim()
    }

    setSubmitting(true)
    try {
      await api.createTask(payload)
      message.success('任务创建成功')
      setModalOpen(false)
      form.resetFields()
      loadTasks(0)
      loadSearchTasks()
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
        <Space size={4} wrap>
          {record.taskType === 'SEARCH_BATCH' ? (
            <Typography.Text strong>{name}</Typography.Text>
          ) : (
            <Link to={`/artworks?taskId=${record.id}`}>{name}</Link>
          )}
          {record.parentTaskId && (
            <Tag color="purple">
              批量 #{record.parentTaskId}{record.parentTaskName ? ` ${record.parentTaskName}` : ''}
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'taskType',
      width: 120,
      render: (taskType: TaskType) => {
        const cfg = TASK_TYPE_CONFIG[taskType] || TASK_TYPE_CONFIG.SEARCH
        return <Tag color={cfg.color}>{cfg.label}</Tag>
      },
    },
    {
      title: '关键词',
      render: (_, record) => (
        <Space size={4} wrap>
          <Tooltip title={record.keyword}>
            <Tag color="purple">{record.keyword}</Tag>
          </Tooltip>
          {isSupplementTaskType(record.taskType) && record.targetTaskId && (
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

        if (record.taskType === 'SEARCH_BATCH') {
          return (
            <Tooltip title={`已完成 ${record.currentPage} / ${record.totalPages} 个目标`}>
              <Progress percent={pct} size="small" status={record.status === 'FAILED' ? 'exception' : undefined} />
            </Tooltip>
          )
        }

        if (isSupplementTaskType(record.taskType)) {
          return (
            <Tooltip title={`已处理 ${record.currentPage} / ${record.totalPages} 条`}>
              <Progress percent={pct} size="small" status={record.status === 'FAILED' ? 'exception' : undefined} />
            </Tooltip>
          )
        }

        const currentDisplayPage =
          record.status === 'COMPLETED'
            ? record.totalPages
            : Math.min(record.currentPage + 1, record.totalPages)

        return (
          <Tooltip title={`第 ${currentDisplayPage} / ${record.totalPages} 页`}>
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
          {record.taskType === 'ORIGINAL_IMAGE' || record.taskType === 'HD_IMAGE' || record.taskType === 'DESCRIPTION'
            ? record.totalFetched
            : record.artworkCount}
        </Typography.Text>
      ),
    },
    {
      title: '性能',
      width: 280,
      render: (_, record) => {
        if (record.taskType === 'SEARCH_BATCH') {
          const batchStatusText =
            record.errorMessage ||
            (record.status === 'COMPLETED'
              ? '全部目标已完成'
              : record.status === 'RUNNING'
                ? '批量任务执行中'
                : record.status === 'PAUSED'
                  ? '批量任务已暂停'
                  : record.status === 'CANCELLED'
                    ? '批量任务已取消'
                    : '等待启动后自动按目标串行执行')

          return (
            <Tooltip title={batchStatusText}>
              <Space direction="vertical" size={0}>
                <Typography.Text type="secondary">
                  目标完成 {record.currentPage || 0} / {record.totalPages || 0}
                </Typography.Text>
                <Typography.Text type="secondary">
                  累计拍品 {record.artworkCount || 0} / 待重试 {record.pendingFailureCount || 0}
                </Typography.Text>
                <Typography.Text type="secondary">
                  {batchStatusText}
                </Typography.Text>
              </Space>
            </Tooltip>
          )
        }

        if (isSupplementTaskType(record.taskType)) {
          return (
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
                  : record.taskType === 'DESCRIPTION'
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
          )
        }

        return (
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
        )
      },
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
      render: (_, record) => {
        const actions = [
          record.status === 'PENDING' && hasPermission(permissions.taskStart) && (
            <Button
              key="start"
              size="small"
              type="primary"
              icon={<CaretRightOutlined />}
              onClick={() => handleAction(() => api.startTask(record.id), '任务已启动')}
            >
              启动
            </Button>
          ),
          record.status === 'RUNNING' && hasPermission(permissions.taskPause) && (
            <Button
              key="pause"
              size="small"
              icon={<PauseOutlined />}
              onClick={() => handleAction(() => api.pauseTask(record.id), '任务已暂停')}
            >
              暂停
            </Button>
          ),
          record.status === 'PAUSED' && hasPermission(permissions.taskResume) && (
            <Button
              key="resume"
              size="small"
              type="primary"
              icon={<CaretRightOutlined />}
              onClick={() => handleAction(() => api.resumeTask(record.id), '任务已恢复')}
            >
              恢复
            </Button>
          ),
          (record.status === 'RUNNING' || record.status === 'PAUSED') && hasPermission(permissions.taskCancel) && (
            <Popconfirm key="cancel" title="确认取消该任务？" onConfirm={() => handleAction(() => api.cancelTask(record.id), '任务已取消')}>
              <Button size="small" danger icon={<StopOutlined />}>取消</Button>
            </Popconfirm>
          ),
          (record.status === 'FAILED' || record.status === 'CANCELLED' || record.status === 'COMPLETED') && hasPermission(permissions.taskStart) && (
            <Button
              key="retry"
              size="small"
              type="primary"
              icon={<CaretRightOutlined />}
              onClick={() => handleAction(() => api.startTask(record.id), '任务已重新启动')}
            >
              重试
            </Button>
          ),
          hasPermission(permissions.taskDelete) && (
          <Popconfirm
            key="delete"
            title="确认删除该任务及其所有数据？"
            onConfirm={() => handleAction(async () => {
              await api.deleteTask(record.id)
              return {} as Task
            }, '任务已删除')}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
          ),
        ].filter(Boolean)
        return actions.length ? <Space>{actions}</Space> : <Typography.Text type="secondary">—</Typography.Text>
      },
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
            {hasPermission(permissions.taskCreate) && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
                新建任务
              </Button>
            )}
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
            onChange: (p) => {
              setPage(p - 1)
              loadTasks(p - 1)
            },
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>

      <Modal
        title="新建任务"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          form.resetFields()
        }}
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
                { value: 'SEARCH_BATCH', label: '批量检索任务' },
                { value: 'ORIGINAL_IMAGE', label: '补充原始图片任务' },
                { value: 'HD_IMAGE', label: '补充超清无损图任务' },
                { value: 'TRANSACTION_PRICE', label: '补充成交价任务' },
                { value: 'DESCRIPTION', label: '补充拍品描述任务' },
              ]}
            />
          </Form.Item>
          <Form.Item label="任务名称" name="name" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input
              placeholder={
                selectedTaskType === 'SEARCH_BATCH'
                  ? '例如：2024春拍批量检索'
                  : selectedTaskType === 'ORIGINAL_IMAGE'
                    ? '例如：张大千原图补充'
                    : selectedTaskType === 'HD_IMAGE'
                      ? '例如：张大千超清无损图补充'
                      : selectedTaskType === 'TRANSACTION_PRICE'
                        ? '例如：张大千成交价补充'
                        : selectedTaskType === 'DESCRIPTION'
                          ? '例如：张大千拍品描述补充'
                          : '例如：印象派艺术品检索'
              }
            />
          </Form.Item>
          {isSupplementTaskType(selectedTaskType) ? (
            <Form.Item
              label="目标检索任务"
              name="targetTaskId"
              rules={[{ required: true, message: '请选择目标检索任务' }]}
            >
              <Select
                placeholder="选择一个已存在的检索目标任务"
                options={searchTaskOptions}
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>
          ) : selectedTaskType === 'SEARCH_BATCH' ? (
            <Form.Item
              label="检索目标"
              name="keywordsText"
              tooltip="一行一个检索目标，创建后会自动生成对应的子检索任务"
              rules={[
                { required: true, message: '请输入检索目标' },
                {
                  validator: (_, value) => (
                    parseBatchKeywords(value).length >= 2
                      ? Promise.resolve()
                      : Promise.reject(new Error('请至少输入 2 个检索目标'))
                  ),
                },
              ]}
            >
              <Input.TextArea
                rows={6}
                placeholder={'例如：\n张大千\n齐白石\n吴冠中'}
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
