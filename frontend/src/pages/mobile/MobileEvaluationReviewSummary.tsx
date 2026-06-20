import { Button, Card, Empty, Space, Tag, Typography } from 'antd'
import type { ArtworkReviewSummary, MetricConfig } from '../../types'
import { formatStoredOptionValue } from '../evaluation/metricInputUtils'

export default function MobileEvaluationReviewSummary({
  summary,
  metrics,
  canReject = false,
  onReject,
}: {
  summary: ArtworkReviewSummary
  metrics: MetricConfig[]
  canReject?: boolean
  onReject?: (reviewId: number, expertName: string) => void
}) {
  const metricMap = new Map(metrics.map((metric) => [metric.id, metric]))

  if (summary.reviews.length === 0) {
    return <Empty description="暂无专家评估" />
  }

  return (
    <div className="mobile-data-stack">
      {summary.reviews.map((review) => (
        <Card
          key={review.id}
          size="small"
          title={review.expertName}
          extra={<Tag>{review.status}</Tag>}
        >
          <div className="mobile-data-stack" style={{ gap: 8 }}>
            <Typography.Text>最终估价：{review.finalEstimate || '—'} {review.finalEstimateCurrency || ''}</Typography.Text>
            <Typography.Paragraph style={{ margin: 0 }}>整体评语：{review.comment || '—'}</Typography.Paragraph>
            {review.rejectedReason && <Typography.Text type="danger">驳回原因：{review.rejectedReason}</Typography.Text>}
            {review.scores.map((score) => {
              const metric = metricMap.get(score.projectMetricId)
              const optionText = formatStoredOptionValue(metric?.inputComponent, score.optionValue, metric?.optionValues)
              return (
                <div className="mobile-evaluation-score" key={`${review.id}-${score.projectMetricId}`}>
                  <strong>{metric?.name || `指标 #${score.projectMetricId}`}</strong>
                  <Space size={[4, 4]} wrap>
                    {score.score != null && <Tag color="blue">分值 {score.score}</Tag>}
                    {optionText && optionText !== '—' && <Tag>{optionText}</Tag>}
                  </Space>
                  {score.textValue && <span>{score.textValue}</span>}
                  {score.comment && <span className="mobile-dataset-meta">备注：{score.comment}</span>}
                </div>
              )
            })}
            {canReject && onReject && (
              <Button danger block onClick={() => onReject(review.id, review.expertName)}>
                驳回该条评估
              </Button>
            )}
          </div>
        </Card>
      ))}
    </div>
  )
}
