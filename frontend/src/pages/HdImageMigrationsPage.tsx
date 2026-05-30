import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Col, Drawer, Form, Image, Input, InputNumber, message, Modal, Popconfirm, Progress, Row, Select, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { CaretRightOutlined, EyeOutlined, PauseOutlined, PlusOutlined, ReloadOutlined, RetweetOutlined, StopOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import * as api from '../api'
import type { HdImageMigrationItem, HdImageMigrationItemStatus, HdImageMigrationTask, ObjectStorageConfig, Task } from '../types'
import { useAuth } from '../auth/AuthContext'
import { permissions } from '../auth/permissions'

const STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待启动', color: 'default' },
  RUNNING: { label: '运行中', color: 'blue' },
  PAUSED: { label: '已暂停', color: 'orange' },
  COMPLETED: { label: '已完成', color: 'green' },
  FAILED: { label: '失败', color: 'red' },
  CANCELLED: { label: '已取消', color: 'default' },
  UPLOADING: { label: '上传中', color: 'blue' },
  MIGRATED: { label: '已迁移', color: 'green' },
  SKIPPED: { label: '已跳过', color: 'gold' },
}

type CreateValues = {
  name: string
  configId: number
  mode: 'FULL' | 'INCREMENTAL' | 'RETRY_FAILED'
  scopeType: 'ALL' | 'SEARCH_TASK'
  targetTaskId?: number
  uploadConcurrency?: number
}

export default function HdImageMigrationsPage() {
  const { hasPermission } = useAuth()
  const canManage = hasPermission(permissions.hdImageMigrationManage)
  const canViewImage = hasPermission(permissions.artworkImageView)
  const [tasks, setTasks] = useState<HdImageMigrationTask[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [configs, setConfigs] = useState<ObjectStorageConfig[]>([])
  const [searchTasks, setSearchTasks] = useState<Task[]>([])
  const [activeTask, setActiveTask] = useState<HdImageMigrationTask | null>(null)
  const [items, setItems] = useState<HdImageMigrationItem[]>([])
  const [itemTotal, setItemTotal] = useState(0)
  const [itemPage, setItemPage] = useState(0)
  const [itemStatus, setItemStatus] = useState<HdImageMigrationItemStatus | undefined>()
  const [itemLoading, setItemLoading] = useState(false)
  const [previewUrl, setPreviewUrl] = useState<string>()
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewLoadingId, setPreviewLoadingId] = useState<number>()
  const [form] = Form.useForm<CreateValues>()
  const timerRef = useRef<number>()

  const load = useCallback(async (p = page) => {
    setLoading(true)
    try {
      const result = await api.listHdImageMigrations(p, 20)
      setTasks(result.items)
      setTotal(result.total)
      setPage(p)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }, [page])

  const loadSupportingData = async () => {
    try {
      const [configList, taskResult] = await Promise.all([
        api.listObjectStorageConfigs(),
        api.listTasks(0, 500),
      ])
      setConfigs(configList.filter((item) => item.enabled && item.migrateEnabled))
      setSearchTasks(taskResult.items.filter((item) => item.taskType === 'SEARCH'))
    } catch {
    }
  }

  useEffect(() => {
    load(0)
    loadSupportingData()
    timerRef.current = window.setInterval(() => load(), 5000)
    return () => clearInterval(timerRef.current)
  }, [load])

  const running = useMemo(() => tasks.some((task) => task.status === 'RUNNING'), [tasks])

  const openCreate = () => {
    form.setFieldsValue({
      name: `高清图增量迁移 ${new Date().toLocaleString()}`,
      mode: 'INCREMENTAL',
      scopeType: 'ALL',
      uploadConcurrency: 4,
      configId: configs[0]?.id,
    })
    setModalOpen(true)
  }

  const submit = async (values: CreateValues) => {
    setSubmitting(true)
    try {
      await api.createHdImageMigration(values)
      message.success('迁移任务已创建')
      setModalOpen(false)
      form.resetFields()
      load(0)
      setPage(0)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  const action = async (fn: () => Promise<HdImageMigrationTask>, text: string) => {
    try {
      await fn()
      message.success(text)
      load()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const loadItems = async (task = activeTask, p = itemPage, status = itemStatus) => {
    if (!task) return
    setItemLoading(true)
    try {
      const result = await api.listHdImageMigrationItems(task.id, { page: p, size: 20, status })
      setItems(result.items)
      setItemTotal(result.total)
      setItemPage(p)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setItemLoading(false)
    }
  }

  const openItems = (task: HdImageMigrationTask, status?: HdImageMigrationItemStatus) => {
    setActiveTask(task)
    setItemStatus(status)
    setItemPage(0)
    loadItems(task, 0, status)
  }

  const handleViewMigratedItem = async (item: HdImageMigrationItem) => {
    if (!canViewImage || item.status !== 'MIGRATED' || previewLoadingId) return
    setPreviewLoadingId(item.id)
    const hideLoading = message.loading('正在加载高清图...', 0)
    try {
      const objectUrl = await api.createProtectedBlobUrl(api.hdImageViewUrl(item.artworkId))
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl)
      }
      setPreviewUrl(objectUrl)
      setPreviewOpen(true)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      hideLoading()
      setPreviewLoadingId(undefined)
    }
  }

  const handlePreviewVisibleChange = (visible: boolean) => {
    setPreviewOpen(visible)
    if (!visible && previewUrl) {
      URL.revokeObjectURL(previewUrl)
      setPreviewUrl(undefined)
    }
  }

  const columns: ColumnsType<HdImageMigrationTask> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '任务名称', dataIndex: 'name', ellipsis: true },
    { title: '模式', dataIndex: 'mode', width: 110, render: (v) => ({ FULL: '全量', INCREMENTAL: '增量', RETRY_FAILED: '失败重试' } as any)[v] || v },
    { title: '范围', width: 150, render: (_, r) => r.scopeType === 'SEARCH_TASK' ? `检索任务 #${r.targetTaskId}` : '全部高清图' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v) => <Tag color={STATUS[v]?.color}>{STATUS[v]?.label || v}</Tag>,
    },
    {
      title: '进度',
      width: 230,
      render: (_, r) => (
        <Space direction="vertical" size={0} style={{ width: '100%' }}>
          <Progress percent={Number((r.progressPercent || 0).toFixed(1))} size="small" status={r.status === 'FAILED' ? 'exception' : undefined} />
          <Typography.Text type="secondary">{r.processedCount}/{r.totalCount}</Typography.Text>
        </Space>
      ),
    },
    { title: '成功', dataIndex: 'successCount', width: 80 },
    { title: '跳过', dataIndex: 'skippedCount', width: 80 },
    {
      title: '失败',
      dataIndex: 'failedCount',
      width: 80,
      render: (v, r) => v > 0 ? (
        <Button type="link" danger size="small" onClick={() => openItems(r, 'FAILED')} style={{ padding: 0 }}>
          {v}
        </Button>
      ) : v,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: (v) => v?.replace('T', ' ').slice(0, 19) },
    {
      title: '操作',
      fixed: 'right',
      width: 300,
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => openItems(r)}>明细</Button>
          {canManage && ['PENDING', 'PAUSED', 'FAILED'].includes(r.status) && (
            <Button size="small" icon={<CaretRightOutlined />} type="primary" disabled={running && r.status !== 'RUNNING'} onClick={() => action(() => api.startHdImageMigration(r.id), '迁移任务已启动')}>启动</Button>
          )}
          {canManage && r.status === 'RUNNING' && <Button size="small" icon={<PauseOutlined />} onClick={() => action(() => api.pauseHdImageMigration(r.id), '迁移任务已暂停')}>暂停</Button>}
          {canManage && ['PENDING', 'RUNNING', 'PAUSED'].includes(r.status) && (
            <Popconfirm title="取消当前迁移任务？" onConfirm={() => action(() => api.cancelHdImageMigration(r.id), '迁移任务已取消')}>
              <Button size="small" icon={<StopOutlined />}>取消</Button>
            </Popconfirm>
          )}
          {canManage && r.failedCount > 0 && <Button size="small" icon={<RetweetOutlined />} onClick={() => action(() => api.retryFailedHdImageMigration(r.id), '失败项已重置')}>重试失败</Button>}
        </Space>
      ),
    },
  ]

  const itemColumns: ColumnsType<HdImageMigrationItem> = [
    { title: '艺术品ID', dataIndex: 'artworkId', width: 100 },
    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <Tag color={STATUS[v]?.color}>{STATUS[v]?.label || v}</Tag> },
    { title: '本地路径', dataIndex: 'localPath', ellipsis: true },
    { title: '对象 Key', dataIndex: 'objectKey', ellipsis: true },
    { title: '大小', dataIndex: 'fileSize', width: 110, render: (v) => v ? `${(v / 1024 / 1024).toFixed(1)} MB` : '—' },
    { title: '重试', dataIndex: 'attemptCount', width: 80 },
    {
      title: '失败/跳过原因',
      dataIndex: 'errorMessage',
      width: 260,
      ellipsis: true,
      render: (v, record) => v ? (
        <Tooltip title={v}>
          <Typography.Text type={record.status === 'FAILED' ? 'danger' : 'warning'}>{v}</Typography.Text>
        </Tooltip>
      ) : '—',
    },
    {
      title: '查看',
      fixed: 'right',
      width: 90,
      render: (_, record) => {
        const disabledReason = record.status !== 'MIGRATED'
          ? '仅已迁移成功的文件可查看'
          : canViewImage ? undefined : '缺少查看高清图权限'
        return (
          <Tooltip title={disabledReason}>
            <span>
              <Button
                size="small"
                icon={<EyeOutlined />}
                disabled={Boolean(disabledReason)}
                loading={previewLoadingId === record.id}
                onClick={() => handleViewMigratedItem(record)}
              >
                查看
              </Button>
            </span>
          </Tooltip>
        )
      },
    },
  ]

  const scopeType = Form.useWatch('scopeType', form)

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col><Typography.Title level={4} style={{ margin: 0 }}>高清图迁移</Typography.Title></Col>
        <Col>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => load()}>刷新</Button>
            {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>创建迁移任务</Button>}
          </Space>
        </Col>
      </Row>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={tasks}
        loading={loading}
        pagination={{ current: page + 1, pageSize: 20, total, onChange: (p) => load(p - 1), showTotal: (t) => `共 ${t} 个任务` }}
        scroll={{ x: 1500 }}
      />

      <Modal title="创建高清图迁移任务" open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} okText="创建" confirmLoading={submitting} width={680}>
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item name="name" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="configId" label="对象存储配置" rules={[{ required: true, message: '请选择配置' }]}>
                <Select options={configs.map((item) => ({ value: item.id, label: `${item.name} / ${item.bucket}` }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="mode" label="迁移模式" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'INCREMENTAL', label: '增量迁移' },
                    { value: 'FULL', label: '全量扫描' },
                    { value: 'RETRY_FAILED', label: '失败重试' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="scopeType" label="迁移范围" rules={[{ required: true }]}>
                <Select options={[{ value: 'ALL', label: '全部高清图' }, { value: 'SEARCH_TASK', label: '指定检索任务' }]} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="uploadConcurrency" label="上传并发">
                <InputNumber min={1} max={16} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            {scopeType === 'SEARCH_TASK' && (
              <Col span={24}>
                <Form.Item name="targetTaskId" label="目标检索任务" rules={[{ required: true, message: '请选择目标检索任务' }]}>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    options={searchTasks.map((task) => ({ value: task.id, label: `#${task.id} ${task.name}` }))}
                  />
                </Form.Item>
              </Col>
            )}
          </Row>
        </Form>
      </Modal>

      <Drawer title={activeTask ? `迁移明细 #${activeTask.id}` : '迁移明细'} open={Boolean(activeTask)} onClose={() => setActiveTask(null)} width="86%">
        <Space style={{ marginBottom: 16 }}>
          <Select
            allowClear
            placeholder="全部状态"
            style={{ width: 160 }}
            value={itemStatus}
            onChange={(v) => { setItemStatus(v); loadItems(activeTask, 0, v) }}
            options={[
              { value: 'PENDING', label: '待处理' },
              { value: 'UPLOADING', label: '上传中' },
              { value: 'MIGRATED', label: '已迁移' },
              { value: 'SKIPPED', label: '已跳过' },
              { value: 'FAILED', label: '失败' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => loadItems()}>刷新</Button>
        </Space>
        {activeTask?.errorMessage && (
          <Alert
            type="error"
            showIcon
            message="任务失败原因"
            description={activeTask.errorMessage}
            style={{ marginBottom: 16 }}
          />
        )}
        <Table
          rowKey="id"
          columns={itemColumns}
          dataSource={items}
          loading={itemLoading}
          pagination={{ current: itemPage + 1, pageSize: 20, total: itemTotal, onChange: (p) => loadItems(activeTask, p - 1, itemStatus), showTotal: (t) => `共 ${t} 条明细` }}
          scroll={{ x: 1450 }}
        />
      </Drawer>
      {previewUrl && (
        <Image
          src={previewUrl}
          alt="迁移高清图"
          style={{ display: 'none' }}
          preview={{
            visible: previewOpen,
            src: previewUrl,
            movable: true,
            minScale: 0.2,
            maxScale: 8,
            scaleStep: 0.5,
            destroyOnHidden: true,
            onVisibleChange: handlePreviewVisibleChange,
          }}
        />
      )}
    </div>
  )
}
