import { useEffect, useState } from 'react'
import {
  Badge,
  Breadcrumb,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Image,
  message,
  Row,
  Skeleton,
  Space,
  Typography,
} from 'antd'
import { ArrowLeftOutlined, LinkOutlined } from '@ant-design/icons'
import { Link, useNavigate, useParams } from 'react-router-dom'
import * as api from '../api'
import type { Artwork } from '../types'

export default function ArtworkDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [artwork, setArtwork] = useState<Artwork | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    api.getArtwork(Number(id))
      .then(setArtwork)
      .catch((e) => { message.error(e.message); navigate('/artworks') })
      .finally(() => setLoading(false))
  }, [id])

  if (loading) {
    return (
      <Card>
        <Skeleton active avatar={{ size: 200 }} paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  if (!artwork) return null

  return (
    <div>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <Link to="/artworks">艺术品数据</Link> },
          { title: artwork.title },
        ]}
      />

      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)} />
            <Typography.Title level={4} style={{ margin: 0 }}>{artwork.title}</Typography.Title>
          </Space>
        }
        extra={
          artwork.sourceUrl && (
            <Button icon={<LinkOutlined />} href={artwork.sourceUrl} target="_blank">
              查看原始数据
            </Button>
          )
        }
      >
        <Row gutter={[24, 24]}>
          {/* 图片 */}
          <Col xs={24} md={8}>
            {artwork.imageUrl ? (
              <Image
                src={artwork.imageUrl}
                style={{ width: '100%', maxHeight: 400, objectFit: 'contain', borderRadius: 8 }}
                fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
              />
            ) : (
              <div style={{
                width: '100%', height: 300, background: '#f0f0f0', borderRadius: 8,
                display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#aaa',
              }}>
                <Typography.Text type="secondary">暂无图片</Typography.Text>
              </div>
            )}
          </Col>

          {/* 详情 */}
          <Col xs={24} md={16}>
            {/* 拍品基本信息 */}
            <Descriptions bordered column={1} size="middle">
              <Descriptions.Item label="拍品名称">
                <Typography.Text strong>{artwork.title}</Typography.Text>
              </Descriptions.Item>
              {artwork.lotNumber && (
                <Descriptions.Item label="编号">
                  {artwork.lotNumber}
                </Descriptions.Item>
              )}
              <Descriptions.Item label="作者">
                {artwork.artist || <span style={{ color: '#aaa' }}>未知</span>}
              </Descriptions.Item>
              <Descriptions.Item label="材质">
                {artwork.medium || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="形制">
                {artwork.format || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="尺寸">
                {artwork.dimensions || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="估价">
                {artwork.valuation || '—'}
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left" style={{ marginTop: 16, marginBottom: 8 }}>拍卖信息</Divider>

            <Descriptions bordered column={1} size="middle">
              <Descriptions.Item label="拍卖公司">
                {artwork.auctionHouse || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="拍卖会">
                {artwork.auctionName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="拍卖专场">
                {artwork.auctionSession || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="拍卖日期">
                {artwork.auctionDate || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="拍卖地点">
                {artwork.auctionLocation || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="预展时间">
                {artwork.previewTime || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="预展地点">
                {artwork.previewLocation || '—'}
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left" style={{ marginTop: 16, marginBottom: 8 }}>系统信息</Divider>

            <Descriptions bordered column={1} size="middle">
              <Descriptions.Item label="来源任务">
                <Badge color="blue" text={
                  <Link to={`/artworks?taskId=${artwork.taskId}`}>{artwork.taskName}</Link>
                } />
              </Descriptions.Item>
              <Descriptions.Item label="抓取时间">
                {artwork.createdAt?.replace('T', ' ').slice(0, 19)}
              </Descriptions.Item>
            </Descriptions>

            {artwork.description && (
              <Card title="描述" size="small" style={{ marginTop: 16 }}>
                <Typography.Paragraph
                  ellipsis={{ rows: 6, expandable: true, symbol: '展开' }}
                  style={{ margin: 0 }}
                >
                  {artwork.description}
                </Typography.Paragraph>
              </Card>
            )}
          </Col>
        </Row>
      </Card>
    </div>
  )
}
