import { useEffect, useState } from 'react'
import { Button, Form, Input, message, Select, Space } from 'antd'
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import * as api from '../../api'
import type { AutoEvaluationSourceProject, EvaluationProjectExpert } from '../../types'

type CreateFormValues = {
  name: string
  sourceEvaluationId: number
  aggregationStrategy: 'AVERAGE_ALL_EXPERTS' | 'SELECTED_EXPERT'
  selectedExpertId?: number
}

export default function MobileAutoEvaluationDatasetNewPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm<CreateFormValues>()
  const [sourceProjects, setSourceProjects] = useState<AutoEvaluationSourceProject[]>([])
  const [experts, setExperts] = useState<EvaluationProjectExpert[]>([])
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    setLoading(true)
    api.listAutoEvaluationSourceProjects({ page: 0, size: 100 })
      .then((result) => setSourceProjects(result.items))
      .catch((e: any) => message.error(e.message))
      .finally(() => setLoading(false))
    form.setFieldValue('aggregationStrategy', 'AVERAGE_ALL_EXPERTS')
  }, [form])

  const onSourceChange = async (evaluationId: number) => {
    form.setFieldValue('selectedExpertId', undefined)
    const project = sourceProjects.find((item) => item.id === evaluationId)
    if (project && !form.getFieldValue('name')) {
      form.setFieldValue('name', `${project.name} 训练数据集`)
    }
    try {
      const result = await api.listEvaluationExperts(evaluationId)
      setExperts(result)
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const create = async () => {
    const values = await form.validateFields()
    setCreating(true)
    try {
      const dataset = await api.createAutoEvaluationDataset(values)
      message.success('训练数据集草稿已创建')
      navigate(`/m/auto-evaluation/datasets/${dataset.id}`, { replace: true })
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setCreating(false)
    }
  }

  return (
    <MobileDataLayout title="新建训练集">
      <div className="mobile-detail-topbar">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/m/auto-evaluation/datasets')}>返回</Button>
      </div>

      <section className="mobile-detail-section">
        <Form form={form} layout="vertical">
          <Form.Item name="sourceEvaluationId" label="来源评估项目" rules={[{ required: true, message: '请选择来源项目' }]}>
            <Select
              loading={loading}
              showSearch
              optionFilterProp="label"
              placeholder="选择已完成评估项目"
              options={sourceProjects.map((item) => ({
                value: item.id,
                label: `${item.name}（${item.artworkCount} 件 / ${item.expertCount} 位专家）`,
              }))}
              onChange={onSourceChange}
            />
          </Form.Item>
          <Form.Item name="name" label="数据集名称" rules={[{ required: true, message: '请输入数据集名称' }]}>
            <Input placeholder="训练数据集名称" />
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
                <Select placeholder="选择专家" options={experts.map((item) => ({ value: item.expertId, label: item.expertName }))} />
              </Form.Item>
            )}
          </Form.Item>
        </Form>
      </section>

      <Space direction="vertical" className="mobile-detail-actions">
        <Button type="primary" icon={<SaveOutlined />} loading={creating} onClick={create}>创建草稿</Button>
      </Space>
    </MobileDataLayout>
  )
}
