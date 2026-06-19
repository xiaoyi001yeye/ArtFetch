import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Collapse,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Radio,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { ArrowLeftOutlined, PictureOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import * as api from '../../api'
import type { ExpertMobileReviewForm, ExpertReviewScore, MetricConfig } from '../../types'
import { ProtectedImageViewer, ProtectedPreviewImage } from '../../components/expert/ProtectedImage'
import { parseMetricOptions, parseStoredOptionValue, stringifyStoredOptionValue } from '../evaluation/metricInputUtils'
import { reviewStatusTag } from './expertUi'

type ReviewFormValues = {
  finalEstimateAmount?: number
  comment?: string
}

type ViewerType = 'preview' | 'original' | 'hd'

export default function ExpertArtworkReviewPage() {
  const { projectId, artworkId } = useParams()
  const navigate = useNavigate()
  const [form] = Form.useForm<ReviewFormValues>()
  const [data, setData] = useState<ExpertMobileReviewForm>()
  const [scores, setScores] = useState<Record<number, ExpertReviewScore>>({})
  const [dirty, setDirty] = useState(false)
  const [saving, setSaving] = useState(false)
  const [autoSaving, setAutoSaving] = useState(false)
  const [savedAt, setSavedAt] = useState<Date>()
  const [viewer, setViewer] = useState<ViewerType>()

  const load = async () => {
    if (!projectId || !artworkId) return
    try {
      const result = await api.getExpertMobileReview(Number(projectId), Number(artworkId))
      setData(result)
      form.setFieldsValue({
        finalEstimateAmount: result.review.finalEstimateAmount,
        comment: result.review.comment,
      })
      setScores(Object.fromEntries(result.metrics
        .filter((metric) => metric.id)
        .map((metric) => [
          metric.id!,
          result.review.scores.find((score) => score.projectMetricId === metric.id) || { projectMetricId: metric.id! },
        ])))
      setDirty(false)
    } catch (e: any) {
      message.error(e.message)
      navigate(`/expert/projects/${projectId}`, { replace: true })
    }
  }

  useEffect(() => {
    load()
  }, [projectId, artworkId])

  const readOnly = useMemo(() => {
    if (!data) return true
    if (!['PUBLISHED', 'IN_PROGRESS', 'REVIEW_REJECTED'].includes(data.evaluationStatus)) return true
    return ['SUBMITTED', 'RESUBMITTED'].includes(data.review.status)
  }, [data])

  const payload = () => {
    const values = form.getFieldsValue()
    return {
      finalEstimate: values.finalEstimateAmount == null ? undefined : String(values.finalEstimateAmount),
      finalEstimateAmount: values.finalEstimateAmount,
      finalEstimateCurrency: 'CNY',
      comment: values.comment,
      scores: Object.values(scores),
    }
  }

  const saveDraft = async (quiet = false) => {
    if (!data || readOnly || saving || autoSaving) return false
    quiet ? setAutoSaving(true) : setSaving(true)
    try {
      await api.saveExpertMobileReview(data.evaluationId, data.artwork.id, payload())
      setDirty(false)
      setSavedAt(new Date())
      if (!quiet) message.success('草稿已保存')
      return true
    } catch (e: any) {
      message.error(quiet ? '自动保存失败，请点击保存草稿' : e.message)
      return false
    } finally {
      quiet ? setAutoSaving(false) : setSaving(false)
    }
  }

  useEffect(() => {
    if (!dirty || readOnly) return
    const timer = window.setTimeout(() => saveDraft(true), 1500)
    return () => window.clearTimeout(timer)
  }, [dirty, readOnly, scores])

  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', beforeUnload)
    return () => window.removeEventListener('beforeunload', beforeUnload)
  }, [dirty])

  const updateScore = (metricId: number, patch: Partial<ExpertReviewScore>) => {
    setScores((current) => ({
      ...current,
      [metricId]: { ...current[metricId], projectMetricId: metricId, ...patch },
    }))
    setDirty(true)
  }

  const go = (path: string) => {
    if (!dirty) {
      navigate(path)
      return
    }
    Modal.confirm({
      title: '还有未保存的修改',
      content: '离开后，尚未保存的内容会丢失。',
      okText: '继续离开',
      cancelText: '留在当前页',
      onOk: () => navigate(path),
    })
  }

  const submit = async (goNext: boolean) => {
    if (!data) return
    setSaving(true)
    try {
      await form.validateFields()
      await api.submitExpertMobileReview(data.evaluationId, data.artwork.id, payload())
      setDirty(false)
      message.success('评估已提交')
      if (goNext) {
        const project = await api.getExpertMobileProject(data.evaluationId)
        navigate(project.nextArtworkId
          ? `/expert/projects/${data.evaluationId}/artworks/${project.nextArtworkId}/review`
          : `/expert/projects/${data.evaluationId}`, { replace: true })
      } else {
        await load()
      }
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  const renderMetricInput = (metric: MetricConfig) => {
    if (!metric.id) return null
    const score = scores[metric.id] || { projectMetricId: metric.id }
    if (metric.inputComponent === 'input-number') {
      return (
        <InputNumber
          value={score.score}
          disabled={readOnly}
          min={metric.minScore}
          max={metric.maxScore}
          step={metric.scoreStep}
          inputMode="decimal"
          style={{ width: '100%' }}
          onChange={(value) => updateScore(metric.id!, { score: value ?? undefined })}
        />
      )
    }
    if (metric.inputComponent === 'textarea') {
      return <Input.TextArea autoSize={{ minRows: 3 }} value={score.textValue} disabled={readOnly} onChange={(event) => updateScore(metric.id!, { textValue: event.target.value })} />
    }
    const options = parseMetricOptions(metric.optionValues)
    if (metric.inputComponent === 'radio') {
      return (
        <Radio.Group className="expert-mobile-metric-options" value={parseStoredOptionValue(metric.inputComponent, score.optionValue)} disabled={readOnly} onChange={(event) => updateScore(metric.id!, { optionValue: stringifyStoredOptionValue(metric.inputComponent, event.target.value) })}>
          {options.map((option) => <Radio key={option.value} value={option.value}>{option.label}</Radio>)}
        </Radio.Group>
      )
    }
    if (metric.inputComponent === 'checkbox-group') {
      return (
        <Checkbox.Group className="expert-mobile-metric-options" value={parseStoredOptionValue(metric.inputComponent, score.optionValue) as string[]} disabled={readOnly} onChange={(value) => updateScore(metric.id!, { optionValue: stringifyStoredOptionValue(metric.inputComponent, value as string[]) })}>
          {options.map((option) => <Checkbox key={option.value} value={option.value}>{option.label}</Checkbox>)}
        </Checkbox.Group>
      )
    }
    if (metric.inputComponent === 'select') {
      return <Select value={parseStoredOptionValue(metric.inputComponent, score.optionValue) as string | undefined} disabled={readOnly} allowClear options={options} style={{ width: '100%' }} onChange={(value) => updateScore(metric.id!, { optionValue: stringifyStoredOptionValue(metric.inputComponent, value) })} />
    }
    return <Input value={score.textValue} disabled={readOnly} onChange={(event) => updateScore(metric.id!, { textValue: event.target.value })} />
  }

  if (!data) return <Spin />

  const viewerUrl = viewer === 'preview'
    ? api.expertPreviewImageUrl(data.evaluationId, data.artwork.id)
    : viewer === 'original'
    ? api.expertOriginalImageUrl(data.evaluationId, data.artwork.id)
    : viewer === 'hd'
    ? api.expertHdImageUrl(data.evaluationId, data.artwork.id)
    : undefined

  return (
    <div className="expert-mobile-stack">
      <div className="expert-mobile-title-row">
        <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => go(`/expert/projects/${data.evaluationId}`)} style={{ paddingInline: 0 }}>
          返回作品列表
        </Button>
        <Typography.Text className="expert-mobile-muted">第 {data.artworkIndex} / {data.artworkTotal} 件</Typography.Text>
      </div>
      {readOnly && <Alert type="info" showIcon message={data.evaluationStatus === 'IN_REVIEW' ? '项目审核中，暂不能修改' : '当前评估内容为只读状态'} />}
      {data.review.rejectedReason && <Alert type="warning" showIcon message="审核驳回，请修改" description={data.review.rejectedReason} />}
      <Card className="expert-mobile-card">
        <div className="expert-mobile-stack">
          <div className="expert-mobile-preview" style={{ width: '100%', height: 240 }} onClick={() => data.artwork.previewImageAvailable && setViewer('preview')}>
            <ProtectedPreviewImage
              url={data.artwork.previewImageAvailable ? api.expertPreviewImageUrl(data.evaluationId, data.artwork.id) : undefined}
              alt={data.artwork.title}
            />
          </div>
          <Space wrap>
            <Button icon={<PictureOutlined />} disabled={!data.artwork.originalImageAvailable} onClick={() => setViewer('original')}>查看原图</Button>
            <Button icon={<PictureOutlined />} disabled={!data.artwork.hdImageAvailable} onClick={() => {
              Modal.confirm({
                title: '加载高清大图',
                content: '高清大图可能消耗较多流量，确认继续加载吗？',
                okText: '继续加载',
                onOk: () => setViewer('hd'),
              })
            }}>查看高清大图</Button>
          </Space>
          {!data.artwork.originalImageAvailable && <Typography.Text className="expert-mobile-muted">原图尚未准备好</Typography.Text>}
          {!data.artwork.hdImageAvailable && <Typography.Text className="expert-mobile-muted">高清图尚未准备好</Typography.Text>}
        </div>
      </Card>
      <Card className="expert-mobile-card">
        <div className="expert-mobile-stack" style={{ gap: 8 }}>
          <Space wrap>{reviewStatusTag(data.review.status)}</Space>
          <Typography.Title level={4} style={{ margin: 0 }}>{data.artwork.title}</Typography.Title>
          <Typography.Text>{data.artwork.artist || '—'}{data.artwork.lotNumber ? ` · ${data.artwork.lotNumber}` : ''}</Typography.Text>
          <Typography.Text className="expert-mobile-muted">材质：{data.artwork.medium || '—'}</Typography.Text>
          <Typography.Text className="expert-mobile-muted">尺寸：{data.artwork.dimensions || '—'}</Typography.Text>
          <Typography.Text className="expert-mobile-muted">拍卖公司：{data.artwork.auctionHouse || '—'}</Typography.Text>
          <Typography.Text className="expert-mobile-muted">拍卖日期：{data.artwork.auctionDate || '—'}</Typography.Text>
          <Typography.Text className="expert-mobile-muted">原始估价：{data.artwork.valuation || '—'}</Typography.Text>
          <Collapse ghost items={[{
            key: 'more',
            label: '展开更多作品信息',
            children: (
              <div className="expert-mobile-stack" style={{ gap: 6 }}>
                <Typography.Text>形制：{data.artwork.format || '—'}</Typography.Text>
                <Typography.Text>拍卖会：{data.artwork.auctionName || '—'}</Typography.Text>
                <Typography.Text>专场：{data.artwork.auctionSession || '—'}</Typography.Text>
                <Typography.Text>地点：{data.artwork.auctionLocation || '—'}</Typography.Text>
                <Typography.Text>预展时间：{data.artwork.previewTime || '—'}</Typography.Text>
                <Typography.Text>预展地点：{data.artwork.previewLocation || '—'}</Typography.Text>
                <Typography.Paragraph>{data.artwork.description || '暂无补充说明'}</Typography.Paragraph>
              </div>
            ),
          }]} />
        </div>
      </Card>
      {data.metrics.map((metric) => metric.id && (
        <Card key={metric.id} className="expert-mobile-card">
          <div className="expert-mobile-stack" style={{ gap: 8 }}>
            <Space wrap>
              <Typography.Text strong>{metric.name}</Typography.Text>
              {metric.required && <Tag color="red">必填</Tag>}
            </Space>
            {metric.scoringGuide && <Typography.Text className="expert-mobile-muted">{metric.scoringGuide}</Typography.Text>}
            {renderMetricInput(metric)}
            <Input value={scores[metric.id]?.comment} disabled={readOnly} placeholder="指标备注（选填）" onChange={(event) => updateScore(metric.id!, { comment: event.target.value })} />
          </div>
        </Card>
      ))}
      <Card className="expert-mobile-card" title="整体结论">
        <Form form={form} layout="vertical" onValuesChange={() => setDirty(true)}>
          <Form.Item name="finalEstimateAmount" label="最终估值金额（CNY）" rules={[{ required: true, message: '请输入最终估值金额' }]}>
            <InputNumber min={0.01} precision={2} disabled={readOnly} style={{ width: '100%' }} inputMode="decimal" />
          </Form.Item>
          <Form.Item name="comment" label="整体评语">
            <Input.TextArea disabled={readOnly} autoSize={{ minRows: 3 }} />
          </Form.Item>
        </Form>
        <Typography.Text className="expert-mobile-muted">
          {autoSaving ? '正在自动保存...' : dirty ? '有未保存修改' : savedAt ? `已保存 ${savedAt.toLocaleTimeString()}` : '填写后将自动保存草稿'}
        </Typography.Text>
      </Card>
      <div className="expert-mobile-action-bar">
        <div className="expert-mobile-action-bar-inner">
          <Button disabled={!data.previousArtworkId} onClick={() => data.previousArtworkId && go(`/expert/projects/${data.evaluationId}/artworks/${data.previousArtworkId}/review`)}>上一件</Button>
          <Button disabled={readOnly} loading={saving} onClick={() => saveDraft(false)}>保存草稿</Button>
          <Button type="primary" disabled={readOnly} loading={saving} onClick={() => submit(true)}>提交并下一件</Button>
        </div>
      </div>
      <ProtectedImageViewer url={viewerUrl} title={viewer === 'hd' ? '高清大图' : viewer === 'original' ? '原图' : '预览图'} open={Boolean(viewer)} onClose={() => setViewer(undefined)} />
    </div>
  )
}
