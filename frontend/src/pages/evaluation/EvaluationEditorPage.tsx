import { useEffect, useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import {
  Button,
  Card,
  Divider,
  Form,
  Input,
  InputNumber,
  List,
  message,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import * as api from '../../api'
import type {
  ArtworkPreview,
  AuthUser,
  CriterionItem,
  EvaluationMetricDefinition,
  EvaluationMetricTemplate,
  EvaluationProject,
  MetricConfig,
} from '../../types'

type ProjectFormValues = {
  name: string
  description?: string
  auditorId: number
  criteria: CriterionItem[]
}

type CriteriaFieldOption = {
  value: string
  label: string
  valueType?: 'text' | 'boolean'
  operators?: string[]
  defaultOperator?: string
}

const criteriaFields: CriteriaFieldOption[] = [
  { value: 'title', label: '标题' },
  { value: 'lotNumber', label: '拍品编号' },
  { value: 'artist', label: '作者' },
  { value: 'medium', label: '材质' },
  { value: 'valuation', label: '估价' },
  { value: 'auctionHouse', label: '拍卖公司' },
  { value: 'auctionName', label: '拍卖会' },
  { value: 'auctionSession', label: '拍卖专场' },
  { value: 'auctionDate', label: '拍卖日期' },
  { value: 'taskId', label: '抓取任务' },
  { value: 'externalId', label: '外部 ID' },
  { value: 'hdImageAvailable', label: '是否有高清大图', valueType: 'boolean', operators: ['equals'], defaultOperator: 'equals' },
]

const operatorOptions = [
  { value: 'contains', label: '包含' },
  { value: 'equals', label: '等于' },
  { value: 'year', label: '年份' },
  { value: 'notEmpty', label: '不为空' },
]

const booleanValueOptions = [
  { value: 'true', label: '是' },
  { value: 'false', label: '否' },
]

const criterionItemStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: 12,
  alignItems: 'flex-start',
  padding: 16,
  border: '1px solid #f0f0f0',
  borderRadius: 12,
  background: '#fafafa',
}

const criterionFieldStyle: CSSProperties = {
  width: 180,
  marginBottom: 0,
}

const criterionOperatorStyle: CSSProperties = {
  width: 140,
  marginBottom: 0,
}

const criterionValueStyle: CSSProperties = {
  flex: '1 1 240px',
  minWidth: 220,
  marginBottom: 0,
}

const criterionDeleteWrapStyle: CSSProperties = {
  marginLeft: 'auto',
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

export default function EvaluationEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const editingId = id ? Number(id) : undefined
  const [project, setProject] = useState<EvaluationProject | null>(null)
  const [users, setUsers] = useState<AuthUser[]>([])
  const [definitions, setDefinitions] = useState<EvaluationMetricDefinition[]>([])
  const [templates, setTemplates] = useState<EvaluationMetricTemplate[]>([])
  const [previewArtworks, setPreviewArtworks] = useState<ArtworkPreview[]>([])
  const [selectedArtworkIds, setSelectedArtworkIds] = useState<number[]>([])
  const [selectedExpertIds, setSelectedExpertIds] = useState<number[]>([])
  const [selectedDefinitionIds, setSelectedDefinitionIds] = useState<number[]>([])
  const [templateId, setTemplateId] = useState<number | undefined>()
  const [metrics, setMetrics] = useState<MetricConfig[]>([])
  const [previewLoading, setPreviewLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<ProjectFormValues>()
  const criteriaFieldMap = useMemo(() => new Map(criteriaFields.map((field) => [field.value, field])), [])

  const locked = Boolean(project && !['DRAFT', 'PENDING'].includes(project.status))

  const expertUsers = useMemo(() => users.filter((user) => user.roles.includes('EXPERT')), [users])
  const auditorUsers = useMemo(() => users.filter((user) => user.roles.includes('AUDITOR') || user.roles.includes('ADMIN')), [users])

  const loadBase = async () => {
    const [userResult, definitionResult, templateResult] = await Promise.all([
      api.listUsers(0, 500),
      api.listEnabledEvaluationMetrics(),
      api.listEvaluationMetricTemplates(0, 200),
    ])
    setUsers(userResult.items)
    setDefinitions(definitionResult)
    setTemplates(templateResult.items)
  }

  const loadProject = async () => {
    if (!editingId) return
    const result = await api.getEvaluation(editingId)
    setProject(result)
    form.setFieldsValue({
      name: result.name,
      description: result.description,
      auditorId: result.auditorId,
      criteria: result.criteria,
    })
    setSelectedArtworkIds(result.artworks.map((item) => item.artworkId))
    setPreviewArtworks(result.artworks.map((item) => ({
      id: item.artwork.id,
      title: item.artwork.title,
      artist: item.artwork.artist,
      lotNumber: item.artwork.lotNumber,
      medium: item.artwork.medium,
      valuation: item.artwork.valuation,
      auctionHouse: item.artwork.auctionHouse,
      auctionDate: item.artwork.auctionDate,
      imageUrl: item.artwork.imageUrl,
    })))
    setSelectedExpertIds(result.experts.map((item) => item.expertId))
    setMetrics(result.metrics.map((item, index) => ({ ...item, sortOrder: item.sortOrder ?? index + 1 })))
    setSelectedDefinitionIds(result.metrics.map((item) => item.sourceMetricDefinitionId).filter(Boolean) as number[])
  }

  useEffect(() => {
    loadBase().catch((e: any) => message.error(e.message))
  }, [])

  useEffect(() => {
    loadProject().catch((e: any) => {
      message.error(e.message)
      navigate('/evaluations')
    })
  }, [editingId])

  const updateMetric = (index: number, patch: Partial<MetricConfig>) => {
    setMetrics((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item))
  }

  const handleDefinitionSelection = (definitionIds: number[]) => {
    setSelectedDefinitionIds(definitionIds)
    const existing = new Map(metrics.map((item) => [item.sourceMetricDefinitionId, item]))
    const next = definitionIds.map((definitionId, index) => {
      const current = existing.get(definitionId)
      if (current) return { ...current, sortOrder: index + 1 }
      const definition = definitions.find((item) => item.id === definitionId)
      return definition ? toMetricConfig(definition, index + 1) : null
    }).filter(Boolean) as MetricConfig[]
    setMetrics(next)
  }

  const importTemplate = async () => {
    if (!templateId) return
    try {
      const items = await api.getEvaluationMetricTemplateItems(templateId)
      setMetrics(items.map((item, index) => ({ ...item, sortOrder: item.sortOrder || index + 1 })))
      setSelectedDefinitionIds(items.map((item) => item.sourceMetricDefinitionId).filter(Boolean) as number[])
      message.success('模板已导入')
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const preview = async () => {
    try {
      const values = await form.validateFields()
      setPreviewLoading(true)
      const criteria = (values.criteria || []).map((item) => {
        const field = criteriaFields.find((entry) => entry.value === item.fieldName)
        return { ...item, fieldLabel: field?.label || item.fieldName }
      })
      const result = await api.previewEvaluationArtworks({ criteria, page: 0, size: 100 })
      setPreviewArtworks(result.items)
      if (!editingId) {
        setSelectedArtworkIds(result.items.map((item) => item.id))
      }
    } catch (e: any) {
      if (e?.message) message.error(e.message)
    } finally {
      setPreviewLoading(false)
    }
  }

  const updateCriterionField = (index: number, nextFieldName: string) => {
    const fieldConfig = criteriaFieldMap.get(nextFieldName)
    const nextOperator = fieldConfig?.defaultOperator || 'contains'
    const nextValueType = fieldConfig?.valueType || 'text'
    const nextValue = nextValueType === 'boolean' ? 'true' : undefined

    form.setFieldValue(['criteria', index, 'operator'], nextOperator)
    form.setFieldValue(['criteria', index, 'valueType'], nextValueType)
    form.setFieldValue(['criteria', index, 'value'], nextValue)
  }

  const submit = async (values: ProjectFormValues) => {
    setSaving(true)
    try {
      const criteria = (values.criteria || []).map((item) => {
        const field = criteriaFields.find((entry) => entry.value === item.fieldName)
        return { ...item, fieldLabel: field?.label || item.fieldName }
      })
      if (!locked && selectedArtworkIds.length === 0) {
        throw new Error('请至少选择一件艺术品')
      }
      if (!locked && selectedExpertIds.length === 0) {
        throw new Error('请至少选择一位专家')
      }
      if (!locked && metrics.length === 0) {
        throw new Error('请至少选择一个评估指标')
      }

      const payload = {
        name: values.name,
        description: values.description,
        auditorId: values.auditorId,
        criteria,
        ...(locked ? {} : { artworkIds: selectedArtworkIds, expertIds: selectedExpertIds, metrics }),
      }

      if (editingId) {
        await api.updateEvaluation(editingId, payload)
        message.success('项目已更新')
        navigate(`/evaluations/${editingId}`)
      } else {
        const result = await api.createEvaluation(payload as any)
        message.success('项目已创建')
        navigate(`/evaluations/${result.id}`)
      }
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {editingId ? '编辑评估项目' : '新建评估项目'}
      </Typography.Title>

      <Form form={form} layout="vertical" onFinish={submit} initialValues={{ criteria: [{ fieldName: 'artist', operator: 'contains', valueType: 'text' }] }}>
        <Card title="基本信息">
          <Space style={{ width: '100%' }} align="start">
            <Form.Item name="name" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="auditorId" label="审核人" rules={[{ required: true, message: '请选择审核人' }]} style={{ width: 280 }}>
              <Select
                options={auditorUsers.map((user) => ({ value: user.id, label: `${user.displayName} (${user.username})` }))}
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="项目说明">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Card>

        <Card
          title="艺术品筛选条件"
          extra={!locked && <Button onClick={preview} loading={previewLoading}>预览匹配艺术品</Button>}
        >
          {locked && <Typography.Text type="secondary">项目已发布，不能修改项目数据。</Typography.Text>}
          <Form.List name="criteria">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                {fields.map((field) => (
                  <div key={field.key} style={criterionItemStyle}>
                    <Form.Item
                      {...field}
                      name={[field.name, 'fieldName']}
                      rules={[{ required: true, message: '请选择字段' }]}
                      style={criterionFieldStyle}
                    >
                      <Select
                        disabled={locked}
                        style={{ width: '100%' }}
                        options={criteriaFields}
                        placeholder="字段"
                        onChange={(value) => updateCriterionField(field.name, value)}
                      />
                    </Form.Item>
                    <Form.Item shouldUpdate noStyle>
                      {() => {
                        const fieldName = form.getFieldValue(['criteria', field.name, 'fieldName']) as string | undefined
                        const operator = form.getFieldValue(['criteria', field.name, 'operator']) as string | undefined
                        const fieldConfig = fieldName ? criteriaFieldMap.get(fieldName) : undefined
                        const supportedOperators = fieldConfig?.operators || operatorOptions.map((item) => item.value)
                        const filteredOperators = operatorOptions.filter((item) => supportedOperators.includes(item.value))
                        const isBooleanField = fieldConfig?.valueType === 'boolean'
                        const hideValueInput = operator === 'notEmpty'

                        return (
                          <>
                            <Form.Item
                              {...field}
                              name={[field.name, 'operator']}
                              rules={[{ required: true, message: '请选择操作符' }]}
                              style={criterionOperatorStyle}
                            >
                              <Select disabled={locked} style={{ width: '100%' }} options={filteredOperators} placeholder="操作符" />
                            </Form.Item>
                            {!hideValueInput && (
                              <Form.Item {...field} name={[field.name, 'value']} style={criterionValueStyle}>
                                {isBooleanField ? (
                                  <Select disabled={locked} style={{ width: '100%' }} options={booleanValueOptions} placeholder="请选择" />
                                ) : (
                                  <Input disabled={locked} style={{ width: '100%' }} placeholder="值" />
                                )}
                              </Form.Item>
                            )}
                          </>
                        )
                      }}
                    </Form.Item>
                    {!locked && (
                      <div style={criterionDeleteWrapStyle}>
                        <Button danger onClick={() => remove(field.name)}>删除</Button>
                      </div>
                    )}
                  </div>
                ))}
                {!locked && (
                  <Button icon={<PlusOutlined />} onClick={() => add({ fieldName: 'artist', operator: 'contains', valueType: 'text' })}>
                    添加条件
                  </Button>
                )}
              </Space>
            )}
          </Form.List>
        </Card>

        <Card title={`匹配艺术品 (${selectedArtworkIds.length})`}>
          <Table
            rowKey="id"
            loading={previewLoading}
            dataSource={previewArtworks}
            pagination={false}
            rowSelection={locked ? undefined : {
              selectedRowKeys: selectedArtworkIds,
              onChange: (keys) => setSelectedArtworkIds(keys as number[]),
            }}
            columns={[
              { title: '标题', dataIndex: 'title' },
              { title: '作者', dataIndex: 'artist', width: 120, render: (v) => v || '—' },
              { title: '编号', dataIndex: 'lotNumber', width: 100, render: (v) => v || '—' },
              { title: '拍卖公司', dataIndex: 'auctionHouse', width: 140, render: (v) => v || '—' },
              { title: '拍卖日期', dataIndex: 'auctionDate', width: 120, render: (v) => v || '—' },
            ]}
          />
        </Card>

        <Card title="专家">
          <Select
            mode="multiple"
            disabled={locked}
            value={selectedExpertIds}
            onChange={setSelectedExpertIds}
            style={{ width: '100%' }}
            showSearch
            optionFilterProp="label"
            options={expertUsers.map((user) => ({ value: user.id, label: `${user.displayName} (${user.username})` }))}
            placeholder="选择专家账号"
          />
        </Card>

        <Card title="评估指标">
          {!locked && (
            <>
              <Space style={{ marginBottom: 16 }} wrap>
                <Select
                  mode="multiple"
                  value={selectedDefinitionIds}
                  onChange={handleDefinitionSelection}
                  style={{ width: 420 }}
                  options={definitions.map((item) => ({ value: item.id, label: `${item.name} (${item.code})` }))}
                  placeholder="从指标库选择指标"
                />
                <Divider type="vertical" />
                <Select
                  allowClear
                  value={templateId}
                  onChange={setTemplateId}
                  style={{ width: 260 }}
                  options={templates.map((item) => ({ value: item.id, label: item.name }))}
                  placeholder="选择模板"
                />
                <Button onClick={importTemplate} disabled={!templateId}>导入模板</Button>
              </Space>
            </>
          )}

          <Table
            rowKey={(record) => record.code}
            pagination={false}
            dataSource={metrics}
            columns={[
              { title: '名称', dataIndex: 'name', width: 180 },
              { title: '编码', dataIndex: 'code', width: 180 },
              {
                title: '必填',
                width: 90,
                render: (_, record, index) => (
                  <Switch disabled={locked} checked={record.required} onChange={(checked) => updateMetric(index, { required: checked })} />
                ),
              },
              {
                title: '最小分值',
                width: 120,
                render: (_, record, index) => (
                  <InputNumber disabled={locked || record.inputComponent !== 'input-number'} value={record.minScore} onChange={(value) => updateMetric(index, { minScore: value ?? undefined })} />
                ),
              },
              {
                title: '最大分值',
                width: 120,
                render: (_, record, index) => (
                  <InputNumber disabled={locked || record.inputComponent !== 'input-number'} value={record.maxScore} onChange={(value) => updateMetric(index, { maxScore: value ?? undefined })} />
                ),
              },
              {
                title: '评分说明',
                render: (_, record, index) => (
                  <Input
                    disabled={locked}
                    value={record.scoringGuide}
                    onChange={(event) => updateMetric(index, { scoringGuide: event.target.value })}
                  />
                ),
              },
              {
                title: '操作',
                width: 90,
                render: (_, record) => !locked ? (
                  <Button
                    size="small"
                    danger
                    onClick={() => {
                      const ids = selectedDefinitionIds.filter((item) => item !== record.sourceMetricDefinitionId)
                      setSelectedDefinitionIds(ids)
                      setMetrics((current) => current.filter((item) => item.code !== record.code))
                    }}
                  >
                    移除
                  </Button>
                ) : null,
              },
            ]}
          />
        </Card>

        <Space>
          <Button onClick={() => navigate(editingId ? `/evaluations/${editingId}` : '/evaluations')}>取消</Button>
          <Button type="primary" htmlType="submit" loading={saving}>保存</Button>
        </Space>
      </Form>
    </Space>
  )
}
