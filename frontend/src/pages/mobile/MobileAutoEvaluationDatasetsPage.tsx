import { useEffect, useState } from 'react'
import { Button, Empty, message, Skeleton, Space, Typography } from 'antd'
import { DownloadOutlined, EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import * as api from '../../api'
import type { AutoEvaluationDataset } from '../../types'
import { datasetStatusTag, formatBytes, strategyText } from '../auto-evaluation/autoEvaluationUi'

const PAGE_SIZE = 10

export default function MobileAutoEvaluationDatasetsPage() {
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const [items, setItems] = useState<AutoEvaluationDataset[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)

  const load = async (p = page) => {
    setLoading(true)
    try {
      const result = await api.listAutoEvaluationDatasets(p, PAGE_SIZE)
      setItems(result.items)
      setTotal(result.total)
      setTotalPages(Math.max(1, result.totalPages || 1))
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

  const download = async (dataset: AutoEvaluationDataset) => {
    try {
      await api.downloadAutoEvaluationDataset(dataset.id)
      message.success('下载已开始')
    } catch (e: any) {
      message.error(e.message)
    }
  }

  return (
    <MobileDataLayout title="训练数据集">
      <div className="mobile-data-toolbar">
        <div className="mobile-data-toolbar-actions">
          <Button icon={<ReloadOutlined />} onClick={() => load(0)} loading={loading}>刷新</Button>
          {hasPermission(permissions.autoEvaluationDatasetCreate) && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/m/auto-evaluation/datasets/new')}>新建</Button>
          )}
        </div>
      </div>

      {loading ? (
        <div className="mobile-data-stack">
          <Skeleton active />
          <Skeleton active />
        </div>
      ) : items.length === 0 ? (
        <Empty description="暂无训练数据集" />
      ) : (
        <div className="mobile-data-stack">
          {items.map((item) => (
            <section className="mobile-dataset-card" key={item.id}>
              <div className="mobile-dataset-card-head">
                <Typography.Text strong className="mobile-dataset-title">{item.name}</Typography.Text>
                {datasetStatusTag(item.status)}
              </div>
              <div className="mobile-dataset-meta">来源：{item.sourceEvaluationName}</div>
              <div className="mobile-dataset-meta">
                策略：{strategyText(item.aggregationStrategy)}{item.selectedExpertName ? ` / ${item.selectedExpertName}` : ''}
              </div>
              <div className="mobile-dataset-stats">
                <span>已选 {item.selectedCount}</span>
                <span>样本 {item.sampleCount}</span>
                <span>跳过 {item.skippedCount}</span>
                <span>{formatBytes(item.zipFileSize || item.estimatedSelectedImageSize)}</span>
              </div>
              <Space className="mobile-dataset-actions">
                <Button block icon={<EyeOutlined />} onClick={() => navigate(`/m/auto-evaluation/datasets/${item.id}`)}>详情</Button>
                {item.status === 'READY' && hasPermission(permissions.autoEvaluationDatasetExport) && (
                  <Button block type="primary" icon={<DownloadOutlined />} onClick={() => download(item)}>下载</Button>
                )}
              </Space>
            </section>
          ))}
        </div>
      )}

      <div className="mobile-data-pagination">
        <Button disabled={page <= 0 || loading} onClick={() => load(page - 1)}>上一页</Button>
        <span>第 {page + 1} / {totalPages} 页</span>
        <Button disabled={page + 1 >= totalPages || loading} onClick={() => load(page + 1)}>下一页</Button>
        <div>共 {total} 个</div>
      </div>
    </MobileDataLayout>
  )
}
