import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Checkbox, Empty, Form, Input, message, Select, Skeleton, Space, Tabs, Tag, Typography } from 'antd'
import { ArrowLeftOutlined, CheckOutlined, LeftOutlined, RightOutlined, SaveOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import * as api from '../../api'
import type { ArtworkPreview, AuthUser, CriterionItem, EvaluationMetricTemplate, MetricConfig } from '../../types'

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

const steps = ['基本信息', '筛选作品', '确认作品', '专家与指标', '确认创建']

const defaultCriterion = { fieldName: 'artist', operator: 'contains', valueType: 'text' }

const userLabel = (user: AuthUser) => `${user.displayName || user.username} (${user.username})`

export default function MobileEvaluationNewPage() {
  const navigate = useNavigate()
  const { id } = useParams()
  const { user } = useAuth()
  const editingId = id ? Number(id) : undefined
  const editing = editingId != null && Number.isFinite(editingId)
  const [form] = Form.useForm<ProjectFormValues>()
  const [step, setStep] = useState(0)
  const [users, setUsers] = useState<AuthUser[]>([])
  const [templates, setTemplates] = useState<EvaluationMetricTemplate[]>([])
  const [templateMetrics, setTemplateMetrics] = useState<MetricConfig[]>([])
  const [templateId, setTemplateId] = useState<number | undefined>()
  const [previewArtworks, setPreviewArtworks] = useState<ArtworkPreview[]>([])
  const [selectedArtworkIds, setSelectedArtworkIds] = useState<number[]>([])
  const [selectedExpertIds, setSelectedExpertIds] = useState<number[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [loadingTemplate, setLoadingTemplate] = useState(false)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [creating, setCreating] = useState(false)

  const criteriaFieldMap = useMemo(() => new Map(criteriaFields.map((field) => [field.value, field])), [])
  const expertUsers = useMemo(() => users.filter((item) => item.status === 'ENABLED' && item.roles.includes('EXPERT')), [users])
  const auditorUsers = useMemo(() => users.filter((item) => item.status === 'ENABLED' && (item.roles.includes('AUDITOR') || item.roles.includes('ADMIN'))), [users])
  const enabledTemplates = useMemo(() => templates.filter((item) => item.enabled), [templates])
  const selectedTemplate = useMemo(() => enabledTemplates.find((item) => item.id === templateId), [enabledTemplates, templateId])
  const selectedExperts = useMemo(() => expertUsers.filter((item) => selectedExpertIds.includes(item.id)), [expertUsers, selectedExpertIds])

  useEffect(() => {
    setLoadingOptions(true)
    const projectRequest = editingId ? api.getEvaluation(editingId) : Promise.resolve(undefined)
    Promise.all([api.listUsers(0, 500), api.listEvaluationMetricTemplates(0, 200), projectRequest])
      .then(([userResult, templateResult, project]) => {
        setUsers(userResult.items)
        setTemplates(templateResult.items)
        if (project) {
          if (!['DRAFT', 'PENDING'].includes(project.status)) {
            message.error('只有未发布项目可以编辑')
            navigate(`/m/evaluations/${project.id}`, { replace: true })
            return
          }
          form.setFieldsValue({
            name: project.name,
            description: project.description,
            auditorId: project.auditorId,
            criteria: project.criteria.length > 0 ? project.criteria : [defaultCriterion as CriterionItem],
          })
          setPreviewArtworks(project.artworks.map((item) => ({
            id: item.artworkId,
            title: item.artwork.title,
            artist: item.artwork.artist,
            lotNumber: item.artwork.lotNumber,
            medium: item.artwork.medium,
            valuation: item.artwork.valuation,
            auctionHouse: item.artwork.auctionHouse,
            auctionDate: item.artwork.auctionDate,
            imageUrl: item.artwork.imageUrl,
          })))
          setSelectedArtworkIds(project.artworks.map((item) => item.artworkId))
          setSelectedExpertIds(project.experts.map((item) => item.expertId))
          setTemplateMetrics(project.metrics)
          const sourceTemplateIds = Array.from(new Set(project.metrics.map((item) => item.sourceTemplateId).filter(Boolean)))
          if (sourceTemplateIds.length === 1) setTemplateId(sourceTemplateIds[0])
        } else {
          form.setFieldsValue({ criteria: [defaultCriterion] as CriterionItem[] })
        }
        if (!project && user?.roles.some((role) => role === 'ADMIN' || role === 'AUDITOR')) {
          form.setFieldValue('auditorId', user.id)
        }
      })
      .catch((e: any) => message.error(e.message))
      .finally(() => setLoadingOptions(false))
  }, [editingId, form, navigate, user])

  const buildCriteria = () => (form.getFieldValue('criteria') || []).map((item: CriterionItem) => {
    const field = criteriaFieldMap.get(item.fieldName)
    return { ...item, fieldLabel: field?.label || item.fieldName }
  })

  const updateCriterionField = (index: number, nextFieldName: string) => {
    const fieldConfig = criteriaFieldMap.get(nextFieldName)
    form.setFieldValue(['criteria', index, 'operator'], fieldConfig?.defaultOperator || 'contains')
    form.setFieldValue(['criteria', index, 'valueType'], fieldConfig?.valueType || 'text')
    form.setFieldValue(['criteria', index, 'value'], fieldConfig?.valueType === 'boolean' ? 'true' : undefined)
  }

  const validateBasic = async () => {
    await form.validateFields(['name', 'auditorId'])
    setStep(1)
  }

  const preview = async () => {
    try {
      await form.validateFields(['criteria'])
      setPreviewLoading(true)
      const result = await api.previewEvaluationArtworks({ criteria: buildCriteria(), page: 0, size: 100 })
      setPreviewArtworks(result.items)
      setSelectedArtworkIds(result.items.map((item) => item.id))
      setStep(2)
    } catch (e: any) {
      if (e?.message) message.error(e.message)
    } finally {
      setPreviewLoading(false)
    }
  }

  const chooseTemplate = async (id?: number) => {
    setTemplateId(id)
    setTemplateMetrics([])
    if (!id) return
    setLoadingTemplate(true)
    try {
      const items = await api.getEvaluationMetricTemplateItems(id)
      setTemplateMetrics(items.map((item, index) => ({ ...item, sortOrder: item.sortOrder || index + 1 })))
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoadingTemplate(false)
    }
  }

  const toggleArtwork = (id: number, checked: boolean) => {
    setSelectedArtworkIds((current) => checked ? Array.from(new Set([...current, id])) : current.filter((item) => item !== id))
  }

  const validateArtworkSelection = () => {
    if (selectedArtworkIds.length === 0) {
      message.warning('请至少选择一件艺术品')
      return
    }
    setStep(3)
  }

  const validateExpertsAndMetrics = () => {
    if (selectedExpertIds.length === 0) {
      message.warning('请至少选择一位专家')
      return
    }
    if (!templateId || templateMetrics.length === 0) {
      message.warning('请选择一个包含指标的模板')
      return
    }
    setStep(4)
  }

  const create = async () => {
    try {
      const values = await form.validateFields()
      if (selectedArtworkIds.length === 0) throw new Error('请至少选择一件艺术品')
      if (selectedExpertIds.length === 0) throw new Error('请至少选择一位专家')
      if (!templateId || templateMetrics.length === 0) throw new Error('请选择一个包含指标的模板')
      setCreating(true)
      await api.createEvaluation({
        name: values.name,
        description: values.description,
        auditorId: values.auditorId,
        criteria: buildCriteria(),
        artworkIds: selectedArtworkIds,
        expertIds: selectedExpertIds,
        metrics: templateMetrics,
      })
      message.success('评估项目已创建')
      navigate('/m/evaluations', { replace: true })
    } catch (e: any) {
      if (e?.message) message.error(e.message)
    } finally {
      setCreating(false)
    }
  }

  const saveEditTab = async () => {
    if (!editingId) return
    try {
      const values = await form.validateFields(['name', 'description', 'auditorId', 'criteria'])
      const payload: Parameters<typeof api.updateEvaluation>[1] = {
        name: values.name,
        description: values.description,
        auditorId: values.auditorId,
        criteria: buildCriteria(),
      }
      if (step >= 2) {
        if (selectedArtworkIds.length === 0) throw new Error('请至少选择一件艺术品')
        if (selectedExpertIds.length === 0) throw new Error('请至少选择一位专家')
        if (templateMetrics.length === 0) throw new Error('请至少配置一个评估指标')
        payload.artworkIds = selectedArtworkIds
        payload.expertIds = selectedExpertIds
        payload.metrics = templateMetrics
      }
      setCreating(true)
      const result = await api.updateEvaluation(editingId, payload)
      setTemplateMetrics(result.metrics)
      message.success(`${steps[step]}已保存`)
    } catch (e: any) {
      if (e?.message) message.error(e.message)
    } finally {
      setCreating(false)
    }
  }

  const currentAuditor = auditorUsers.find((item) => item.id === form.getFieldValue('auditorId'))

  return (
    <MobileDataLayout title={editing ? '编辑评估项目' : '新建评估项目'} hideNav>
      <div className="mobile-detail-topbar">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(editingId ? `/m/evaluations/${editingId}` : '/m/evaluations')}>返回</Button>
      </div>

      {editing ? (
        <Tabs
          className="mobile-evaluation-edit-tabs"
          activeKey={String(step)}
          onChange={(key) => setStep(Number(key))}
          items={['基本信息', '筛选作品', '确认作品', '专家', '指标'].map((label, index) => ({ key: String(index), label }))}
        />
      ) : (
        <div className="mobile-evaluation-steps">
          {steps.map((item, index) => (
            <div key={item} className={index === step ? 'active' : index < step ? 'done' : ''}>
              <span>{index + 1}</span>
              <strong>{item}</strong>
            </div>
          ))}
        </div>
      )}

      {loadingOptions ? (
        <div className="mobile-data-stack">
          <Skeleton active />
          <Skeleton active />
        </div>
      ) : (
        <Form form={form} layout="vertical">
          {step === 0 && (
            <section className="mobile-detail-section">
              <Form.Item name="name" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
                <Input placeholder="项目名称" />
              </Form.Item>
              <Form.Item name="auditorId" label="审核人" rules={[{ required: true, message: '请选择审核人' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择审核人"
                  options={auditorUsers.map((item) => ({ value: item.id, label: userLabel(item) }))}
                />
              </Form.Item>
              <Form.Item name="description" label="项目说明">
                <Input.TextArea rows={3} placeholder="可选" />
              </Form.Item>
            </section>
          )}

          {step === 1 && (
            <section className="mobile-detail-section">
              <Form.List name="criteria">
                {(fields, { add, remove }) => (
                  <div className="mobile-data-stack">
                    {fields.map((field) => (
                      <div key={field.key} className="mobile-evaluation-criterion">
                        <Form.Item
                          {...field}
                          name={[field.name, 'fieldName']}
                          label="字段"
                          rules={[{ required: true, message: '请选择字段' }]}
                        >
                          <Select options={criteriaFields} placeholder="字段" onChange={(value) => updateCriterionField(field.name, value)} />
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
                                  label="操作符"
                                  rules={[{ required: true, message: '请选择操作符' }]}
                                >
                                  <Select options={filteredOperators} placeholder="操作符" />
                                </Form.Item>
                                {!hideValueInput && (
                                  <Form.Item {...field} name={[field.name, 'value']} label="值">
                                    {isBooleanField ? (
                                      <Select options={booleanValueOptions} placeholder="请选择" />
                                    ) : (
                                      <Input placeholder="筛选值" />
                                    )}
                                  </Form.Item>
                                )}
                              </>
                            )
                          }}
                        </Form.Item>
                        <Button danger onClick={() => remove(field.name)} disabled={fields.length <= 1}>删除条件</Button>
                      </div>
                    ))}
                    <Button onClick={() => add(defaultCriterion)}>添加条件</Button>
                  </div>
                )}
              </Form.List>
            </section>
          )}

          {step === 2 && (
            <section className="mobile-detail-section">
              {previewArtworks.length >= 100 && (
                <Alert type="info" showIcon message="当前仅使用预览返回的前 100 件作品；如需处理更多作品，请收窄筛选条件或到桌面端处理。" />
              )}
              <div className="mobile-dataset-select-actions">
                <Button onClick={() => setSelectedArtworkIds(previewArtworks.map((item) => item.id))}>全选</Button>
                <Button onClick={() => setSelectedArtworkIds([])}>清空</Button>
                <Tag color="blue">已选 {selectedArtworkIds.length}/{previewArtworks.length}</Tag>
              </div>
              {previewArtworks.length === 0 ? (
                <Empty description="没有匹配作品" />
              ) : (
                <div className="mobile-data-stack">
                  {previewArtworks.map((artwork) => (
                    <label className="mobile-evaluation-artwork" key={artwork.id}>
                      <Checkbox
                        checked={selectedArtworkIds.includes(artwork.id)}
                        onChange={(event) => toggleArtwork(artwork.id, event.target.checked)}
                      />
                      <div className="mobile-artwork-thumb">
                        {artwork.imageUrl ? <img src={artwork.imageUrl} alt={artwork.title} /> : <span>无图</span>}
                      </div>
                      <div className="mobile-artwork-info">
                        <Typography.Text strong className="mobile-artwork-title">{artwork.title}</Typography.Text>
                        <div className="mobile-artwork-meta">{artwork.artist || '未知作者'}{artwork.lotNumber ? ` / ${artwork.lotNumber}` : ''}</div>
                        <div className="mobile-artwork-meta">{[artwork.auctionHouse, artwork.auctionDate].filter(Boolean).join(' · ') || '拍卖信息待补充'}</div>
                      </div>
                    </label>
                  ))}
                </div>
              )}
            </section>
          )}

          {step === 3 && (
            <section className="mobile-detail-section">
              <Form.Item label="专家">
                <Select
                  mode="multiple"
                  value={selectedExpertIds}
                  onChange={setSelectedExpertIds}
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择专家账号"
                  options={expertUsers.map((item) => ({ value: item.id, label: userLabel(item) }))}
                />
              </Form.Item>
              {!editing && (
                <>
                  <Form.Item label="指标模板">
                    <Select
                      allowClear
                      value={templateId}
                      onChange={chooseTemplate}
                      loading={loadingTemplate}
                      showSearch
                      optionFilterProp="label"
                      placeholder="选择指标模板"
                      options={enabledTemplates.map((item) => ({ value: item.id, label: `${item.name}（${item.itemCount} 项）` }))}
                    />
                  </Form.Item>
                  {enabledTemplates.length === 0 && <Alert type="warning" showIcon message="暂无可用指标模板，请先在桌面端配置指标模板。" />}
                  {templateId && (
                    <div className="mobile-evaluation-template-panel">
                      <Typography.Text strong>{selectedTemplate?.name || '已选模板'}</Typography.Text>
                      <div className="mobile-dataset-meta">指标数：{templateMetrics.length}</div>
                      <Space wrap size={[4, 6]}>
                        {templateMetrics.map((item) => <Tag key={item.code}>{item.name}</Tag>)}
                      </Space>
                    </div>
                  )}
                </>
              )}
            </section>
          )}

          {step === 4 && editing && (
            <section className="mobile-detail-section">
              <Form.Item label="指标模板">
                <Select
                  allowClear
                  value={templateId}
                  onChange={chooseTemplate}
                  loading={loadingTemplate}
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择模板以替换当前指标"
                  options={enabledTemplates.map((item) => ({ value: item.id, label: `${item.name}（${item.itemCount} 项）` }))}
                />
              </Form.Item>
              {enabledTemplates.length === 0 && <Alert type="warning" showIcon message="暂无可用指标模板，请先在桌面端配置指标模板。" />}
              <div className="mobile-evaluation-template-panel">
                <Typography.Text strong>{selectedTemplate?.name || '当前项目指标'}</Typography.Text>
                <div className="mobile-dataset-meta">指标数：{templateMetrics.length}</div>
                <Space wrap size={[4, 6]}>
                  {templateMetrics.map((item) => <Tag key={item.code}>{item.name}</Tag>)}
                </Space>
              </div>
            </section>
          )}

          {step === 4 && !editing && (
            <section className="mobile-detail-section">
              <div className="mobile-evaluation-summary">
                <div><span>项目名称</span><strong>{form.getFieldValue('name')}</strong></div>
                <div><span>审核人</span><strong>{currentAuditor ? userLabel(currentAuditor) : '—'}</strong></div>
                <div><span>筛选条件</span><strong>{(form.getFieldValue('criteria') || []).length} 条</strong></div>
                <div><span>作品数量</span><strong>{selectedArtworkIds.length} 件</strong></div>
                <div><span>专家数量</span><strong>{selectedExpertIds.length} 位</strong></div>
                <div><span>指标模板</span><strong>{selectedTemplate?.name || '—'}</strong></div>
                <div><span>指标数量</span><strong>{templateMetrics.length} 项</strong></div>
              </div>
              <Space wrap size={[4, 6]}>
                {selectedExperts.map((item) => <Tag color="blue" key={item.id}>{item.displayName || item.username}</Tag>)}
              </Space>
            </section>
          )}
        </Form>
      )}

      {editing ? (
        <div className="mobile-evaluation-wizard-actions">
          {step === 1 ? (
            <Button icon={<CheckOutlined />} loading={previewLoading} onClick={preview}>重新预览</Button>
          ) : (
            <Button onClick={() => navigate(`/m/evaluations/${editingId}`)}>取消</Button>
          )}
          <Button type="primary" icon={<SaveOutlined />} loading={creating} onClick={saveEditTab}>保存本页</Button>
        </div>
      ) : (
        <div className="mobile-evaluation-wizard-actions">
          <Button icon={<LeftOutlined />} disabled={step === 0 || creating} onClick={() => setStep(step - 1)}>上一步</Button>
          {step === 0 && <Button type="primary" icon={<RightOutlined />} onClick={validateBasic}>下一步</Button>}
          {step === 1 && <Button type="primary" icon={<CheckOutlined />} loading={previewLoading} onClick={preview}>预览作品</Button>}
          {step === 2 && <Button type="primary" icon={<RightOutlined />} onClick={validateArtworkSelection}>下一步</Button>}
          {step === 3 && <Button type="primary" icon={<RightOutlined />} onClick={validateExpertsAndMetrics}>下一步</Button>}
          {step === 4 && <Button type="primary" icon={<SaveOutlined />} loading={creating} onClick={create}>创建项目</Button>}
        </div>
      )}
    </MobileDataLayout>
  )
}
