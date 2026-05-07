import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, List, message, Modal, Popconfirm, Space, Table, Tag, Typography } from 'antd'
import { Link, useNavigate, useParams } from 'react-router-dom'
import * as api from '../../api'
import type { ArtworkReviewSummary, EvaluationAuditRecord, EvaluationProject } from '../../types'
import { permissions } from '../../auth/permissions'
import { useAuth } from '../../auth/AuthContext'
import { formatStoredOptionValue, getInputComponentLabel, isNumericInputComponent } from './metricInputUtils'

const statusText: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待发布',
  PUBLISHED: '已发布',
  IN_PROGRESS: '进行中',
  READY_FOR_REVIEW: '待提交审核',
  IN_REVIEW: '审核中',
  REVIEW_REJECTED: '审核驳回',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export default function EvaluationDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user, hasPermission } = useAuth()
  const [project, setProject] = useState<EvaluationProject | null>(null)
  const [auditRecords, setAuditRecords] = useState<EvaluationAuditRecord[]>([])
  const [summary, setSummary] = useState<ArtworkReviewSummary | null>(null)
  const [loadingSummary, setLoadingSummary] = useState(false)

  const load = async () => {
    if (!id) return
    try {
      const projectResult = await api.getEvaluation(Number(id))
      setProject(projectResult)
      if (hasPermission(permissions.evaluationAuditHistoryView) || hasPermission(permissions.evaluationAuditView) || hasPermission(permissions.evaluationView)) {
        api.listEvaluationAuditRecords(Number(id)).then(setAuditRecords).catch(() => {})
      }
    } catch (e: any) {
      message.error(e.message)
      navigate('/evaluations')
    }
  }

  useEffect(() => {
    load()
  }, [id])

  if (!project) {
    return null
  }

  const canEdit = hasPermission(permissions.evaluationUpdate) && ['DRAFT', 'PENDING'].includes(project.status)
  const isAssignedExpert = Boolean(user && project.experts.some((expert) => expert.expertId === user.id))
  const metricMap = new Map(project.metrics.map((metric) => [metric.id, metric]))

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>{project.name}</Typography.Title>
        <Space>
          {canEdit && <Button onClick={() => navigate(`/evaluations/${project.id}/edit`)}>编辑项目</Button>}
          {hasPermission(permissions.evaluationPublish) && ['DRAFT', 'PENDING'].includes(project.status) && (
            <Popconfirm
              title="发布后将锁定项目配置，确认发布？"
              onConfirm={async () => {
                try {
                  await api.publishEvaluation(project.id)
                  message.success('项目已发布')
                  load()
                } catch (e: any) {
                  message.error(e.message)
                }
              }}
            >
              <Button>发布</Button>
            </Popconfirm>
          )}
          {hasPermission(permissions.evaluationSubmitReview) && (
            <Button
              disabled={!['READY_FOR_REVIEW', 'REVIEW_REJECTED'].includes(project.status)}
              onClick={async () => {
                try {
                  await api.submitEvaluationReview(project.id)
                  message.success('已提交审核')
                  load()
                } catch (e: any) {
                  message.error(e.message)
                }
              }}
            >
              提交审核
            </Button>
          )}
          {hasPermission(permissions.evaluationAuditView) && (
            <Link to={`/evaluations/${project.id}/audit`}>
              <Button>进入审核</Button>
            </Link>
          )}
        </Space>
      </Space>

      <Card>
        <Descriptions bordered column={2}>
          <Descriptions.Item label="状态">{statusText[project.status] || project.status}</Descriptions.Item>
          <Descriptions.Item label="审核人">{project.auditorName || '—'}</Descriptions.Item>
          <Descriptions.Item label="专家数">{project.expertCount}</Descriptions.Item>
          <Descriptions.Item label="艺术品数">{project.artworkCount}</Descriptions.Item>
          <Descriptions.Item label="完成进度">{project.completedCount} / {project.expectedReviewCount}</Descriptions.Item>
          <Descriptions.Item label="审核结果">{project.auditResult || '—'}</Descriptions.Item>
          <Descriptions.Item label="项目说明" span={2}>{project.description || '—'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="筛选条件">
        <List
          dataSource={project.criteria}
          locale={{ emptyText: '未配置筛选条件' }}
          renderItem={(item) => (
            <List.Item>
              <Space>
                <Tag>{item.fieldLabel || item.fieldName}</Tag>
                <span>{item.operator}</span>
                <span>{item.value || '—'}{item.valueTo ? ` ~ ${item.valueTo}` : ''}</span>
              </Space>
            </List.Item>
          )}
        />
      </Card>

      <Card title="评估指标">
        <Table
          rowKey="code"
          pagination={false}
          dataSource={project.metrics}
          columns={[
            { title: '名称', dataIndex: 'name' },
            { title: '编码', dataIndex: 'code', width: 180 },
            { title: '输入控件', dataIndex: 'inputComponent', width: 120, render: (value) => getInputComponentLabel(value) },
            { title: '分值范围', width: 120, render: (_, record) => isNumericInputComponent(record.inputComponent) && record.minScore != null && record.maxScore != null ? `${record.minScore} - ${record.maxScore}` : '—' },
            { title: '必填', width: 80, render: (_, record) => record.required ? <Tag color="green">是</Tag> : '否' },
          ]}
        />
      </Card>

      <Card title="项目专家">
        <Table
          rowKey="id"
          pagination={false}
          dataSource={project.experts}
          columns={[
            { title: '专家', dataIndex: 'expertName' },
            { title: '状态', dataIndex: 'status', width: 120 },
            { title: '完成', width: 120, render: (_, record) => `${record.completedCount} / ${record.totalCount}` },
            { title: '驳回数', dataIndex: 'rejectedCount', width: 100 },
          ]}
        />
      </Card>

      <Card title="艺术品评估">
        <Table
          rowKey="id"
          pagination={false}
          dataSource={project.artworks}
          columns={[
            { title: '标题', dataIndex: ['artwork', 'title'] },
            { title: '作者', dataIndex: ['artwork', 'artist'], width: 140, render: (v) => v || '—' },
            { title: '拍品编号', dataIndex: ['artwork', 'lotNumber'], width: 120, render: (v) => v || '—' },
            { title: '状态', dataIndex: 'status', width: 120 },
            {
              title: '操作',
              width: 240,
              render: (_, record) => (
                <Space>
                  {hasPermission(permissions.evaluationReviewOwnView) && isAssignedExpert && project.status !== 'PENDING' && project.status !== 'DRAFT' && (
                    <Link to={`/evaluations/${project.id}/artworks/${record.artworkId}/review`}>
                      <Button size="small">我的评估</Button>
                    </Link>
                  )}
                  {(hasPermission(permissions.evaluationResultView) || hasPermission(permissions.evaluationAuditView) || hasPermission(permissions.evaluationView)) && (
                    <Button
                      size="small"
                      onClick={async () => {
                        try {
                          setLoadingSummary(true)
                          const result = await api.getArtworkReviewSummary(project.id, record.artworkId)
                          setSummary(result)
                        } catch (e: any) {
                          message.error(e.message)
                        } finally {
                          setLoadingSummary(false)
                        }
                      }}
                    >
                      查看评估结果
                    </Button>
                  )}
                </Space>
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
            { title: '动作', dataIndex: 'action', width: 160, render: (v) => v || '—' },
            { title: '审核人', dataIndex: 'auditorName', width: 120, render: (v) => v || '—' },
            { title: '专家', dataIndex: 'expertName', width: 120, render: (v) => v || '—' },
            { title: '说明', dataIndex: 'comment', render: (v) => v || '—' },
          ]}
        />
      </Card>

      <Modal
        title={summary?.artwork?.title || '评估结果'}
        open={Boolean(summary)}
        onCancel={() => setSummary(null)}
        footer={null}
        width={900}
      >
        {loadingSummary ? '加载中...' : summary && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {summary.reviews.map((review) => (
              <Card key={review.id} size="small" title={`${review.expertName} · ${review.status}`}>
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
            ))}
          </Space>
        )}
      </Modal>
    </Space>
  )
}
