import { useEffect, useState } from 'react'
import { Button, Card, Input, List, message, Modal, Space, Table, Tag, Typography } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import * as api from '../../api'
import type { ArtworkReviewSummary, EvaluationAuditRecord, EvaluationProject } from '../../types'
import { formatStoredOptionValue } from './metricInputUtils'

export default function EvaluationAuditPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [project, setProject] = useState<EvaluationProject | null>(null)
  const [auditRecords, setAuditRecords] = useState<EvaluationAuditRecord[]>([])
  const [summary, setSummary] = useState<ArtworkReviewSummary | null>(null)
  const [comment, setComment] = useState('')
  const [loadingSummary, setLoadingSummary] = useState(false)

  const load = async () => {
    if (!id) return
    try {
      const [projectResult, auditResult] = await Promise.all([
        api.getEvaluation(Number(id)),
        api.listEvaluationAuditRecords(Number(id)),
      ])
      setProject(projectResult)
      setAuditRecords(auditResult)
      setComment(projectResult.auditComment || '')
    } catch (e: any) {
      message.error(e.message)
      navigate('/evaluations')
    }
  }

  useEffect(() => {
    load()
  }, [id])

  if (!project) return null

  const metricMap = new Map(project.metrics.map((metric) => [metric.id, metric]))

  const openSummary = async (artworkId: number) => {
    try {
      setLoadingSummary(true)
      const result = await api.getArtworkReviewSummary(project.id, artworkId)
      setSummary(result)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoadingSummary(false)
    }
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>评估审核</Typography.Title>
        <Space>
          <Button onClick={() => navigate(`/evaluations/${project.id}`)}>返回详情</Button>
          <Button
            type="primary"
            disabled={project.status !== 'IN_REVIEW'}
            onClick={async () => {
              try {
                await api.approveEvaluation(project.id, comment)
                message.success('审核已通过')
                load()
              } catch (e: any) {
                message.error(e.message)
              }
            }}
          >
            审核通过
          </Button>
        </Space>
      </Space>

      <Card title={project.name}>
        <Typography.Paragraph>状态：<Tag>{project.status}</Tag></Typography.Paragraph>
        <Typography.Paragraph>审核人：{project.auditorName || '—'}</Typography.Paragraph>
        <Typography.Paragraph>已完成：{project.completedCount} / {project.expectedReviewCount}</Typography.Paragraph>
        <Input.TextArea rows={3} value={comment} onChange={(event) => setComment(event.target.value)} placeholder="审核意见" />
      </Card>

      <Card title="待审艺术品">
        <Table
          rowKey="id"
          pagination={false}
          dataSource={project.artworks}
          columns={[
            { title: '标题', dataIndex: ['artwork', 'title'] },
            { title: '作者', dataIndex: ['artwork', 'artist'], width: 120, render: (v) => v || '—' },
            { title: '状态', dataIndex: 'status', width: 120 },
            {
              title: '操作',
              width: 120,
              render: (_, record) => (
                <Button size="small" onClick={() => openSummary(record.artworkId)}>查看评估</Button>
              ),
            },
          ]}
        />
      </Card>

      <Card title="审核历史">
        <Table
          rowKey="id"
          pagination={false}
          dataSource={auditRecords}
          columns={[
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            { title: '动作', dataIndex: 'action', width: 180, render: (v) => v || '—' },
            { title: '审核人', dataIndex: 'auditorName', width: 120, render: (v) => v || '—' },
            { title: '意见', dataIndex: 'comment', render: (v) => v || '—' },
          ]}
        />
      </Card>

      <Modal
        title={summary?.artwork?.title || '专家评估'}
        open={Boolean(summary)}
        onCancel={() => setSummary(null)}
        footer={null}
        width={960}
      >
        {loadingSummary ? '加载中...' : summary && (
          <List
            dataSource={summary.reviews}
            renderItem={(review) => (
              <List.Item key={review.id}>
                <Card
                  title={`${review.expertName} · ${review.status}`}
                  style={{ width: '100%' }}
                  extra={project.status === 'IN_REVIEW' && (
                    <Button
                      danger
                      size="small"
                      onClick={async () => {
                        const reason = window.prompt(`请输入驳回 ${review.expertName} 的原因`)
                        if (!reason) return
                        try {
                          await api.rejectEvaluationReview(project.id, review.id, reason)
                          message.success('已驳回该条专家评估')
                          setSummary(null)
                          load()
                        } catch (e: any) {
                          message.error(e.message)
                        }
                      }}
                    >
                      驳回该条评估
                    </Button>
                  )}
                >
                  <Typography.Paragraph>最终估价：{review.finalEstimate || '—'} {review.finalEstimateCurrency || ''}</Typography.Paragraph>
                  <Typography.Paragraph>整体评语：{review.comment || '—'}</Typography.Paragraph>
                  <Table
                    rowKey={(record) => `${review.id}-${record.projectMetricId}`}
                    pagination={false}
                    dataSource={review.scores}
                    columns={[
                      {
                        title: '指标',
                        dataIndex: 'projectMetricId',
                        width: 220,
                        render: (value) => metricMap.get(value)?.name || `#${value}`,
                      },
                      { title: '分值', dataIndex: 'score', width: 100, render: (v) => v ?? '—' },
                      {
                        title: '选项',
                        dataIndex: 'optionValue',
                        width: 180,
                        render: (value, record) => formatStoredOptionValue(
                          metricMap.get(record.projectMetricId)?.inputComponent,
                          value,
                          metricMap.get(record.projectMetricId)?.optionValues,
                        ),
                      },
                      { title: '文本', dataIndex: 'textValue', render: (v) => v || '—' },
                      { title: '备注', dataIndex: 'comment', render: (v) => v || '—' },
                    ]}
                  />
                </Card>
              </List.Item>
            )}
          />
        )}
      </Modal>
    </Space>
  )
}
