import { Tag } from 'antd'

export const formatBytes = (value?: number) => {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = value
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
}

export const datasetStatusTag = (status: string) => {
  const color: Record<string, string> = {
    DRAFT: 'blue',
    GENERATING: 'processing',
    READY: 'green',
    FAILED: 'red',
    ARCHIVED: 'default',
  }
  const text: Record<string, string> = {
    DRAFT: '草稿',
    GENERATING: '生成中',
    READY: '可下载',
    FAILED: '失败',
    ARCHIVED: '已归档',
  }
  return <Tag color={color[status] || 'default'}>{text[status] || status}</Tag>
}

export const strategyText = (strategy: string) => (
  strategy === 'SELECTED_EXPERT' ? '指定专家' : '所有专家平均'
)

export const imageSourceTag = (source: string) => {
  if (source === 'HD') return <Tag color="green">高清图</Tag>
  if (source === 'ORIGINAL') return <Tag color="blue">原图</Tag>
  return <Tag color="red">缺图</Tag>
}
