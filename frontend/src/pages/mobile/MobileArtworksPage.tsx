import { useEffect, useMemo, useState } from 'react'
import { Button, Drawer, Empty, Form, Input, message, Modal, Select, Skeleton, Space, Tag, Typography } from 'antd'
import { DownloadOutlined, FilterOutlined, SearchOutlined } from '@ant-design/icons'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import * as api from '../../api'
import type { Artwork } from '../../types'

const PAGE_SIZE = 20

const readParam = (params: URLSearchParams, key: string) => params.get(key)?.trim() || undefined

const buildQuery = (params: URLSearchParams): api.ArtworkQuery => {
  const page = Math.max(1, Number(params.get('page') || '1'))
  const taskId = params.get('taskId') ? Number(params.get('taskId')) : undefined
  return {
    taskId: Number.isFinite(taskId) ? taskId : undefined,
    keyword: readParam(params, 'keyword'),
    artist: readParam(params, 'artist'),
    lotNumber: readParam(params, 'lotNumber'),
    auctionDate: readParam(params, 'auctionDate'),
    hdImageSyncStatus: readParam(params, 'hdImageSyncStatus') as api.HdImageSyncStatus | undefined,
    transactionPriceStatus: readParam(params, 'transactionPriceStatus') as any,
    page: page - 1,
    size: PAGE_SIZE,
  }
}

const hasExportFilter = (query: api.ArtworkQuery) =>
  Boolean(
    query.taskId
    || query.keyword
    || query.artist
    || query.lotNumber
    || query.auctionDate
    || query.hdImageSyncStatus
    || query.transactionPriceStatus
  )

const hdStatusTag = (artwork: Artwork) => {
  if (artwork.hdImageAvailable) return <Tag color="green">高清已同步</Tag>
  if (artwork.hdImageLastError?.includes('没有观看权限')) return <Tag color="red">无权限</Tag>
  if (artwork.hdImageStatus === 'FAILED') return <Tag color="orange">高清失败</Tag>
  return <Tag>高清未同步</Tag>
}

const filterText = (query: api.ArtworkQuery) => {
  const parts: string[] = []
  if (query.taskId) parts.push('已按来源范围筛选')
  if (query.keyword) parts.push(`关键词=${query.keyword}`)
  if (query.artist) parts.push(`艺术家=${query.artist}`)
  if (query.lotNumber) parts.push(`编号=${query.lotNumber}`)
  if (query.auctionDate) parts.push(`拍卖日期=${query.auctionDate}`)
  if (query.hdImageSyncStatus) parts.push(`高清图=${hdStatusLabel(query.hdImageSyncStatus)}`)
  if (query.transactionPriceStatus) parts.push(`成交价=${transactionStatusLabel(query.transactionPriceStatus)}`)
  return parts.join(' · ')
}

const hdStatusLabel = (status: string) => ({
  SYNCED: '已同步',
  UNSYNCED: '未同步',
  NO_PERMISSION: '无权限',
  FAILED: '失败',
}[status] || status)

const transactionStatusLabel = (status: string) => ({
  HAS_PRICE: '已有成交价',
  MISSING: '待补充',
  LOGIN_REQUIRED: '需要登录',
  FAILED: '补充失败',
}[status] || status)

export default function MobileArtworksPage() {
  const { hasPermission } = useAuth()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const query = useMemo(() => buildQuery(searchParams), [searchParams])
  const [items, setItems] = useState<Artwork[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [searchValue, setSearchValue] = useState(query.keyword || '')
  const [form] = Form.useForm()

  useEffect(() => {
    setSearchValue(query.keyword || '')
    form.setFieldsValue({
      artist: query.artist,
      lotNumber: query.lotNumber,
      auctionDate: query.auctionDate,
      hdImageSyncStatus: query.hdImageSyncStatus,
      transactionPriceStatus: query.transactionPriceStatus,
    })
  }, [query.artist, query.auctionDate, query.hdImageSyncStatus, query.keyword, query.lotNumber, query.transactionPriceStatus, form])

  useEffect(() => {
    setLoading(true)
    api.listArtworks(query)
      .then((result) => {
        setItems(result.items)
        setTotal(result.total)
        setTotalPages(Math.max(1, result.totalPages || 1))
      })
      .catch((e: any) => message.error(e.message))
      .finally(() => setLoading(false))
  }, [query])

  const updateParams = (patch: Record<string, any>) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(patch).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') next.delete(key)
      else next.set(key, String(value))
    })
    setSearchParams(next)
  }

  const handleSearch = (value = searchValue) => {
    updateParams({ keyword: value.trim() || undefined, page: 1 })
  }

  const handleFilters = (values: any) => {
    updateParams({
      artist: values.artist,
      lotNumber: values.lotNumber,
      auctionDate: values.auctionDate,
      hdImageSyncStatus: values.hdImageSyncStatus,
      transactionPriceStatus: values.transactionPriceStatus,
      page: 1,
    })
    setDrawerOpen(false)
  }

  const handleResetFilters = () => {
    form.resetFields()
    updateParams({
      artist: undefined,
      lotNumber: undefined,
      auctionDate: undefined,
      hdImageSyncStatus: undefined,
      transactionPriceStatus: undefined,
      page: 1,
    })
    setDrawerOpen(false)
  }

  const handleExport = () => {
    if (!hasExportFilter(query)) {
      message.warning('请先设置筛选条件后再导出')
      return
    }
    Modal.confirm({
      title: '导出艺术品数据',
      content: '将按当前筛选条件导出 Excel。',
      okText: '确认导出',
      cancelText: '取消',
      onOk: async () => {
        try {
          await api.downloadArtworksExport(query)
          message.success('导出已开始')
        } catch (e: any) {
          message.error(e.message)
        }
      },
    })
  }

  const currentPage = Math.max(1, query.page ? query.page + 1 : 1)
  const summary = filterText(query)
  const from = `${location.pathname}${location.search}`

  return (
    <MobileDataLayout title="艺术品数据">
      <div className="mobile-data-toolbar">
        <Input.Search
          value={searchValue}
          allowClear
          enterButton="搜索"
          loading={loading}
          size="large"
          prefix={<SearchOutlined />}
          placeholder="搜索标题、艺术家或编号"
          onChange={(e) => setSearchValue(e.target.value)}
          onSearch={(value, _event, info) => {
            if (info?.source === 'clear') {
              updateParams({ keyword: undefined, page: 1 })
              return
            }
            handleSearch(value)
          }}
        />
        <div className="mobile-data-toolbar-actions">
          <Button icon={<FilterOutlined />} onClick={() => setDrawerOpen(true)}>筛选</Button>
          {hasPermission(permissions.artworkExport) && (
            <Button type="primary" icon={<DownloadOutlined />} onClick={handleExport}>导出</Button>
          )}
        </div>
        {summary && <div className="mobile-data-filter-summary">已筛选：{summary}</div>}
      </div>

      {loading ? (
        <div className="mobile-data-stack">
          <Skeleton active />
          <Skeleton active />
        </div>
      ) : items.length === 0 ? (
        <Empty description="没有符合条件的艺术品" />
      ) : (
        <div className="mobile-data-stack">
          {items.map((artwork) => (
            <Link
              className="mobile-artwork-card"
              key={artwork.id}
              to={`/m/artworks/${artwork.id}?from=${encodeURIComponent(from)}`}
            >
              <div className="mobile-artwork-thumb">
                {artwork.imageUrl ? (
                  <img
                    src={artwork.imageUrl}
                    alt={artwork.title}
                    draggable={false}
                    onContextMenu={(event) => event.preventDefault()}
                  />
                ) : <span>无图</span>}
              </div>
              <div className="mobile-artwork-info">
                <Typography.Text strong className="mobile-artwork-title">{artwork.title}</Typography.Text>
                <div className="mobile-artwork-meta">
                  {artwork.artist || '未知作者'}{artwork.lotNumber ? ` / ${artwork.lotNumber}` : ''}
                </div>
                <div className="mobile-artwork-meta">
                  {[artwork.auctionHouse, artwork.auctionDate].filter(Boolean).join(' · ') || '拍卖信息待补充'}
                </div>
                <div className="mobile-artwork-source">来源：{artwork.taskName || '未知来源'}</div>
                <div>{hdStatusTag(artwork)}</div>
              </div>
            </Link>
          ))}
        </div>
      )}

      <div className="mobile-data-pagination">
        <Button disabled={currentPage <= 1 || loading} onClick={() => updateParams({ page: currentPage - 1 })}>上一页</Button>
        <span>第 {currentPage} / {totalPages} 页</span>
        <Button disabled={currentPage >= totalPages || loading} onClick={() => updateParams({ page: currentPage + 1 })}>下一页</Button>
        <div>共 {total} 条</div>
      </div>

      <Drawer
        title="筛选"
        placement="bottom"
        height="auto"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        <Form form={form} layout="vertical" onFinish={handleFilters}>
          <Form.Item name="artist" label="艺术家">
            <Input allowClear placeholder="艺术家姓名" />
          </Form.Item>
          <Form.Item name="lotNumber" label="拍品编号">
            <Input allowClear placeholder="拍品编号" />
          </Form.Item>
          <Form.Item name="auctionDate" label="拍卖日期">
            <Input allowClear placeholder="例如 2023" />
          </Form.Item>
          <Form.Item name="hdImageSyncStatus" label="高清大图状态">
            <Select allowClear placeholder="全部状态">
              <Select.Option value="SYNCED">已同步</Select.Option>
              <Select.Option value="UNSYNCED">未同步</Select.Option>
              <Select.Option value="NO_PERMISSION">无权限</Select.Option>
              <Select.Option value="FAILED">失败</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="transactionPriceStatus" label="成交价状态">
            <Select allowClear placeholder="全部状态">
              <Select.Option value="HAS_PRICE">已有成交价</Select.Option>
              <Select.Option value="MISSING">待补充</Select.Option>
              <Select.Option value="LOGIN_REQUIRED">需要登录</Select.Option>
              <Select.Option value="FAILED">补充失败</Select.Option>
            </Select>
          </Form.Item>
          <Space className="mobile-data-drawer-actions">
            <Button onClick={handleResetFilters}>重置</Button>
            <Button type="primary" htmlType="submit">应用筛选</Button>
          </Space>
        </Form>
      </Drawer>
    </MobileDataLayout>
  )
}
