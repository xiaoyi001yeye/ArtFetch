import { useEffect, useState } from 'react'
import { Button, Descriptions, Empty, List, message, Modal, Skeleton, Space, Tabs, Tag, Typography } from 'antd'
import { ArrowLeftOutlined, DeleteOutlined, EditOutlined, SafetyCertificateOutlined, SendOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import * as api from '../../api'
import type { ArtworkReviewSummary, EvaluationAuditRecord, EvaluationProject } from '../../types'
import MobileEvaluationReviewSummary from './MobileEvaluationReviewSummary'
import {
  editableEvaluationStatuses,
  formatMobileDateTime,
  mobileEvaluationStatusTag,
  submitReviewStatuses,
} from './mobileEvaluationUi'

export default function MobileEvaluationDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const projectId = Number(id)
  const [project, setProject] = useState<EvaluationProject>()
  const [auditRecords, setAuditRecords] = useState<EvaluationAuditRecord[]>([])
  const [summary, setSummary] = useState<ArtworkReviewSummary>()
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)
  const [summaryLoading, setSummaryLoading] = useState(false)

  const load = async () => {
    if (!Number.isFinite(projectId)) return
    setLoading(true)
    try {
      const [projectResult, auditResult] = await Promise.all([
        api.getEvaluation(projectId),
        api.listEvaluationAuditRecords(projectId).catch(() => []),
      ])
      setProject(projectResult)
      setAuditRecords(auditResult)
    } catch (e: any) {
      message.error(e.message)
      navigate('/m/evaluations', { replace: true })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [projectId])

  const runAction = async (action: () => Promise<unknown>, success: string) => {
    setActionLoading(true)
    try {
      await action()
      message.success(success)
      await load()
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setActionLoading(false)
    }
  }

  const confirmPublish = () => {
    if (!project) return
    Modal.confirm({
      title: '确认发布项目？',
      content: `发布后配置将锁定。当前包含 ${project.artworkCount} 件作品、${project.expertCount} 位专家、${project.metrics.length} 个指标。`,
      okText: '确认发布',
      cancelText: '取消',
      onOk: () => runAction(() => api.publishEvaluation(project.id), '项目已发布'),
    })
  }

  const confirmDelete = () => {
    if (!project) return
    Modal.confirm({
      title: '确认删除项目？',
      content: `“${project.name}”删除后将不再出现在项目列表中。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setActionLoading(true)
        try {
          await api.deleteEvaluation(project.id)
          message.success('项目已删除')
          navigate('/m/evaluations', { replace: true })
        } catch (e: any) {
          message.error(e.message)
        } finally {
          setActionLoading(false)
        }
      },
    })
  }

  const openSummary = async (artworkId: number) => {
    setSummaryLoading(true)
    try {
      setSummary(await api.getArtworkReviewSummary(projectId, artworkId))
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSummaryLoading(false)
    }
  }

  if (loading || !project) {
    return (
      <MobileDataLayout title="项目详情" hideNav>
        <Skeleton active />
      </MobileDataLayout>
    )
  }

  const canEdit = hasPermission(permissions.evaluationUpdate) && editableEvaluationStatuses.includes(project.status)
  const canPublish = hasPermission(permissions.evaluationPublish) && editableEvaluationStatuses.includes(project.status)
  const canDelete = hasPermission(permissions.evaluationDelete) && editableEvaluationStatuses.includes(project.status)
  const canSubmit = hasPermission(permissions.evaluationSubmitReview) && submitReviewStatuses.includes(project.status)
  const canAudit = hasPermission(permissions.evaluationAuditView)
  const canViewResults = hasPermission(permissions.evaluationView)
    || hasPermission(permissions.evaluationResultView)
    || hasPermission(permissions.evaluationAuditView)

  const tabItems = [
    {
      key: 'overview',
      label: '概览',
      children: (
        <div className="mobile-data-stack">
          <section className="mobile-detail-section" style={{ marginTop: 0 }}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="状态">{mobileEvaluationStatusTag(project.status)}</Descriptions.Item>
              <Descriptions.Item label="审核人">{project.auditorName || '—'}</Descriptions.Item>
              <Descriptions.Item label="完成进度">{project.completedCount} / {project.expectedReviewCount}</Descriptions.Item>
              <Descriptions.Item label="驳回数">{project.rejectedReviewCount}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatMobileDateTime(project.updatedAt)}</Descriptions.Item>
              <Descriptions.Item label="审核结果">{project.auditResult || '—'}</Descriptions.Item>
              <Descriptions.Item label="审核意见">{project.auditComment || '—'}</Descriptions.Item>
            </Descriptions>
            <Typography.Paragraph style={{ margin: '10px 0 0', whiteSpace: 'pre-wrap' }}>
              {project.description || '暂无项目说明'}
            </Typography.Paragraph>
          </section>
          <section className="mobile-detail-section" style={{ marginTop: 0 }}>
            <Typography.Title level={5}>筛选条件</Typography.Title>
            <List
              dataSource={project.criteria}
              locale={{ emptyText: '未配置筛选条件' }}
              renderItem={(item) => (
                <List.Item>
                  <Space wrap>
                    <Tag>{item.fieldLabel || item.fieldName}</Tag>
                    <span>{item.operator}</span>
                    <span>{item.value || '—'}{item.valueTo ? ` ~ ${item.valueTo}` : ''}</span>
                  </Space>
                </List.Item>
              )}
            />
          </section>
        </div>
      ),
    },
    {
      key: 'artworks',
      label: `作品 ${project.artworkCount}`,
      children: project.artworks.length === 0 ? <Empty description="暂无作品" /> : (
        <div className="mobile-data-stack">
          {project.artworks.map((item) => (
            <section className="mobile-evaluation-result-card" key={item.id}>
              <div className="mobile-dataset-card-head">
                <Typography.Text strong className="mobile-dataset-title">{item.artwork.title}</Typography.Text>
                <Tag>{item.status}</Tag>
              </div>
              <div className="mobile-dataset-meta">{item.artwork.artist || '未知作者'}{item.artwork.lotNumber ? ` · ${item.artwork.lotNumber}` : ''}</div>
              {canViewResults && (
                <Button block loading={summaryLoading} onClick={() => openSummary(item.artworkId)}>
                  查看专家评估
                </Button>
              )}
            </section>
          ))}
        </div>
      ),
    },
    {
      key: 'experts',
      label: `专家 ${project.expertCount}`,
      children: project.experts.length === 0 ? <Empty description="暂无专家" /> : (
        <div className="mobile-data-stack">
          {project.experts.map((expert) => (
            <section className="mobile-evaluation-result-card" key={expert.id}>
              <div className="mobile-dataset-card-head">
                <Typography.Text strong>{expert.expertName}</Typography.Text>
                <Tag>{expert.status}</Tag>
              </div>
              <div className="mobile-dataset-stats">
                <span>完成 {expert.completedCount}/{expert.totalCount}</span>
                <span>驳回 {expert.rejectedCount}</span>
              </div>
            </section>
          ))}
        </div>
      ),
    },
    {
      key: 'metrics',
      label: `指标 ${project.metrics.length}`,
      children: project.metrics.length === 0 ? <Empty description="暂无指标" /> : (
        <div className="mobile-data-stack">
          {project.metrics.map((metric) => (
            <section className="mobile-evaluation-result-card" key={metric.id || metric.code}>
              <div className="mobile-dataset-card-head">
                <Typography.Text strong>{metric.name}</Typography.Text>
                {metric.required && <Tag color="blue">必填</Tag>}
              </div>
              <div className="mobile-dataset-meta">{metric.code}</div>
              {metric.scoringGuide && <Typography.Paragraph style={{ margin: '8px 0 0' }}>{metric.scoringGuide}</Typography.Paragraph>}
            </section>
          ))}
        </div>
      ),
    },
    {
      key: 'audit',
      label: `审核记录 ${auditRecords.length}`,
      children: auditRecords.length === 0 ? <Empty description="暂无审核记录" /> : (
        <div className="mobile-data-stack">
          {auditRecords.map((record) => (
            <section className="mobile-evaluation-result-card" key={record.id}>
              <div className="mobile-dataset-card-head">
                <Typography.Text strong>{record.action || record.result || '审核操作'}</Typography.Text>
                <span className="mobile-dataset-meta">{formatMobileDateTime(record.createdAt)}</span>
              </div>
              <div className="mobile-dataset-meta">审核人：{record.auditorName || '—'}</div>
              {record.expertName && <div className="mobile-dataset-meta">专家：{record.expertName}</div>}
              <Typography.Paragraph style={{ margin: '8px 0 0' }}>{record.comment || '无审核意见'}</Typography.Paragraph>
            </section>
          ))}
        </div>
      ),
    },
  ]

  return (
    <MobileDataLayout title="项目详情" hideNav>
      <div className="mobile-detail-topbar">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/m/evaluations')}>返回</Button>
        {mobileEvaluationStatusTag(project.status)}
      </div>

      <section className="mobile-detail-section" style={{ marginTop: 0 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>{project.name}</Typography.Title>
        <div className="mobile-dataset-stats">
          <span>作品 {project.artworkCount}</span>
          <span>专家 {project.expertCount}</span>
          <span>指标 {project.metrics.length}</span>
          <span>完成 {project.completedCount}/{project.expectedReviewCount}</span>
        </div>
        <div className="mobile-evaluation-project-actions">
          {canEdit && <Button icon={<EditOutlined />} onClick={() => navigate(`/m/evaluations/${project.id}/edit`)}>编辑</Button>}
          {canPublish && <Button type="primary" icon={<SendOutlined />} loading={actionLoading} onClick={confirmPublish}>发布</Button>}
          {canSubmit && <Button type="primary" icon={<SendOutlined />} loading={actionLoading} onClick={() => runAction(() => api.submitEvaluationReview(project.id), '已提交审核')}>提交审核</Button>}
          {canAudit && <Button icon={<SafetyCertificateOutlined />} onClick={() => navigate(`/m/evaluations/${project.id}/audit`)}>进入审核</Button>}
          {canDelete && <Button danger icon={<DeleteOutlined />} loading={actionLoading} onClick={confirmDelete}>删除</Button>}
        </div>
      </section>

      <Tabs className="mobile-evaluation-tabs" items={tabItems} />

      <Modal
        title={summary?.artwork.title || '专家评估'}
        open={Boolean(summary)}
        onCancel={() => setSummary(undefined)}
        footer={null}
        width="calc(100vw - 24px)"
      >
        {summary && <MobileEvaluationReviewSummary summary={summary} metrics={project.metrics} />}
      </Modal>
    </MobileDataLayout>
  )
}
