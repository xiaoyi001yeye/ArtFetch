import { useEffect, useState } from 'react'
import { Button, Empty, message, Modal, Skeleton, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, SafetyCertificateOutlined, SendOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import * as api from '../../api'
import type { EvaluationProjectListItem } from '../../types'
import { editableEvaluationStatuses, mobileEvaluationStatusTag, submitReviewStatuses } from './mobileEvaluationUi'

const PAGE_SIZE = 10

const formatDateTime = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '—'

export default function MobileEvaluationsPage() {
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const [items, setItems] = useState<EvaluationProjectListItem[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)

  const runAction = async (action: () => Promise<unknown>, success: string) => {
    try {
      await action()
      message.success(success)
      await load(page)
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const confirmPublish = (item: EvaluationProjectListItem) => {
    Modal.confirm({
      title: '确认发布项目？',
      content: `发布后配置将锁定。当前包含 ${item.artworkCount} 件作品、${item.expertCount} 位专家。`,
      okText: '确认发布',
      cancelText: '取消',
      onOk: () => runAction(() => api.publishEvaluation(item.id), '项目已发布'),
    })
  }

  const confirmDelete = (item: EvaluationProjectListItem) => {
    Modal.confirm({
      title: '确认删除项目？',
      content: `“${item.name}”删除后将不再出现在项目列表中。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => runAction(() => api.deleteEvaluation(item.id), '项目已删除'),
    })
  }

  const load = async (p = page) => {
    setLoading(true)
    try {
      const result = await api.listEvaluations(p, PAGE_SIZE)
      setItems(result.items)
      setTotal(result.total)
      setTotalPages(Math.max(1, result.totalPages || 1))
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

  return (
    <MobileDataLayout title="评估项目">
      <div className="mobile-data-toolbar">
        <div className="mobile-data-toolbar-actions">
          <Button icon={<ReloadOutlined />} onClick={() => load(0)} loading={loading}>刷新</Button>
          {hasPermission(permissions.evaluationCreate) && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/m/evaluations/new')}>新建</Button>
          )}
        </div>
      </div>

      {loading ? (
        <div className="mobile-data-stack">
          <Skeleton active />
          <Skeleton active />
        </div>
      ) : items.length === 0 ? (
        <Empty description="暂无评估项目" />
      ) : (
        <div className="mobile-data-stack">
          {items.map((item) => (
            <section className="mobile-dataset-card" key={item.id}>
              <div className="mobile-dataset-card-head">
                <Typography.Text strong className="mobile-dataset-title">{item.name}</Typography.Text>
                {mobileEvaluationStatusTag(item.status)}
              </div>
              {item.description && <div className="mobile-evaluation-description">{item.description}</div>}
              <div className="mobile-dataset-meta">审核人：{item.auditorName || '—'}</div>
              <div className="mobile-dataset-meta">更新时间：{formatDateTime(item.updatedAt || item.createdAt)}</div>
              <div className="mobile-dataset-stats">
                <span>作品 {item.artworkCount}</span>
                <span>专家 {item.expertCount}</span>
                <span>应评 {item.expectedReviewCount}</span>
                <span>完成 {item.completedCount}</span>
              </div>
              <div className="mobile-evaluation-project-actions">
                <Button icon={<EyeOutlined />} onClick={() => navigate(`/m/evaluations/${item.id}`)}>详情</Button>
                {hasPermission(permissions.evaluationUpdate) && editableEvaluationStatuses.includes(item.status) && (
                  <Button icon={<EditOutlined />} onClick={() => navigate(`/m/evaluations/${item.id}/edit`)}>编辑</Button>
                )}
                {hasPermission(permissions.evaluationPublish) && editableEvaluationStatuses.includes(item.status) && (
                  <Button type="primary" icon={<SendOutlined />} onClick={() => confirmPublish(item)}>发布</Button>
                )}
                {hasPermission(permissions.evaluationSubmitReview) && submitReviewStatuses.includes(item.status) && (
                  <Button type="primary" icon={<SendOutlined />} onClick={() => runAction(() => api.submitEvaluationReview(item.id), '已提交审核')}>提交审核</Button>
                )}
                {hasPermission(permissions.evaluationAuditView) && (
                  <Button icon={<SafetyCertificateOutlined />} onClick={() => navigate(`/m/evaluations/${item.id}/audit`)}>审核</Button>
                )}
                {hasPermission(permissions.evaluationDelete) && editableEvaluationStatuses.includes(item.status) && (
                  <Button danger icon={<DeleteOutlined />} onClick={() => confirmDelete(item)}>删除</Button>
                )}
              </div>
            </section>
          ))}
        </div>
      )}

      <div className="mobile-data-pagination">
        <Button disabled={page <= 0 || loading} onClick={() => load(page - 1)}>上一页</Button>
        <span>第 {page + 1} / {totalPages} 页</span>
        <Button disabled={page + 1 >= totalPages || loading} onClick={() => load(page + 1)}>下一页</Button>
        <div>共 {total} 个</div>
      </div>
    </MobileDataLayout>
  )
}
