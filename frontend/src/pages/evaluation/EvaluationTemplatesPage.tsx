import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Form, Input, InputNumber, message, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography } from 'antd'
import { EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import * as api from '../../api'
import type { EvaluationMetricDefinition, EvaluationMetricTemplate, MetricConfig } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'

type TemplateFormValues = {
  name: string
  description?: string
  enabled: boolean
}

const toMetricConfig = (definition: EvaluationMetricDefinition, sortOrder: number): MetricConfig => ({
  sourceMetricDefinitionId: definition.id,
  sourceVersion: definition.version,
  code: definition.code,
  exportField: definition.exportField,
  name: definition.name,
  description: definition.description,
  category: definition.category,
  scoreType: definition.scoreType,
  minScore: definition.minScore,
  maxScore: definition.maxScore,
  scoreStep: definition.scoreStep,
  weight: definition.defaultWeight,
  required: definition.required,
  inputComponent: definition.inputComponent,
  optionValues: definition.optionValues,
  scoringGuide: definition.scoringGuide,
  scoringRubric: definition.scoringRubric,
  sortOrder,
})

export default function EvaluationTemplatesPage() {
  const { hasPermission } = useAuth()
  const [templates, setTemplates] = useState<EvaluationMetricTemplate[]>([])
  const [definitions, setDefinitions] = useState<EvaluationMetricDefinition[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<EvaluationMetricTemplate | null>(null)
  const [items, setItems] = useState<MetricConfig[]>([])
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [form] = Form.useForm<TemplateFormValues>()

  const definitionOptions = useMemo(
    () => definitions.map((item) => ({ value: item.id, label: `${item.name} (${item.code})` })),
    [definitions],
  )

  const load = async (p = page) => {
    setLoading(true)
    try {
      const [templateResult, metricResult] = await Promise.all([
        api.listEvaluationMetricTemplates(p, 20),
        api.listEnabledEvaluationMetrics(),
      ])
      setTemplates(templateResult.items)
      setTotal(templateResult.total)
      setPage(p)
      setDefinitions(metricResult)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(0)
  }, [])

  const rebuildItemsFromSelection = (ids: number[], currentItems = items) => {
    const existingMap = new Map(currentItems.map((item) => [item.sourceMetricDefinitionId, item]))
    const next = ids
      .map((id, index) => {
        const existing = existingMap.get(id)
        if (existing) return { ...existing, sortOrder: index + 1 }
        const definition = definitions.find((item) => item.id === id)
        return definition ? toMetricConfig(definition, index + 1) : null
      })
      .filter(Boolean) as MetricConfig[]
    setItems(next)
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ enabled: true })
    setItems([])
    setSelectedIds([])
    setModalOpen(true)
  }

  const openEdit = (item: EvaluationMetricTemplate) => {
    setEditing(item)
    form.setFieldsValue({ name: item.name, description: item.description, enabled: item.enabled })
    setItems(item.items)
    setSelectedIds(item.items.map((entry) => entry.sourceMetricDefinitionId).filter(Boolean) as number[])
    setModalOpen(true)
  }

  const submit = async (values: TemplateFormValues) => {
    try {
      const payload = { ...values, items }
      if (editing) {
        await api.updateEvaluationMetricTemplate(editing.id, payload)
        message.success('模板已更新')
      } else {
        await api.createEvaluationMetricTemplate(payload)
        message.success('模板已创建')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const updateItem = (index: number, patch: Partial<MetricConfig>) => {
    setItems((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item))
  }

  const columns: ColumnsType<EvaluationMetricTemplate> = [
    { title: '模板名称', dataIndex: 'name', width: 220 },
    { title: '编码', dataIndex: 'code', width: 180, render: (v) => v || '—' },
    { title: '说明', dataIndex: 'description', render: (v) => v || '—' },
    { title: '指标数', dataIndex: 'itemCount', width: 90 },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (v) => v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '操作',
      width: 160,
      render: (_, record) => (
        <Space>
          {hasPermission(permissions.evaluationTemplateUpdate) && (
            <Button size="small" icon={<EditOutlined />} disabled={record.builtIn} onClick={() => openEdit(record)}>编辑</Button>
          )}
          {hasPermission(permissions.evaluationTemplateDisable) && !record.builtIn && (
            <Popconfirm
              title="确认删除该模板？"
              onConfirm={async () => {
                await api.deleteEvaluationMetricTemplate(record.id)
                message.success('模板已删除')
                load()
              }}
            >
              <Button size="small" danger>删除</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Typography.Title level={4} style={{ margin: 0 }}>评估指标模板</Typography.Title>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => load(0)}>刷新</Button>
            {hasPermission(permissions.evaluationTemplateCreate) && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建模板</Button>
            )}
          </Space>
        </Space>

        <Card>
          <Table
            rowKey="id"
            loading={loading}
            dataSource={templates}
            columns={columns}
            pagination={{
              current: page + 1,
              pageSize: 20,
              total,
              onChange: (p) => load(p - 1),
              showTotal: (t) => `共 ${t} 个模板`,
            }}
          />
        </Card>
      </Space>

      <Modal
        title={editing ? '编辑模板' : '新建模板'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        width={960}
        okText="保存"
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="enabled" label="是否启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="选择指标">
            <Select
              mode="multiple"
              options={definitionOptions}
              value={selectedIds}
              onChange={(value) => {
                setSelectedIds(value)
                rebuildItemsFromSelection(value, items)
              }}
              placeholder="选择指标定义"
            />
          </Form.Item>
          <Card size="small" title="模板指标">
            <Table
              rowKey={(record) => record.code}
              pagination={false}
              dataSource={items}
              columns={[
                { title: '名称', dataIndex: 'name', width: 180 },
                { title: '导出字段', dataIndex: 'exportField', width: 160 },
                {
                  title: '必填',
                  width: 90,
                  render: (_, record, index) => (
                    <Switch checked={record.required} onChange={(checked) => updateItem(index, { required: checked })} />
                  ),
                },
                {
                  title: '最小分值',
                  width: 120,
                  render: (_, record, index) => (
                    <InputNumber
                      disabled={record.inputComponent !== 'input-number'}
                      value={record.minScore}
                      onChange={(value) => updateItem(index, { minScore: value ?? undefined })}
                    />
                  ),
                },
                {
                  title: '最大分值',
                  width: 120,
                  render: (_, record, index) => (
                    <InputNumber
                      disabled={record.inputComponent !== 'input-number'}
                      value={record.maxScore}
                      onChange={(value) => updateItem(index, { maxScore: value ?? undefined })}
                    />
                  ),
                },
                {
                  title: '排序',
                  width: 90,
                  render: (_, record, index) => (
                    <InputNumber value={record.sortOrder} onChange={(value) => updateItem(index, { sortOrder: value ?? index + 1 })} />
                  ),
                },
                {
                  title: '操作',
                  width: 90,
                  render: (_, record) => (
                    <Button
                      size="small"
                      danger
                      onClick={() => {
                        const nextIds = selectedIds.filter((id) => id !== record.sourceMetricDefinitionId)
                        setSelectedIds(nextIds)
                        rebuildItemsFromSelection(nextIds, items.filter((item) => item.code !== record.code))
                      }}
                    >
                      移除
                    </Button>
                  ),
                },
              ]}
            />
          </Card>
        </Form>
      </Modal>
    </div>
  )
}
