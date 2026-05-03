import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Checkbox, Descriptions, Form, Image, Input, InputNumber, List, message, Radio, Select, Space, Tag, Typography } from 'antd'
import { PictureOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import * as api from '../../api'
import type { EvaluationArtworkItem, ExpertReviewForm, ExpertReviewScore } from '../../types'
import { permissions } from '../../auth/permissions'
import { useAuth } from '../../auth/AuthContext'
import {
  getInputComponentLabel,
  parseMetricOptions,
  parseStoredOptionValue,
  stringifyStoredOptionValue,
} from './metricInputUtils'

type ReviewFormValues = {
  finalEstimate?: string
  finalEstimateCurrency?: string
  comment?: string
}

export default function ExpertReviewPage() {
  const { evaluationId, artworkId } = useParams()
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const [data, setData] = useState<ExpertReviewForm | null>(null)
  const [artworks, setArtworks] = useState<EvaluationArtworkItem[]>([])
  const [scores, setScores] = useState<Record<number, ExpertReviewScore>>({})
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<ReviewFormValues>()
  const currentArtworkId = artworkId ? Number(artworkId) : undefined

  const readOnly = useMemo(() => {
    if (!data) return true
    if (['COMPLETED', 'CANCELLED', 'IN_REVIEW'].includes(data.evaluationStatus)) return true
    return ['SUBMITTED', 'RESUBMITTED'].includes(data.review.status)
  }, [data])

  const load = async () => {
    if (!evaluationId || !artworkId) return
    try {
      const [result, artworkItems] = await Promise.all([
        api.getMyExpertReview(Number(evaluationId), Number(artworkId)),
        api.listEvaluationArtworks(Number(evaluationId)),
      ])
      setData(result)
      setArtworks(artworkItems)
      form.setFieldsValue({
        finalEstimate: result.review.finalEstimate,
        finalEstimateCurrency: result.review.finalEstimateCurrency,
        comment: result.review.comment,
      })
      const scoreMap: Record<number, ExpertReviewScore> = {}
      result.metrics.forEach((metric) => {
        const current = result.review.scores.find((score) => score.projectMetricId === metric.id)
        if (metric.id) {
          scoreMap[metric.id] = current || { projectMetricId: metric.id }
        }
      })
      setScores(scoreMap)
    } catch (e: any) {
      message.error(e.message)
      navigate('/my-evaluations')
    }
  }

  useEffect(() => {
    load()
  }, [evaluationId, artworkId])

  const buildPayload = async () => {
    const values = await form.validateFields()
    return {
      finalEstimate: values.finalEstimate,
      finalEstimateCurrency: values.finalEstimateCurrency,
      comment: values.comment,
      scores: Object.values(scores),
    }
  }

  const submitSave = async () => {
    if (!data) return
    setSaving(true)
    try {
      const payload = await buildPayload()
      await api.saveMyExpertReview(data.evaluationId, data.artwork.id, payload)
      message.success('草稿已保存')
      load()
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  const submitReview = async () => {
    if (!data) return
    setSaving(true)
    try {
      const payload = await buildPayload()
      await api.submitMyExpertReview(data.evaluationId, data.artwork.id, payload)
      message.success('评估已提交')
      load()
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  if (!data) return null

  const currentArtworkIndex = artworks.findIndex((item) => item.artworkId === currentArtworkId)
  const previousArtwork = currentArtworkIndex > 0 ? artworks[currentArtworkIndex - 1] : null
  const nextArtwork = currentArtworkIndex >= 0 && currentArtworkIndex < artworks.length - 1 ? artworks[currentArtworkIndex + 1] : null

  const navigateToArtwork = (targetArtworkId: number) => {
    navigate(`/evaluations/${data.evaluationId}/artworks/${targetArtworkId}/review`)
  }

  const canViewProtectedImage = hasPermission(permissions.artworkImageView)
  const canViewOriginal = canViewProtectedImage && Boolean(
    data.artwork.originalImageAvailable || data.artwork.originalImageSourceUrl || data.artwork.sourceUrl || data.artwork.imageUrl,
  )
  const canViewHd = canViewProtectedImage && Boolean(data.artwork.hdImageAvailable)
  const imageActionText = canViewHd ? '点击查看高清大图' : canViewOriginal ? '点击查看原图' : undefined

  const handleOpenArtworkImage = async () => {
    try {
      if (canViewHd) {
        await api.openProtectedBlob(api.hdImageViewUrl(data.artwork.id))
        return
      }
      if (canViewOriginal) {
        await api.openProtectedBlob(api.originalImageViewUrl(data.artwork.id))
      }
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const renderMetricInput = (metricId: number, score: ExpertReviewScore, inputComponent?: string, optionValues?: string) => {
    const commonDisabled = readOnly

    if (inputComponent === 'input-number') {
      return (
        <InputNumber
          disabled={commonDisabled}
          value={score.score}
          onChange={(value) => setScores((current) => ({ ...current, [metricId]: { ...score, score: value ?? undefined } }))}
          placeholder="分值"
        />
      )
    }

    if (inputComponent === 'textarea') {
      return (
        <Input.TextArea
          disabled={commonDisabled}
          value={score.textValue}
          onChange={(event) => setScores((current) => ({ ...current, [metricId]: { ...score, textValue: event.target.value } }))}
          placeholder="文本评价"
          rows={3}
          style={{ minWidth: 320 }}
        />
      )
    }

    const options = parseMetricOptions(optionValues)
    if (inputComponent === 'radio') {
      return (
        <Radio.Group
          disabled={commonDisabled}
          options={options}
          optionType="default"
          value={parseStoredOptionValue(inputComponent, score.optionValue) as string | undefined}
          onChange={(event) => setScores((current) => ({
            ...current,
            [metricId]: { ...score, optionValue: stringifyStoredOptionValue(inputComponent, event.target.value) },
          }))}
        />
      )
    }

    if (inputComponent === 'checkbox-group') {
      return (
        <Checkbox.Group
          disabled={commonDisabled}
          options={options}
          value={parseStoredOptionValue(inputComponent, score.optionValue) as string[]}
          onChange={(value) => setScores((current) => ({
            ...current,
            [metricId]: { ...score, optionValue: stringifyStoredOptionValue(inputComponent, value as string[]) },
          }))}
        />
      )
    }

    if (inputComponent === 'select') {
      return (
        <Select
          disabled={commonDisabled}
          options={options}
          value={parseStoredOptionValue(inputComponent, score.optionValue) as string | undefined}
          onChange={(value) => setScores((current) => ({
            ...current,
            [metricId]: { ...score, optionValue: stringifyStoredOptionValue(inputComponent, value) },
          }))}
          placeholder="请选择"
          allowClear
          style={{ minWidth: 240 }}
        />
      )
    }

    return (
      <Input
        disabled={commonDisabled}
        value={score.textValue}
        onChange={(event) => setScores((current) => ({ ...current, [metricId]: { ...score, textValue: event.target.value } }))}
        placeholder="文本评价"
        style={{ width: 320 }}
      />
    )
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start" wrap>
        <Space direction="vertical" size={4}>
          <Typography.Title level={4} style={{ margin: 0 }}>{data.evaluationName} / 我的评估</Typography.Title>
          <Typography.Text type="secondary">
            {currentArtworkIndex >= 0 ? `当前第 ${currentArtworkIndex + 1} 件，共 ${artworks.length} 件` : `共 ${artworks.length} 件作品`}
          </Typography.Text>
        </Space>
        <Space wrap>
          <Button disabled={!previousArtwork} onClick={() => previousArtwork && navigateToArtwork(previousArtwork.artworkId)}>
            上一个
          </Button>
          <Button disabled={!nextArtwork} onClick={() => nextArtwork && navigateToArtwork(nextArtwork.artworkId)}>
            下一个
          </Button>
        </Space>
      </Space>

      <Card>
        <Descriptions bordered column={2}>
          <Descriptions.Item label="状态"><Tag>{data.review.status}</Tag></Descriptions.Item>
          <Descriptions.Item label="项目状态"><Tag>{data.evaluationStatus}</Tag></Descriptions.Item>
          <Descriptions.Item label="标题" span={2}>{data.artwork.title}</Descriptions.Item>
          <Descriptions.Item label="作者">{data.artwork.artist || '—'}</Descriptions.Item>
          <Descriptions.Item label="拍品编号">{data.artwork.lotNumber || '—'}</Descriptions.Item>
          <Descriptions.Item label="材质">{data.artwork.medium || '—'}</Descriptions.Item>
          <Descriptions.Item label="拍卖公司">{data.artwork.auctionHouse || '—'}</Descriptions.Item>
          <Descriptions.Item label="拍卖日期">{data.artwork.auctionDate || '—'}</Descriptions.Item>
          <Descriptions.Item label="估价">{data.artwork.valuation || '—'}</Descriptions.Item>
          <Descriptions.Item label="审核驳回原因" span={2}>{data.review.rejectedReason || '—'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card
        title="作品图片"
        extra={imageActionText ? (
          <Button icon={<PictureOutlined />} onClick={handleOpenArtworkImage}>
            {imageActionText}
          </Button>
        ) : undefined}
      >
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {data.artwork.imageUrl ? (
            <Image
              src={data.artwork.imageUrl}
              alt={data.artwork.title}
              preview={false}
              onClick={imageActionText ? handleOpenArtworkImage : undefined}
              style={{
                width: '100%',
                maxWidth: 320,
                maxHeight: 320,
                objectFit: 'contain',
                borderRadius: 8,
                cursor: imageActionText ? 'pointer' : 'default',
              }}
              fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
            />
          ) : (
            <div style={{
              width: 320,
              maxWidth: '100%',
              height: 240,
              background: '#f0f0f0',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#aaa',
            }}>
              <Typography.Text type="secondary">暂无缩略图</Typography.Text>
            </div>
          )}
          {(canViewHd || canViewOriginal) && (
            <Typography.Text type="secondary">
              {canViewHd ? '当前显示缩略图，点击可打开高清大图。' : '当前显示缩略图，点击可打开原图。'}
            </Typography.Text>
          )}
        </Space>
      </Card>

      <Card title="评分指标">
        <List
          dataSource={data.metrics}
          renderItem={(metric) => {
            if (!metric.id) return null
            const score = scores[metric.id] || { projectMetricId: metric.id }
            return (
              <List.Item key={metric.id}>
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  <Space>
                    <Typography.Text strong>{metric.name}</Typography.Text>
                    {metric.required && <Tag color="red">必填</Tag>}
                    <Typography.Text type="secondary">
                      {metric.inputComponent === 'input-number' && metric.minScore != null && metric.maxScore != null
                        ? `${metric.minScore} - ${metric.maxScore}`
                        : getInputComponentLabel(metric.inputComponent)}
                    </Typography.Text>
                  </Space>
                  {metric.scoringGuide && <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{metric.scoringGuide}</Typography.Paragraph>}
                  {metric.optionValues && ['radio', 'checkbox-group', 'select'].includes(metric.inputComponent || '') && (
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      可选项：{parseMetricOptions(metric.optionValues).map((item) => item.label).join('、') || '—'}
                    </Typography.Paragraph>
                  )}
                  <Space direction="vertical" size={8} style={{ width: '100%' }}>
                    {renderMetricInput(metric.id, score, metric.inputComponent, metric.optionValues)}
                    <Input
                      disabled={readOnly}
                      value={score.comment}
                      onChange={(event) => setScores((current) => ({ ...current, [metric.id!]: { ...score, comment: event.target.value } }))}
                      placeholder="备注"
                      style={{ width: 320, maxWidth: '100%' }}
                    />
                  </Space>
                </Space>
              </List.Item>
            )
          }}
        />
      </Card>

      <Card title="整体结论">
        <Form form={form} layout="vertical">
          <Space style={{ width: '100%' }} align="start">
            <Form.Item name="finalEstimate" label="最终估价" style={{ flex: 1 }}>
              <Input disabled={readOnly} />
            </Form.Item>
            <Form.Item name="finalEstimateCurrency" label="币种" style={{ width: 160 }}>
              <Input disabled={readOnly} placeholder="例如 RMB" />
            </Form.Item>
          </Space>
          <Form.Item name="comment" label="整体评语">
            <Input.TextArea rows={4} disabled={readOnly} />
          </Form.Item>
        </Form>
      </Card>

      <Space>
        <Button onClick={() => navigate(`/evaluations/${data.evaluationId}`)}>返回项目</Button>
        <Button disabled={!previousArtwork} onClick={() => previousArtwork && navigateToArtwork(previousArtwork.artworkId)}>
          上一个
        </Button>
        <Button disabled={!nextArtwork} onClick={() => nextArtwork && navigateToArtwork(nextArtwork.artworkId)}>
          下一个
        </Button>
        {!readOnly && <Button onClick={submitSave} loading={saving}>保存草稿</Button>}
        {!readOnly && <Button type="primary" onClick={submitReview} loading={saving}>提交评估</Button>}
      </Space>
    </Space>
  )
}
