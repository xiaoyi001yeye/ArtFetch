import { Tag } from 'antd'
import type { EvaluationProjectStatus, ExpertReviewStatus } from '../../types'

const projectLabels: Record<EvaluationProjectStatus, string> = {
  DRAFT: '未发布',
  PENDING: '未发布',
  PUBLISHED: '待开始',
  IN_PROGRESS: '评估中',
  READY_FOR_REVIEW: '已完成待审核',
  IN_REVIEW: '审核中',
  REVIEW_REJECTED: '有评估被驳回',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const reviewLabels: Record<ExpertReviewStatus, string> = {
  NOT_STARTED: '未开始',
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  REVIEW_REJECTED: '审核驳回',
  RESUBMITTED: '已重新提交',
}

export const projectStatusText = (status: EvaluationProjectStatus) => projectLabels[status] || status
export const reviewStatusText = (status: ExpertReviewStatus) => reviewLabels[status] || status

export const projectStatusTag = (status: EvaluationProjectStatus) => (
  <Tag color={status === 'REVIEW_REJECTED' ? 'red' : status === 'COMPLETED' ? 'green' : status === 'IN_REVIEW' ? 'purple' : 'blue'}>
    {projectStatusText(status)}
  </Tag>
)

export const reviewStatusTag = (status: ExpertReviewStatus) => (
  <Tag color={status === 'REVIEW_REJECTED' ? 'red' : status === 'SUBMITTED' || status === 'RESUBMITTED' ? 'green' : status === 'DRAFT' ? 'gold' : 'default'}>
    {reviewStatusText(status)}
  </Tag>
)

export const reviewActionText = (status: ExpertReviewStatus) => {
  if (status === 'REVIEW_REJECTED') return '修改评估'
  if (status === 'DRAFT') return '继续填写'
  if (status === 'NOT_STARTED') return '开始评估'
  return '查看内容'
}
