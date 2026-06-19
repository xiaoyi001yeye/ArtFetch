import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Checkbox, Empty, Input, message, Modal, Skeleton, Space, Tag, Typography } from 'antd'
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  ClearOutlined,
  DownloadOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import * as api from '../../api'
import type { AutoEvaluationArtworkCandidate, AutoEvaluationDataset, CheckAutoEvaluationDatasetResponse } from '../../types'
import { datasetStatusTag, formatBytes, imageSourceTag, strategyText } from '../auto-evaluation/autoEvaluationUi'

const PAGE_SIZE = 10

export default function MobileAutoEvaluationDatasetDetailPage() {
  const { id } = useParams()
  const datasetId = Number(id)
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const [dataset, setDataset] = useState<AutoEvaluationDataset>()
  const [artworks, setArtworks] = useState<AutoEvaluationArtworkCandidate[]>([])
  const [artworkTotal, setArtworkTotal] = useState(0)
  const [artworkTotalPages, setArtworkTotalPages] = useState(1)
  const [artworkPage, setArtworkPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [artworksLoading, setArtworksLoading] = useState(true)
  const [checking, setChecking] = useState(false)
  const [checkResult, setCheckResult] = useState<CheckAutoEvaluationDatasetResponse>()

  const editable = dataset && ['DRAFT', 'FAILED'].includes(dataset.status)
  const canCreate = hasPermission(permissions.autoEvaluationDatasetCreate)
  const canExport = hasPermission(permissions.autoEvaluationDatasetExport)

  const loadDataset = async () => {
    if (!Number.isFinite(datasetId)) return
    try {
      const result = await api.getAutoEvaluationDataset(datasetId)
      setDataset(result)
    } catch (e: any) {
      message.error(e.message)
      navigate('/m/auto-evaluation/datasets', { replace: true })
    } finally {
      setLoading(false)
    }
  }

  const loadArtworks = async (p = artworkPage, q = keyword) => {
    if (!Number.isFinite(datasetId)) return
    setArtworksLoading(true)
    try {
      const result = await api.listAutoEvaluationDatasetArtworks(datasetId, { page: p, size: PAGE_SIZE, keyword: q || undefined })
      setArtworks(result.items)
      setArtworkTotal(result.total)
      setArtworkTotalPages(Math.max(1, result.totalPages || 1))
      setArtworkPage(p)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setArtworksLoading(false)
    }
  }

  useEffect(() => {
    loadDataset()
    loadArtworks(0, '')
  }, [datasetId])

  useEffect(() => {
    if (dataset?.status !== 'GENERATING') return
    const timer = window.setInterval(loadDataset, 5000)
    return () => window.clearInterval(timer)
  }, [dataset?.status])

  const currentPageIds = useMemo(() => artworks.map((item) => item.artworkId), [artworks])
  const selectedOnPage = useMemo(() => artworks.filter((item) => item.selected).map((item) => item.artworkId), [artworks])

  const updateSelection = async (artworkIds: number[], selected: boolean) => {
    if (!dataset || artworkIds.length === 0) return
    try {
      const next = await api.updateAutoEvaluationDatasetSelection(dataset.id, artworkIds, selected)
      setDataset(next)
      setCheckResult(undefined)
      await loadArtworks()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const clearSelection = () => {
    if (!dataset) return
    Modal.confirm({
      title: '清空已选作品',
      content: '已保存的选择会被清空。',
      okText: '清空',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          const next = await api.clearAutoEvaluationDatasetSelection(dataset.id)
          setDataset(next)
          setCheckResult(undefined)
          await loadArtworks()
        } catch (e: any) {
          message.error(e.message)
        }
      },
    })
  }

  const check = async () => {
    if (!dataset) return
    setChecking(true)
    try {
      const result = await api.checkAutoEvaluationDataset(dataset.id)
      setCheckResult(result)
      message.success('检查完成')
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setChecking(false)
    }
  }

  const generate = async () => {
    if (!dataset) return
    try {
      const next = await api.generateAutoEvaluationDataset(dataset.id)
      setDataset(next)
      message.success('已开始生成训练包')
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const download = async () => {
    if (!dataset) return
    try {
      await api.downloadAutoEvaluationDataset(dataset.id)
      message.success('下载已开始')
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const search = (value: string) => {
    const next = value.trim()
    setKeyword(next)
    loadArtworks(0, next)
  }

  if (loading && !dataset) {
    return (
      <MobileDataLayout title="训练集详情">
        <Skeleton active />
      </MobileDataLayout>
    )
  }

  if (!dataset) return null

  return (
    <MobileDataLayout title="训练集详情">
      <div className="mobile-detail-topbar">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/m/auto-evaluation/datasets')}>返回</Button>
        <Button icon={<ReloadOutlined />} onClick={() => { loadDataset(); loadArtworks() }}>刷新</Button>
      </div>

      {dataset.errorMessage && <Alert type="error" showIcon message="生成失败" description={dataset.errorMessage} className="mobile-data-alert" />}
      {dataset.status === 'GENERATING' && <Alert type="info" showIcon message="训练包正在生成" className="mobile-data-alert" />}

      <section className="mobile-detail-section">
        <div className="mobile-dataset-card-head">
          <Typography.Title level={4} className="mobile-dataset-heading">{dataset.name}</Typography.Title>
          {datasetStatusTag(dataset.status)}
        </div>
        <div className="mobile-dataset-meta">来源：{dataset.sourceEvaluationName}</div>
        <div className="mobile-dataset-meta">
          策略：{strategyText(dataset.aggregationStrategy)}{dataset.selectedExpertName ? ` / ${dataset.selectedExpertName}` : ''}
        </div>
        <div className="mobile-dataset-stats">
          <span>已选 {dataset.selectedCount}</span>
          <span>样本 {dataset.sampleCount}</span>
          <span>跳过 {dataset.skippedCount}</span>
          <span>{formatBytes(dataset.zipFileSize || dataset.estimatedSelectedImageSize)}</span>
        </div>
        {dataset.zipSha256 && <div className="mobile-dataset-hash">SHA256：{dataset.zipSha256}</div>}
        {dataset.status === 'READY' && canExport && (
          <Button block type="primary" icon={<DownloadOutlined />} className="mobile-detail-primary-action" onClick={download}>下载训练包</Button>
        )}
      </section>

      {editable && canCreate && (
        <section className="mobile-detail-section">
          <Typography.Title level={5}>选择作品</Typography.Title>
          <div className="mobile-data-toolbar">
            <Input.Search
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索标题、作者或编号"
              onSearch={(value, _event, info) => {
                if (info?.source === 'clear') search('')
                else search(value)
              }}
            />
            <div className="mobile-dataset-select-actions">
              <Button disabled={currentPageIds.length === 0 || artworksLoading} onClick={() => updateSelection(currentPageIds, true)}>选择本页</Button>
              <Button disabled={selectedOnPage.length === 0 || artworksLoading} onClick={() => updateSelection(selectedOnPage, false)}>取消本页</Button>
              <Button danger icon={<ClearOutlined />} disabled={dataset.selectedCount === 0} onClick={clearSelection}>清空</Button>
            </div>
          </div>

          {artworksLoading ? (
            <Skeleton active />
          ) : artworks.length === 0 ? (
            <Empty description="没有符合条件的作品" />
          ) : (
            <div className="mobile-data-stack">
              {artworks.map((artwork) => (
                <label className="mobile-dataset-artwork" key={artwork.artworkId}>
                  <Checkbox checked={artwork.selected} onChange={(event) => updateSelection([artwork.artworkId], event.target.checked)} />
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
                    <div className="mobile-artwork-source">
                      {imageSourceTag(artwork.imageSourceCandidate)}
                      <span>{formatBytes(artwork.estimatedImageSize)}</span>
                    </div>
                  </div>
                </label>
              ))}
            </div>
          )}

          <div className="mobile-data-pagination">
            <Button disabled={artworkPage <= 0 || artworksLoading} onClick={() => loadArtworks(artworkPage - 1)}>上一页</Button>
            <span>第 {artworkPage + 1} / {artworkTotalPages} 页</span>
            <Button disabled={artworkPage + 1 >= artworkTotalPages || artworksLoading} onClick={() => loadArtworks(artworkPage + 1)}>下一页</Button>
            <div>共 {artworkTotal} 件</div>
          </div>
        </section>
      )}

      <section className="mobile-detail-section">
        <Typography.Title level={5}>检查与生成</Typography.Title>
        <Space direction="vertical" className="mobile-profile-actions">
          <Button icon={<CheckCircleOutlined />} loading={checking} onClick={check}>检查已选作品</Button>
          {editable && canCreate && (
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              disabled={!checkResult || checkResult.sampleCount === 0 || checkResult.exceedsMobileHardLimit}
              onClick={generate}
            >
              生成训练包
            </Button>
          )}
        </Space>
        {checkResult && (
          <div className="mobile-check-result">
            <Alert
              showIcon
              type={checkResult.exceedsMobileHardLimit ? 'error' : checkResult.skippedCount > 0 || checkResult.exceedsMobileSoftLimit ? 'warning' : 'success'}
              message={`可生成 ${checkResult.sampleCount} 件，跳过 ${checkResult.skippedCount} 件`}
              description={`预计大小 ${formatBytes(checkResult.estimatedPackageSize)}`}
            />
            {checkResult.exceedsMobileHardLimit && <Typography.Text type="danger">预计包过大，请减少选择数量后再生成。</Typography.Text>}
            {checkResult.exceedsMobileSoftLimit && !checkResult.exceedsMobileHardLimit && <Typography.Text type="warning">包体较大，建议在稳定网络下下载。</Typography.Text>}
            {checkResult.skippedSamples.length > 0 && (
              <div className="mobile-skipped-list">
                {checkResult.skippedSamples.slice(0, 5).map((item) => (
                  <div className="mobile-skipped-item" key={item.artworkId}>
                    <Typography.Text strong>{item.title}</Typography.Text>
                    <div>
                      {item.reasons.map((reason) => <Tag color="red" key={reason}>{reason}</Tag>)}
                    </div>
                  </div>
                ))}
                {checkResult.skippedSamples.length > 5 && (
                  <Typography.Text type="secondary">还有 {checkResult.skippedSamples.length - 5} 件跳过作品，可在桌面端查看完整列表。</Typography.Text>
                )}
              </div>
            )}
          </div>
        )}
      </section>
    </MobileDataLayout>
  )
}
