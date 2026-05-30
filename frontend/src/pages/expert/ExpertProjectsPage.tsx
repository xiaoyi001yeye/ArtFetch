import { useEffect, useState } from 'react'
import { Button, Card, Empty, message, Segmented, Space, Typography } from 'antd'
import { ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import * as api from '../../api'
import type { ExpertMobileProjectListItem } from '../../types'
import { projectStatusTag } from './expertUi'

export default function ExpertProjectsPage() {
  const navigate = useNavigate()
  const [filter, setFilter] = useState('all')
  const [items, setItems] = useState<ExpertMobileProjectListItem[]>([])
  const [loading, setLoading] = useState(false)

  const load = async (nextFilter = filter) => {
    setLoading(true)
    try {
      const result = await api.listExpertMobileProjects({ filter: nextFilter, size: 100 })
      setItems(result.items)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(filter)
  }, [filter])

  return (
    <div className="expert-mobile-stack">
      <div className="expert-mobile-title-row">
        <Typography.Title level={3} style={{ margin: 0 }}>我的评估</Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={() => load()} loading={loading}>刷新</Button>
      </div>
      <Segmented
        block
        value={filter}
        onChange={(value) => setFilter(String(value))}
        options={[
          { label: '全部', value: 'all' },
          { label: '待处理', value: 'pending' },
          { label: '已完成', value: 'completed' },
        ]}
      />
      {!loading && items.length === 0 && <Empty description="暂无分配给你的评估项目" />}
      {items.map((item) => (
        <Card key={item.evaluationId} className="expert-mobile-card">
          <div className="expert-mobile-stack" style={{ gap: 10 }}>
            <div className="expert-mobile-title-row">
              <Typography.Title level={5} style={{ margin: 0 }}>{item.name}</Typography.Title>
              {projectStatusTag(item.evaluationStatus)}
            </div>
            {item.description && <Typography.Text className="expert-mobile-muted expert-mobile-description">{item.description}</Typography.Text>}
            <Space wrap>
              <Typography.Text>已完成 {item.submittedCount} / {item.totalCount}</Typography.Text>
              {item.rejectedCount > 0 && <Typography.Text type="danger">待修改 {item.rejectedCount} 件</Typography.Text>}
              {item.draftCount > 0 && <Typography.Text type="warning">草稿 {item.draftCount} 件</Typography.Text>}
            </Space>
            <div className="expert-mobile-title-row">
              <Typography.Text className="expert-mobile-muted">
                更新于 {dayjs(item.updatedAt).format('MM-DD HH:mm')}
              </Typography.Text>
              <Button
                type="primary"
                icon={<RightOutlined />}
                iconPosition="end"
                onClick={() => navigate(`/expert/projects/${item.evaluationId}`)}
              >
                {item.rejectedCount > 0 ? '处理驳回' : item.pendingCount > 0 ? '继续评估' : '查看'}
              </Button>
            </div>
          </div>
        </Card>
      ))}
    </div>
  )
}
