import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Image,
  Input,
  message,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import {
  DownloadOutlined,
  EyeOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { Link, useSearchParams } from 'react-router-dom'
import * as api from '../api'
import type { ArtworkQuery } from '../api'
import type { Artwork, Task } from '../types'

export default function ArtworksPage() {
  const [searchParams] = useSearchParams()
  const initTaskId = searchParams.get('taskId') ? Number(searchParams.get('taskId')) : undefined

  const [artworks, setArtworks] = useState<Artwork[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [tasks, setTasks] = useState<Task[]>([])
  const [query, setQuery] = useState<ArtworkQuery>({ taskId: initTaskId })
  const [form] = Form.useForm()

  useEffect(() => {
    api.listTasks(0, 100).then((r) => setTasks(r.items)).catch(() => {})
    if (initTaskId) {
      form.setFieldsValue({ taskId: initTaskId })
    }
  }, [])

  useEffect(() => {
    loadArtworks(0)
  }, [query])

  const loadArtworks = async (p: number) => {
    setLoading(true)
    try {
      const result = await api.listArtworks({ ...query, page: p, size: 20 })
      setArtworks(result.items)
      setTotal(result.total)
      setPage(p)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = (values: any) => {
    setQuery({
      taskId: values.taskId || undefined,
      keyword: values.keyword || undefined,
      artist: values.artist || undefined,
      auctionDate: values.auctionDate || undefined,
      lotNumber: values.lotNumber || undefined,
    })
  }

  const handleExport = () => {
    const url = api.exportArtworksUrl(query)
    window.open(url, '_blank')
  }

  const columns: ColumnsType<Artwork> = [
    {
      title: '图片',
      dataIndex: 'imageUrl',
      width: 80,
      render: (url) =>
        url ? (
          <Image
            src={url}
            width={60}
            height={60}
            style={{ objectFit: 'cover', borderRadius: 4 }}
            fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAACXBIWXMAAAsTAAALEwEAmpwYAAABbUlEQVR4nO2YTUoDQRCFv5kYNMaFuHDhxoN4BM/gKbx4DI/gRlyIB3DhLtiFCxcuRBAUJKCSkJ+ZJC8fVEJCpvqhq6qHXnRBQ3dVv3rf6+6pF0IIIYS4JiKyJyKHInIkIgciMvXdgpEVkS8R+RWRGxHZFJFVEVkXkTUReRGRFxH5EJE3EXkVkWcReRKRRxF5EJF7ETkTkVMROReRMxE5FZEjETkWkUMR2ReRPRHZFZEdEdkWkS0R2RSRDRFZFhEB0AJQAKgAAM5mZmYDMzMzMzOzs7Ozs7Ozs7Ozs7Oz"
          />
        ) : (
          <div style={{ width: 60, height: 60, background: '#f0f0f0', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#ccc' }}>
            无图
          </div>
        ),
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (title, record) => (
        <Link to={`/artworks/${record.id}`}>
          <Tooltip title={title}>{title}</Tooltip>
        </Link>
      ),
    },
    {
      title: '编号',
      dataIndex: 'lotNumber',
      width: 80,
      render: (v) => v || '—',
    },
    {
      title: '艺术家',
      dataIndex: 'artist',
      width: 120,
      ellipsis: true,
      render: (v) => v || <span style={{ color: '#aaa' }}>未知</span>,
    },
    {
      title: '材质',
      dataIndex: 'medium',
      width: 120,
      ellipsis: true,
      render: (v) => v ? <Tooltip title={v}>{v}</Tooltip> : '—',
    },
    {
      title: '拍卖公司',
      dataIndex: 'auctionHouse',
      width: 130,
      ellipsis: true,
      render: (v) => v ? <Tooltip title={v}>{v}</Tooltip> : '—',
    },
    {
      title: '拍卖日期',
      dataIndex: 'auctionDate',
      width: 120,
      render: (v) => v || '—',
    },
    {
      title: '估价',
      dataIndex: 'valuation',
      width: 140,
      render: (v) => v || '—',
    },
    {
      title: '来源任务',
      dataIndex: 'taskName',
      width: 140,
      render: (name, record) => (
        <Tag color="blue">
          <Link to={`/artworks?taskId=${record.taskId}`} style={{ color: 'inherit' }}>{name}</Link>
        </Tag>
      ),
    },
    {
      title: '操作',
      width: 80,
      render: (_, record) => (
        <Link to={`/artworks/${record.id}`}>
          <Button size="small" icon={<EyeOutlined />}>详情</Button>
        </Link>
      ),
    },
  ]

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Typography.Title level={4} style={{ margin: 0 }}>艺术品数据</Typography.Title>
        </Col>
        <Col>
          <Button type="primary" icon={<DownloadOutlined />} onClick={handleExport}>
            导出 Excel
          </Button>
        </Col>
      </Row>

      <Card style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" onFinish={handleSearch} initialValues={{ taskId: initTaskId }}>
          <Form.Item name="taskId" label="来源任务">
            <Select allowClear placeholder="全部任务" style={{ width: 180 }}>
              {tasks.map((t) => (
                <Select.Option key={t.id} value={t.id}>
                  {t.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="keyword" label="关键词">
            <Input allowClear placeholder="标题 / 艺术家" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="artist" label="艺术家">
            <Input allowClear placeholder="艺术家姓名" style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="lotNumber" label="编号">
            <Input allowClear placeholder="拍品编号" style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="auctionDate" label="拍卖日期">
            <Input allowClear placeholder="例如 2023" style={{ width: 120 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>检索</Button>
              <Button onClick={() => { form.resetFields(); setQuery({}) }}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={artworks}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total,
            onChange: (p) => loadArtworks(p - 1),
            showTotal: (t) => `共 ${t} 条记录`,
            showSizeChanger: false,
          }}
          scroll={{ x: 1100 }}
        />
      </Card>
    </div>
  )
}
