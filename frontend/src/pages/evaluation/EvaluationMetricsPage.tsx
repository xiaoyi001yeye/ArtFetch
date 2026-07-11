import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, InputNumber, message, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography } from 'antd'
import { EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import * as api from '../../api'
import type { EvaluationMetricDefinition } from '../../types'
import { permissions } from '../../auth/permissions'
import { useAuth } from '../../auth/AuthContext'
import { getInputComponentLabel, INPUT_COMPONENT_OPTIONS, isNumericInputComponent, isOptionInputComponent } from './metricInputUtils'

type MetricFormValues = Partial<EvaluationMetricDefinition>

export default function EvaluationMetricsPage() {
  const { hasPermission } = useAuth()
  const [metrics, setMetrics] = useState<EvaluationMetricDefinition[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<EvaluationMetricDefinition | null>(null)
  const [form] = Form.useForm<MetricFormValues>()
  const inputComponent = Form.useWatch('inputComponent', form)
  const showOptionValues = isOptionInputComponent(inputComponent)
  const showScoreRange = isNumericInputComponent(inputComponent)

  const load = async (p = page, q = keyword) => {
    setLoading(true)
    try {
      const result = await api.listEvaluationMetrics(p, 20, q || undefined)
      setMetrics(result.items)
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

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      scoreType: 'NUMBER',
      inputComponent: 'input-number',
      minScore: 1,
      maxScore: 10,
      scoreStep: 1,
      required: true,
      enabled: true,
      sortOrder: 0,
    })
    setModalOpen(true)
  }

  const openEdit = (item: EvaluationMetricDefinition) => {
    setEditing(item)
    form.setFieldsValue(item)
    setModalOpen(true)
  }

  const submit = async (values: MetricFormValues) => {
    try {
      if (editing) {
        await api.updateEvaluationMetric(editing.id, values)
        message.success('指标已更新')
      } else {
        await api.createEvaluationMetric(values)
        message.success('指标已创建')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const columns: ColumnsType<EvaluationMetricDefinition> = [
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '编码', dataIndex: 'code', width: 180 },
    { title: '导出字段', dataIndex: 'exportField', width: 160 },
    { title: '分类', dataIndex: 'category', width: 120, render: (v) => v || '—' },
    { title: '评分类型', dataIndex: 'scoreType', width: 100, render: (v) => v || '—' },
    { title: '输入控件', dataIndex: 'inputComponent', width: 120, render: (v) => getInputComponentLabel(v) },
    {
      title: '分值范围',
      width: 120,
      render: (_, record) => isNumericInputComponent(record.inputComponent) && record.minScore != null && record.maxScore != null ? `${record.minScore} - ${record.maxScore}` : '—',
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (v) => v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '版本',
      dataIndex: 'version',
      width: 80,
    },
    {
      title: '操作',
      width: 160,
      render: (_, record) => (
        <Space>
          {hasPermission(permissions.evaluationMetricUpdate) && (
            <Button size="small" icon={<EditOutlined />} disabled={record.builtIn} onClick={() => openEdit(record)}>编辑</Button>
          )}
          {hasPermission(permissions.evaluationMetricDisable) && !record.builtIn && (
            <Popconfirm
              title="确认删除该指标？"
              onConfirm={async () => {
                await api.deleteEvaluationMetric(record.id)
                message.success('指标已删除')
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
          <Typography.Title level={4} style={{ margin: 0 }}>评估指标库</Typography.Title>
          <Space>
            <Input.Search
              placeholder="搜索名称或编码"
              allowClear
              style={{ width: 240 }}
              onSearch={(value) => {
                setKeyword(value)
                load(0, value)
              }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => load(0, keyword)}>刷新</Button>
            {hasPermission(permissions.evaluationMetricCreate) && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建指标</Button>
            )}
          </Space>
        </Space>

        <Card>
          <Table
            rowKey="id"
            loading={loading}
            dataSource={metrics}
            columns={columns}
            pagination={{
              current: page + 1,
              pageSize: 20,
              total,
              onChange: (p) => load(p - 1, keyword),
              showTotal: (t) => `共 ${t} 个指标`,
            }}
          />
        </Card>
      </Space>

      <Modal
        title={editing ? '编辑指标' : '新建指标'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        width={760}
        okText="保存"
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Space style={{ width: '100%' }} align="start">
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }]} style={{ flex: 1 }}>
              <Input disabled={Boolean(editing)} />
            </Form.Item>
            <Form.Item
              name="exportField"
              label="导出字段"
              rules={[
                { required: true, message: '请输入导出字段' },
                { pattern: /^[a-z][a-z0-9_]*$/, message: '仅支持小写字母、数字和下划线，且必须以字母开头' },
              ]}
              style={{ flex: 1 }}
            >
              <Input placeholder="如 craftsmanship" />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} align="start">
            <Form.Item name="category" label="分类" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="scoreType" label="评分类型" style={{ width: 160 }}>
              <Select
                options={[
                  { value: 'NUMBER', label: '数值评分' },
                  { value: 'TEXT', label: '文本评价' },
                  { value: 'OPTION', label: '选项输入' },
                ]}
              />
            </Form.Item>
            <Form.Item name="inputComponent" label="输入控件" style={{ width: 180 }}>
              <Select
                options={INPUT_COMPONENT_OPTIONS}
                onChange={(value) => {
                  if (value === 'input-number') {
                    form.setFieldsValue({ scoreType: 'NUMBER', optionValues: undefined })
                  } else if (value === 'textarea') {
                    form.setFieldsValue({ scoreType: 'TEXT', minScore: undefined, maxScore: undefined, scoreStep: undefined, optionValues: undefined })
                  } else {
                    form.setFieldsValue({ scoreType: 'OPTION', minScore: undefined, maxScore: undefined, scoreStep: undefined })
                  }
                }}
              />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space style={{ width: '100%' }} align="start" wrap>
            {showScoreRange && (
              <>
                <Form.Item name="minScore" label="最小分值">
                  <InputNumber style={{ width: 120 }} />
                </Form.Item>
                <Form.Item name="maxScore" label="最大分值">
                  <InputNumber style={{ width: 120 }} />
                </Form.Item>
                <Form.Item name="scoreStep" label="分值步长">
                  <InputNumber style={{ width: 120 }} />
                </Form.Item>
              </>
            )}
            <Form.Item name="defaultWeight" label="默认权重">
              <InputNumber style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序">
              <InputNumber style={{ width: 120 }} />
            </Form.Item>
          </Space>
          {showOptionValues && (
            <Form.Item
              name="optionValues"
              label="选项配置"
              extra="每行一个选项；可用“值|显示名”格式区分保存值和展示名，例如 good|良好"
              rules={showOptionValues ? [{ required: true, message: '请输入选项配置' }] : undefined}
            >
              <Input.TextArea rows={5} placeholder={`excellent|优秀\ngood|良好\nfair|一般`} />
            </Form.Item>
          )}
          <Space style={{ width: '100%' }} align="start">
            <Form.Item name="required" label="是否必填" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="enabled" label="是否启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item name="scoringGuide" label="评分说明">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="scoringRubric" label="评分标准">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="applicableArtworkTypes" label="适用艺术品类型">
            <Input />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
