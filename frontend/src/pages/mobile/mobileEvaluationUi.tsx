import { Tag } from 'antd'
import type { EvaluationProjectStatus } from '../../types'

const statusLabels: Record<EvaluationProjectStatus, string> = {
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

const statusColors: Record<EvaluationProjectStatus, string> = {
  DRAFT: 'default',
  PENDING: 'blue',
  PUBLISHED: 'cyan',
  IN_PROGRESS: 'processing',
  READY_FOR_REVIEW: 'gold',
  IN_REVIEW: 'purple',
  REVIEW_REJECTED: 'red',
  COMPLETED: 'green',
  CANCELLED: 'default',
}

export const mobileEvaluationStatusText = (status: EvaluationProjectStatus) => statusLabels[status] || status

export const mobileEvaluationStatusTag = (status: EvaluationProjectStatus) => (
  <Tag color={statusColors[status] || 'default'}>{mobileEvaluationStatusText(status)}</Tag>
)

export const formatMobileDateTime = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '—'

export const editableEvaluationStatuses: EvaluationProjectStatus[] = ['DRAFT', 'PENDING']
export const submitReviewStatuses: EvaluationProjectStatus[] = ['READY_FOR_REVIEW', 'REVIEW_REJECTED']
