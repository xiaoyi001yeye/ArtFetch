import { useEffect, useState } from 'react'
import { Button, Card, message, Popconfirm, Space, Table, Tag, Typography } from 'antd'
import { EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { Link, useNavigate } from 'react-router-dom'
import * as api from '../../api'
import type { EvaluationProjectListItem } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'

const statusTag = (status: string) => {
  const colorMap: Record<string, string> = {
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
  const textMap: Record<string, string> = {
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
  return <Tag color={colorMap[status] || 'default'}>{textMap[status] || status}</Tag>
}

export default function EvaluationsPage() {
  const { hasPermission } = useAuth()
  const navigate = useNavigate()
  const [items, setItems] = useState<EvaluationProjectListItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)

  const canDeleteProject = (status: string) => ['DRAFT', 'PENDING'].includes(status)

  const load = async (p = page) => {
    setLoading(true)
    try {
      const result = await api.listEvaluations(p, 20)
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
    {
      title: '项目名称',
      dataIndex: 'name',
      render: (value, record) => <Link to={`/evaluations/${record.id}`}>{value}</Link>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (status) => statusTag(status),
    },
    {
      title: '艺术品数',
      dataIndex: 'artworkCount',
      width: 90,
    },
    {
      title: '专家数',
      dataIndex: 'expertCount',
      width: 90,
    },
    {
      title: '完成进度',
      width: 160,
      render: (_, record) => `${record.completedCount} / ${record.expectedReviewCount}`,
    },
    {
      title: '审核人',
      dataIndex: 'auditorName',
      width: 120,
      render: (v) => v || '—',
    },
    {
      title: '专家',
      dataIndex: 'experts',
      render: (experts: string[]) => (
        <Space size={4} wrap>
          {experts.map((expert) => <Tag key={expert}>{expert}</Tag>)}
        </Space>
      ),
    },
    {
      title: '操作',
      width: 320,
      render: (_, record) => (
        <Space>
          <Link to={`/evaluations/${record.id}`}>
            <Button size="small" icon={<EyeOutlined />}>详情</Button>
          </Link>
          {hasPermission(permissions.evaluationUpdate) && (
            <Button
              size="small"
              disabled={!['DRAFT', 'PENDING'].includes(record.status)}
              onClick={() => navigate(`/evaluations/${record.id}/edit`)}
            >
              编辑
            </Button>
          )}
          {hasPermission(permissions.evaluationPublish) && (
            <Popconfirm
              title="发布后将锁定项目配置，确认发布？"
              disabled={!['DRAFT', 'PENDING'].includes(record.status)}
              onConfirm={async () => {
                try {
                  await api.publishEvaluation(record.id)
                  message.success('项目已发布')
                  load()
                } catch (e: any) {
                  message.error(e.message)
                }
              }}
            >
              <Button size="small" disabled={!['DRAFT', 'PENDING'].includes(record.status)}>发布</Button>
            </Popconfirm>
          )}
          {hasPermission(permissions.evaluationSubmitReview) && (
            <Button
              size="small"
              disabled={!['READY_FOR_REVIEW', 'REVIEW_REJECTED'].includes(record.status)}
              onClick={async () => {
                try {
                  await api.submitEvaluationReview(record.id)
                  message.success('已提交审核')
                  load()
                } catch (e: any) {
                  message.error(e.message)
                }
              }}
            >
              提交审核
            </Button>
          )}
          {hasPermission(permissions.evaluationAuditView) && (
            <Link to={`/evaluations/${record.id}/audit`}>
              <Button size="small">审核</Button>
            </Link>
          )}
          {hasPermission(permissions.evaluationDelete) && (
            <Popconfirm
              title="确认删除该评估项目？"
              disabled={!canDeleteProject(record.status)}
              onConfirm={async () => {
                await api.deleteEvaluation(record.id)
                message.success('项目已删除')
                load()
              }}
            >
              <Button size="small" danger disabled={!canDeleteProject(record.status)}>删除</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>评估项目</Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => load(0)}>刷新</Button>
          {hasPermission(permissions.evaluationCreate) && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/evaluations/new')}>
              新建评估项目
            </Button>
          )}
        </Space>
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
            showTotal: (t) => `共 ${t} 个项目`,
          }}
        />
      </Card>
    </Space>
  )
}
