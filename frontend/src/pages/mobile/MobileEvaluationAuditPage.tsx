import { useEffect, useState } from 'react'
import { Button, Empty, Input, message, Modal, Skeleton, Tag, Typography } from 'antd'
import { ArrowLeftOutlined, CheckOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import * as api from '../../api'
import type { ArtworkReviewSummary, EvaluationProject } from '../../types'
import MobileEvaluationReviewSummary from './MobileEvaluationReviewSummary'
import { mobileEvaluationStatusTag } from './mobileEvaluationUi'

export default function MobileEvaluationAuditPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const projectId = Number(id)
  const [project, setProject] = useState<EvaluationProject>()
  const [summary, setSummary] = useState<ArtworkReviewSummary>()
  const [comment, setComment] = useState('')
  const [rejecting, setRejecting] = useState<{ reviewId: number; expertName: string }>()
  const [rejectReason, setRejectReason] = useState('')
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const result = await api.getEvaluation(projectId)
      setProject(result)
      setComment(result.auditComment || '')
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

  const openSummary = async (artworkId: number) => {
    setActionLoading(true)
    try {
      setSummary(await api.getArtworkReviewSummary(projectId, artworkId))
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setActionLoading(false)
    }
  }

  const approve = () => {
    if (!project) return
    Modal.confirm({
      title: '确认审核通过？',
      content: '通过后项目将进入已完成状态。',
      okText: '审核通过',
      cancelText: '取消',
      onOk: async () => {
        setActionLoading(true)
        try {
          await api.approveEvaluation(project.id, comment.trim() || undefined)
          message.success('审核已通过')
          await load()
        } catch (e: any) {
          message.error(e.message)
        } finally {
          setActionLoading(false)
        }
      },
    })
  }

  const reject = async () => {
    if (!project || !rejecting || !rejectReason.trim()) {
      message.warning('请输入驳回原因')
      return
    }
    setActionLoading(true)
    try {
      await api.rejectEvaluationReview(project.id, rejecting.reviewId, rejectReason.trim())
      message.success('已驳回该条专家评估')
      setRejecting(undefined)
      setRejectReason('')
      setSummary(undefined)
      await load()
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setActionLoading(false)
    }
  }

  if (loading || !project) {
    return (
      <MobileDataLayout title="项目审核" hideNav>
        <Skeleton active />
      </MobileDataLayout>
    )
  }

  const canApprove = hasPermission(permissions.evaluationAuditApprove) && project.status === 'IN_REVIEW'
  const canReject = hasPermission(permissions.evaluationAuditRejectReview) && project.status === 'IN_REVIEW'

  return (
    <MobileDataLayout title="项目审核" hideNav>
      <div className="mobile-detail-topbar">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/m/evaluations/${project.id}`)}>返回详情</Button>
        {mobileEvaluationStatusTag(project.status)}
      </div>

      <section className="mobile-detail-section" style={{ marginTop: 0 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>{project.name}</Typography.Title>
        <div className="mobile-dataset-meta">审核人：{project.auditorName || '—'}</div>
        <div className="mobile-dataset-meta">完成进度：{project.completedCount}/{project.expectedReviewCount}</div>
        <Typography.Text strong>审核意见</Typography.Text>
        <Input.TextArea
          rows={3}
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          placeholder="审核通过时可填写整体意见"
          disabled={!canApprove}
          style={{ marginTop: 8 }}
        />
        {canApprove && (
          <Button block type="primary" icon={<CheckOutlined />} loading={actionLoading} onClick={approve} style={{ marginTop: 10 }}>
            审核通过
          </Button>
        )}
      </section>

      <Typography.Title level={5} style={{ margin: '16px 0 10px' }}>逐件审核</Typography.Title>
      {project.artworks.length === 0 ? <Empty description="暂无待审作品" /> : (
        <div className="mobile-data-stack">
          {project.artworks.map((item) => (
            <section className="mobile-evaluation-result-card" key={item.id}>
              <div className="mobile-dataset-card-head">
                <Typography.Text strong className="mobile-dataset-title">{item.artwork.title}</Typography.Text>
                <Tag>{item.status}</Tag>
              </div>
              <div className="mobile-dataset-meta">{item.artwork.artist || '未知作者'}{item.artwork.lotNumber ? ` · ${item.artwork.lotNumber}` : ''}</div>
              <Button block loading={actionLoading} onClick={() => openSummary(item.artworkId)}>查看专家评估</Button>
            </section>
          ))}
        </div>
      )}

      <Modal
        title={summary?.artwork.title || '专家评估'}
        open={Boolean(summary)}
        onCancel={() => setSummary(undefined)}
        footer={null}
        width="calc(100vw - 24px)"
      >
        {summary && (
          <MobileEvaluationReviewSummary
            summary={summary}
            metrics={project.metrics}
            canReject={canReject}
            onReject={(reviewId, expertName) => {
              setRejectReason('')
              setRejecting({ reviewId, expertName })
            }}
          />
        )}
      </Modal>

      <Modal
        title={`驳回 ${rejecting?.expertName || ''} 的评估`}
        open={Boolean(rejecting)}
        onCancel={() => setRejecting(undefined)}
        onOk={reject}
        okText="确认驳回"
        okButtonProps={{ danger: true, loading: actionLoading, disabled: !rejectReason.trim() }}
        cancelText="取消"
      >
        <Typography.Paragraph>驳回将精确作用于当前专家对当前作品的评估，原因会展示给专家。</Typography.Paragraph>
        <Input.TextArea rows={4} value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} placeholder="请输入驳回原因（必填）" />
      </Modal>
    </MobileDataLayout>
  )
}
