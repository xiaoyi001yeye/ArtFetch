import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, message, Progress, Segmented, Space, Spin, Typography } from 'antd'
import { ArrowLeftOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import * as api from '../../api'
import type { ExpertMobileArtworkListItem, ExpertMobileProject } from '../../types'
import { ProtectedPreviewImage } from '../../components/expert/ProtectedImage'
import { projectStatusTag, reviewActionText, reviewStatusTag } from './expertUi'

export default function ExpertProjectDetailPage() {
  const navigate = useNavigate()
  const { projectId } = useParams()
  const [data, setData] = useState<ExpertMobileProject>()
  const [filter, setFilter] = useState('all')
  const [loading, setLoading] = useState(false)

  const load = async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setData(await api.getExpertMobileProject(Number(projectId)))
    } catch (e: any) {
      message.error(e.message)
      navigate('/expert/projects', { replace: true })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [projectId])

  const artworks = useMemo(() => (data?.artworks || []).filter((item) => {
    if (filter === 'pending') return item.reviewStatus === 'NOT_STARTED'
    if (filter === 'draft') return item.reviewStatus === 'DRAFT'
    if (filter === 'submitted') return item.reviewStatus === 'SUBMITTED' || item.reviewStatus === 'RESUBMITTED'
    if (filter === 'rejected') return item.reviewStatus === 'REVIEW_REJECTED'
    return true
  }), [data, filter])

  const openReview = (artwork: ExpertMobileArtworkListItem) => {
    navigate(`/expert/projects/${data!.evaluationId}/artworks/${artwork.artworkId}/review`)
  }

  if (loading && !data) return <Spin />
  if (!data) return null

  const percent = data.totalCount ? Math.round((data.submittedCount / data.totalCount) * 100) : 0

  return (
    <div className="expert-mobile-stack">
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/expert/projects')} style={{ alignSelf: 'flex-start', paddingInline: 0 }}>
        返回我的评估
      </Button>
      <Card className="expert-mobile-card">
        <div className="expert-mobile-stack" style={{ gap: 10 }}>
          <div className="expert-mobile-title-row">
            <Typography.Title level={4} style={{ margin: 0 }}>{data.name}</Typography.Title>
            {projectStatusTag(data.evaluationStatus)}
          </div>
          {data.description && <Typography.Text className="expert-mobile-muted">{data.description}</Typography.Text>}
          <Progress percent={percent} />
          <Typography.Text>已完成 {data.submittedCount} / {data.totalCount}</Typography.Text>
          {data.rejectedCount > 0 && <Alert type="warning" showIcon message={`有 ${data.rejectedCount} 件评估被驳回，请优先修改`} />}
          {data.nextArtworkId && (
            <Button type="primary" size="large" onClick={() => navigate(`/expert/projects/${data.evaluationId}/artworks/${data.nextArtworkId}/review`)}>
              {data.rejectedCount > 0 ? '处理驳回' : '继续评估'}
            </Button>
          )}
        </div>
      </Card>
      <Segmented
        block
        value={filter}
        onChange={(value) => setFilter(String(value))}
        options={[
          { label: '全部', value: 'all' },
          { label: '待评估', value: 'pending' },
          { label: '草稿', value: 'draft' },
          { label: '已提交', value: 'submitted' },
          { label: '已驳回', value: 'rejected' },
        ]}
      />
      {artworks.length === 0 && <Empty description="当前筛选下暂无作品" />}
      {artworks.map((artwork) => (
        <Card key={artwork.artworkId} className="expert-mobile-card" onClick={() => openReview(artwork)}>
          <div className="expert-mobile-card-row">
            <div className="expert-mobile-preview">
              <ProtectedPreviewImage
                url={artwork.previewImageAvailable ? api.expertPreviewImageUrl(data.evaluationId, artwork.artworkId) : undefined}
                alt={artwork.title}
              />
            </div>
            <div className="expert-mobile-stack" style={{ minWidth: 0, flex: 1, gap: 6 }}>
              <Typography.Text strong ellipsis>{artwork.title}</Typography.Text>
              <Typography.Text className="expert-mobile-muted">{artwork.artist || '—'}{artwork.lotNumber ? ` · ${artwork.lotNumber}` : ''}</Typography.Text>
              <Space wrap>{reviewStatusTag(artwork.reviewStatus)}</Space>
              {artwork.rejectedReason && <Typography.Text type="danger" className="expert-mobile-description">{artwork.rejectedReason}</Typography.Text>}
              <Typography.Text style={{ color: '#4f46e5' }}>{reviewActionText(artwork.reviewStatus)} <RightOutlined /></Typography.Text>
            </div>
          </div>
        </Card>
      ))}
    </div>
  )
}
