import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, message, Modal, Popconfirm, Select, Space, Table, Typography } from 'antd'
import { DownloadOutlined, EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { Link, useNavigate } from 'react-router-dom'
import * as api from '../../api'
import type { AutoEvaluationDataset, AutoEvaluationSourceProject, EvaluationProjectExpert } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import { datasetStatusTag, formatBytes, strategyText } from './autoEvaluationUi'

type CreateFormValues = {
  name: string
  sourceEvaluationId: number
  aggregationStrategy: 'AVERAGE_ALL_EXPERTS' | 'SELECTED_EXPERT'
  selectedExpertId?: number
}

export default function AutoEvaluationDatasetsPage() {
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const [items, setItems] = useState<AutoEvaluationDataset[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [sourceProjects, setSourceProjects] = useState<AutoEvaluationSourceProject[]>([])
  const [experts, setExperts] = useState<EvaluationProjectExpert[]>([])
  const [creating, setCreating] = useState(false)
  const [form] = Form.useForm<CreateFormValues>()

  const load = async (p = page) => {
    setLoading(true)
    try {
      const result = await api.listAutoEvaluationDatasets(p, 20)
      setItems(result.items)
      setTotal(result.total)
      setPage(p)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }

  const loadSources = async () => {
    try {
      const result = await api.listAutoEvaluationSourceProjects({ page: 0, size: 100 })
      setSourceProjects(result.items)
    } catch (e: any) {
      message.error(e.message)
    }
  }

  useEffect(() => {
    load(0)
  }, [])

  const openCreate = async () => {
    form.resetFields()
    form.setFieldsValue({ aggregationStrategy: 'AVERAGE_ALL_EXPERTS' })
    setExperts([])
    setCreateOpen(true)
    await loadSources()
  }

  const onSourceChange = async (evaluationId: number) => {
    form.setFieldValue('selectedExpertId', undefined)
    try {
      const result = await api.listEvaluationExperts(evaluationId)
      setExperts(result)
      const project = sourceProjects.find((item) => item.id === evaluationId)
      if (project && !form.getFieldValue('name')) {
        form.setFieldValue('name', `${project.name} 训练数据集`)
      }
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const create = async () => {
    const values = await form.validateFields()
    setCreating(true)
    try {
      const dataset = await api.createAutoEvaluationDataset(values)
      message.success('训练数据集草稿已创建')
      setCreateOpen(false)
      navigate(`/auto-evaluation/datasets/${dataset.id}`)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setCreating(false)
    }
  }

  const columns: ColumnsType<AutoEvaluationDataset> = [
    {
      title: '数据集名称',
      dataIndex: 'name',
      render: (value, record) => <Link to={`/auto-evaluation/datasets/${record.id}`}>{value}</Link>,
    },
    { title: '状态', dataIndex: 'status', width: 110, render: datasetStatusTag },
    { title: '来源项目', dataIndex: 'sourceEvaluationName' },
    {
      title: '汇总策略',
      width: 140,
      render: (_, record) => record.aggregationStrategy === 'SELECTED_EXPERT'
        ? `${strategyText(record.aggregationStrategy)}：${record.selectedExpertName || '—'}`
        : strategyText(record.aggregationStrategy),
    },
    { title: '已选/样本/跳过', width: 150, render: (_, record) => `${record.selectedCount} / ${record.sampleCount} / ${record.skippedCount}` },
    { title: '包大小', dataIndex: 'zipFileSize', width: 100, render: formatBytes },
    { title: '创建人', dataIndex: 'createdByName', width: 120, render: (v) => v || '—' },
    {
      title: '操作',
      width: 250,
      render: (_, record) => (
        <Space>
          <Link to={`/auto-evaluation/datasets/${record.id}`}>
            <Button size="small" icon={<EyeOutlined />}>详情</Button>
          </Link>
          {record.status === 'READY' && hasPermission(permissions.autoEvaluationDatasetExport) && (
            <Button size="small" icon={<DownloadOutlined />} onClick={() => api.downloadAutoEvaluationDataset(record.id)}>
              下载
            </Button>
          )}
          {record.status === 'READY' && hasPermission(permissions.autoEvaluationDatasetCreate) && (
            <Popconfirm title="归档后列表默认隐藏，确认归档？" onConfirm={async () => {
              await api.archiveAutoEvaluationDataset(record.id)
              message.success('已归档')
              load()
            }}>
              <Button size="small">归档</Button>
            </Popconfirm>
          )}
          {['DRAFT', 'FAILED'].includes(record.status) && hasPermission(permissions.autoEvaluationDatasetCreate) && (
            <Popconfirm title="确认删除该数据集草稿？" onConfirm={async () => {
              await api.deleteAutoEvaluationDataset(record.id)
              message.success('已删除')
              load()
            }}>
              <Button size="small" danger>删除</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>训练数据集</Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => load(0)}>刷新</Button>
          {hasPermission(permissions.autoEvaluationDatasetCreate) && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建数据集</Button>
          )}
        </Space>
      </Space>

      <Card>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={items}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total,
            onChange: (p) => load(p - 1),
            showTotal: (t) => `共 ${t} 个数据集`,
          }}
        />
      </Card>

      <Modal title="新建训练数据集" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={create} confirmLoading={creating} okText="创建">
        <Form form={form} layout="vertical">
          <Form.Item name="sourceEvaluationId" label="来源评估项目" rules={[{ required: true, message: '请选择来源项目' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={sourceProjects.map((item) => ({
                value: item.id,
                label: `${item.name}（${item.artworkCount} 件 / ${item.expertCount} 位专家）`,
              }))}
              onChange={onSourceChange}
            />
          </Form.Item>
          <Form.Item name="name" label="数据集名称" rules={[{ required: true, message: '请输入数据集名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="aggregationStrategy" label="汇总策略">
            <Select options={[
              { value: 'AVERAGE_ALL_EXPERTS', label: '所有专家平均值' },
              { value: 'SELECTED_EXPERT', label: '指定专家' },
            ]} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.aggregationStrategy !== next.aggregationStrategy}>
            {({ getFieldValue }) => getFieldValue('aggregationStrategy') === 'SELECTED_EXPERT' && (
              <Form.Item name="selectedExpertId" label="指定专家" rules={[{ required: true, message: '请选择专家' }]}>
                <Select options={experts.map((item) => ({ value: item.expertId, label: item.expertName }))} />
              </Form.Item>
            )}
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
