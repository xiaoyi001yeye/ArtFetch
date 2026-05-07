import { useEffect, useState } from 'react'
import { Button, Card, message, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'
import { ReloadOutlined } from '@ant-design/icons'
import * as api from '../../api'
import type { EvaluationProjectListItem } from '../../types'

const statusTag = (status: string) => {
  const colorMap: Record<string, string> = {
    PENDING: 'blue',
    PUBLISHED: 'cyan',
    IN_PROGRESS: 'processing',
    READY_FOR_REVIEW: 'gold',
    IN_REVIEW: 'purple',
    REVIEW_REJECTED: 'red',
    COMPLETED: 'green',
  }
  const textMap: Record<string, string> = {
    PENDING: '待发布',
    PUBLISHED: '已发布',
    IN_PROGRESS: '进行中',
    READY_FOR_REVIEW: '待提交审核',
    IN_REVIEW: '审核中',
    REVIEW_REJECTED: '审核驳回',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  }
  return <Tag color={colorMap[status] || 'default'}>{textMap[status] || status}</Tag>
}

export default function MyEvaluationsPage() {
  const [items, setItems] = useState<EvaluationProjectListItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)

  const load = async (p = page) => {
    setLoading(true)
    try {
      const result = await api.listAssignedEvaluations(p, 20)
      setItems(result.items)
      setTotal(result.total)
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

  const columns: ColumnsType<EvaluationProjectListItem> = [
    { title: '项目名称', dataIndex: 'name', render: (value, record) => <Link to={`/evaluations/${record.id}`}>{value}</Link> },
    { title: '状态', dataIndex: 'status', width: 120, render: (status) => statusTag(status) },
    { title: '艺术品数', dataIndex: 'artworkCount', width: 100 },
    { title: '项目进度', width: 160, render: (_, record) => `${record.completedCount} / ${record.expectedReviewCount}` },
    { title: '审核人', dataIndex: 'auditorName', width: 120, render: (v) => v || '—' },
    {
      title: '操作',
      width: 120,
      render: (_, record) => (
        <Link to={`/evaluations/${record.id}`}>
          <Button size="small">进入项目</Button>
        </Link>
      ),
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>我的评估</Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={() => load(0)}>刷新</Button>
      </Space>
      <Card>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={items}
          columns={columns}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total,
            onChange: (p) => load(p - 1),
          }}
        />
      </Card>
    </Space>
  )
}
