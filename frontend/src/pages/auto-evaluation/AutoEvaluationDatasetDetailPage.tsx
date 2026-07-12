import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Checkbox, Descriptions, Empty, Form, Input, message, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import { ArrowLeftOutlined, CheckCircleOutlined, DownloadOutlined, PlayCircleOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import * as api from '../../api'
import type {
  AutoEvaluationArtworkCandidate,
  AutoEvaluationDataset,
  CheckAutoEvaluationDatasetResponse,
  EvaluationProjectExpert,
} from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import { datasetStatusTag, formatBytes, imageSourceTag, strategyText } from './autoEvaluationUi'

type SettingsFormValues = {
  name: string
  aggregationStrategy: 'AVERAGE_ALL_EXPERTS' | 'SELECTED_EXPERT'
  selectedExpertId?: number
}

export default function AutoEvaluationDatasetDetailPage() {
  const { id } = useParams()
  const datasetId = Number(id)
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const [dataset, setDataset] = useState<AutoEvaluationDataset>()
  const [artworks, setArtworks] = useState<AutoEvaluationArtworkCandidate[]>([])
  const [artworkTotal, setArtworkTotal] = useState(0)
  const [artworkPage, setArtworkPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [artworksLoading, setArtworksLoading] = useState(false)
  const [checking, setChecking] = useState(false)
  const [checkResult, setCheckResult] = useState<CheckAutoEvaluationDatasetResponse>()
  const [experts, setExperts] = useState<EvaluationProjectExpert[]>([])
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [form] = Form.useForm<SettingsFormValues>()

  const editable = dataset && ['DRAFT', 'FAILED'].includes(dataset.status)
  const canCreate = hasPermission(permissions.autoEvaluationDatasetCreate)
  const canExport = hasPermission(permissions.autoEvaluationDatasetExport)

  const load = async () => {
    if (!Number.isFinite(datasetId)) return
    setLoading(true)
    try {
      const result = await api.getAutoEvaluationDataset(datasetId)
      setDataset(result)
      form.setFieldsValue({
        name: result.name,
        aggregationStrategy: result.aggregationStrategy,
        selectedExpertId: result.selectedExpertId,
      })
      const expertItems = await api.listEvaluationExperts(result.sourceEvaluationId)
      setExperts(expertItems)
    } catch (e: any) {
      message.error(e.message)
      navigate('/auto-evaluation/datasets')
    } finally {
      setLoading(false)
    }
  }

  const loadArtworks = async (p = artworkPage, q = keyword) => {
    if (!Number.isFinite(datasetId)) return
    setArtworksLoading(true)
    try {
      const result = await api.listAutoEvaluationDatasetArtworks(datasetId, { page: p, size: 20, keyword: q || undefined })
      setArtworks(result.items)
      setArtworkTotal(result.total)
      setArtworkPage(p)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setArtworksLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [datasetId])

  useEffect(() => {
    if (!editable) return
    loadArtworks(0, '')
  }, [dataset?.id, dataset?.status])

  useEffect(() => {
    if (dataset?.status !== 'GENERATING') return
    const timer = window.setInterval(load, 5000)
    return () => window.clearInterval(timer)
  }, [dataset?.status])

  const selectedOnPage = useMemo(() => artworks.filter((item) => item.selected).map((item) => item.artworkId), [artworks])
  const currentPageIds = useMemo(() => artworks.map((item) => item.artworkId), [artworks])

  const updateSelection = async (artworkIds: number[], selected: boolean) => {
    if (!dataset || artworkIds.length === 0) return
    try {
      const next = await api.updateAutoEvaluationDatasetSelection(dataset.id, artworkIds, selected)
      setDataset(next)
      await loadArtworks()
      setCheckResult(undefined)
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const saveSettings = async () => {
    if (!dataset) return
    const values = await form.validateFields()
    try {
      const updated = await api.updateAutoEvaluationDataset(dataset.id, values)
      setDataset(updated)
      setSettingsOpen(false)
      setCheckResult(undefined)
      message.success('配置已保存')
    } catch (e: any) {
      message.error(e.message)
    }
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
      const updated = await api.generateAutoEvaluationDataset(dataset.id)
      setDataset(updated)
      message.success('已开始生成训练包')
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const columns: ColumnsType<AutoEvaluationArtworkCandidate> = [
    {
      title: '选择',
      width: 72,
      render: (_, record) => (
        <Checkbox disabled={!editable || !canCreate} checked={record.selected} onChange={(event) => updateSelection([record.artworkId], event.target.checked)} />
      ),
    },
    {
      title: '作品',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.title}</Typography.Text>
          <Typography.Text type="secondary">{record.artist || '未知作者'}{record.lotNumber ? ` / ${record.lotNumber}` : ''}</Typography.Text>
        </Space>
      ),
    },
    { title: '拍卖日期', dataIndex: 'auctionDate', width: 140, render: (v) => v || '—' },
    { title: '图片来源', dataIndex: 'imageSourceCandidate', width: 120, render: imageSourceTag },
    { title: '预计大小', dataIndex: 'estimatedImageSize', width: 110, render: formatBytes },
  ]

  if (!dataset) return null

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/auto-evaluation/datasets')}>返回</Button>
          <Typography.Title level={4} style={{ margin: 0 }}>{dataset.name}</Typography.Title>
          {datasetStatusTag(dataset.status)}
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => { load(); if (editable) loadArtworks() }}>刷新</Button>
          {editable && canCreate && <Button onClick={() => setSettingsOpen(true)}>编辑配置</Button>}
          {dataset.status === 'READY' && canExport && (
            <Button type="primary" icon={<DownloadOutlined />} onClick={() => api.downloadAutoEvaluationDataset(dataset.id)}>下载训练包</Button>
          )}
        </Space>
      </Space>

      {dataset.errorMessage && <Alert type="error" showIcon message="生成失败" description={dataset.errorMessage} />}
      {dataset.status === 'GENERATING' && <Alert type="info" showIcon message="训练包正在生成，页面会自动刷新状态。" />}

      <Card loading={loading}>
        <Descriptions column={3} bordered size="small">
          <Descriptions.Item label="来源项目">{dataset.sourceEvaluationName}</Descriptions.Item>
          <Descriptions.Item label="汇总策略">
            {strategyText(dataset.aggregationStrategy)}{dataset.selectedExpertName ? `：${dataset.selectedExpertName}` : ''}
          </Descriptions.Item>
          <Descriptions.Item label="已选作品">{dataset.selectedCount}</Descriptions.Item>
          <Descriptions.Item label="样本/跳过">{dataset.sampleCount} / {dataset.skippedCount}</Descriptions.Item>
          <Descriptions.Item label="预计图片大小">{formatBytes(dataset.estimatedSelectedImageSize)}</Descriptions.Item>
          <Descriptions.Item label="ZIP 大小">{formatBytes(dataset.zipFileSize)}</Descriptions.Item>
          <Descriptions.Item label="SHA256" span={3}>{dataset.zipSha256 || '—'}</Descriptions.Item>
        </Descriptions>
      </Card>

      {editable && (
        <Card title="选择作品">
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
              <Input.Search
                allowClear
                style={{ width: 360 }}
                prefix={<SearchOutlined />}
                placeholder="搜索标题、作者或编号"
                onSearch={(value) => { setKeyword(value); loadArtworks(0, value) }}
              />
              <Space>
                <Button disabled={currentPageIds.length === 0} onClick={() => updateSelection(currentPageIds, true)}>选择当前页</Button>
                <Button disabled={selectedOnPage.length === 0} onClick={() => updateSelection(selectedOnPage, false)}>取消当前页</Button>
                <Button danger onClick={async () => {
                  const updated = await api.clearAutoEvaluationDatasetSelection(dataset.id)
                  setDataset(updated)
                  await loadArtworks()
                  setCheckResult(undefined)
                }}>清空已选</Button>
              </Space>
            </Space>
            <Table
              rowKey="artworkId"
              columns={columns}
              dataSource={artworks}
              loading={artworksLoading}
              pagination={{
                current: artworkPage + 1,
                pageSize: 20,
                total: artworkTotal,
                onChange: (p) => loadArtworks(p - 1),
                showTotal: (t) => `共 ${t} 件作品`,
              }}
            />
          </Space>
        </Card>
      )}

      <Card title="检查与生成">
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space>
            <Button icon={<CheckCircleOutlined />} loading={checking} onClick={check}>检查已选作品</Button>
            {editable && canCreate && (
              <Button type="primary" icon={<PlayCircleOutlined />} disabled={!checkResult || checkResult.sampleCount === 0 || checkResult.exceedsMobileHardLimit} onClick={generate}>
                生成训练包
              </Button>
            )}
          </Space>
          {checkResult && (
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <Alert
                type={checkResult.exceedsMobileHardLimit ? 'error' : checkResult.skippedCount > 0 || checkResult.exceedsMobileSoftLimit ? 'warning' : 'success'}
                showIcon
                message={`可生成 ${checkResult.sampleCount} 件，跳过 ${checkResult.skippedCount} 件，预计 ${formatBytes(checkResult.estimatedPackageSize)}`}
                description={checkResult.exceedsMobileHardLimit ? '预计大小超过硬上限，请减少样本数量。' : checkResult.exceedsMobileSoftLimit ? '预计包较大，建议回到桌面端下载。' : undefined}
              />
              {checkResult.skippedSamples.length > 0 ? (
                <Table
                  size="small"
                  rowKey="artworkId"
                  dataSource={checkResult.skippedSamples}
                  pagination={{ pageSize: 5 }}
                  columns={[
                    { title: '作品', dataIndex: 'title' },
                    { title: '编号', dataIndex: 'lotNumber', width: 120, render: (v) => v || '—' },
                    { title: '原因', dataIndex: 'reasons', render: (items: string[]) => <Space wrap>{items.map((item) => <Tag color="red" key={item}>{item}</Tag>)}</Space> },
                    { title: '缺失指标', dataIndex: 'missingMetricCodes', render: (items: string[]) => items?.join('、') || '—' },
                  ]}
                />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有跳过样本" />
              )}
            </Space>
          )}
        </Space>
      </Card>

      <Modal title="编辑数据集配置" open={settingsOpen} onCancel={() => setSettingsOpen(false)} onOk={saveSettings} okText="保存">
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="数据集名称" rules={[{ required: true, message: '请输入数据集名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="aggregationStrategy" label="汇总策略">
            <Select options={[
              { value: 'AVERAGE_ALL_EXPERTS', label: '所有专家平均值' },
              { value: 'SELECTED_EXPERT', label: '指定专家' },
            ]} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.aggregationStrategy !== next.aggregationStrategy}>
            {({ getFieldValue }) => getFieldValue('aggregationStrategy') === 'SELECTED_EXPERT' && (
              <Form.Item name="selectedExpertId" label="指定专家" rules={[{ required: true, message: '请选择专家' }]}>
                <Select options={experts.map((item) => ({ value: item.expertId, label: item.expertName }))} />
              </Form.Item>
            )}
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
