import { useMemo, useState } from 'react'
import { Button, Dropdown, Form, Input, Layout, Menu, message, Modal, Space, Typography } from 'antd'
import {
  AuditOutlined,
  CloudServerOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  LogoutOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  SwapOutlined,
  SnippetsOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import TasksPage from './pages/TasksPage'
import ArtworksPage from './pages/ArtworksPage'
import ArtworkDetailPage from './pages/ArtworkDetailPage'
import ArtworkImageViewerPage from './pages/ArtworkImageViewerPage'
import LoginPage from './pages/LoginPage'
import UsersPage from './pages/auth/UsersPage'
import RolesPage from './pages/auth/RolesPage'
import AuditLogsPage from './pages/auth/AuditLogsPage'
import EvaluationsPage from './pages/evaluation/EvaluationsPage'
import EvaluationEditorPage from './pages/evaluation/EvaluationEditorPage'
import EvaluationDetailPage from './pages/evaluation/EvaluationDetailPage'
import EvaluationAuditPage from './pages/evaluation/EvaluationAuditPage'
import MyEvaluationsPage from './pages/evaluation/MyEvaluationsPage'
import ExpertReviewPage from './pages/evaluation/ExpertReviewPage'
import EvaluationMetricsPage from './pages/evaluation/EvaluationMetricsPage'
import EvaluationTemplatesPage from './pages/evaluation/EvaluationTemplatesPage'
import HdImageMigrationsPage from './pages/HdImageMigrationsPage'
import ObjectStorageSettingsPage from './pages/settings/ObjectStorageSettingsPage'
import ExpertMobileRoutes from './pages/expert/ExpertMobileRoutes'
import ArtFetchMark from './components/ArtFetchMark'
import { RequireAuth } from './auth/RequireAuth'
import { useAuth } from './auth/AuthContext'
import { permissions } from './auth/permissions'
import * as api from './api'

const { Header, Content, Footer } = Layout

export default function App() {
  const location = useLocation()
  const { user, logout, hasPermission } = useAuth()
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [passwordForm] = Form.useForm()

  const menuItems = useMemo(() => [
    hasPermission(permissions.taskView) && {
      key: 'tasks',
      icon: <FileSearchOutlined />,
      label: <Link to="/tasks">检索任务</Link>,
    },
    hasPermission(permissions.artworkView) && {
      key: 'artworks',
      icon: <UnorderedListOutlined />,
      label: <Link to="/artworks">艺术品数据</Link>,
    },
    hasPermission(permissions.hdImageMigrationView) && {
      key: 'hd-image-migrations',
      icon: <SwapOutlined />,
      label: <Link to="/hd-image-migrations">高清图迁移</Link>,
    },
    hasPermission(permissions.evaluationView) && {
      key: 'evaluations',
      icon: <ProfileOutlined />,
      label: <Link to="/evaluations">评估项目</Link>,
    },
    hasPermission(permissions.evaluationReviewAssignedView) && {
      key: 'my-evaluations',
      icon: <FileDoneOutlined />,
      label: <Link to="/my-evaluations">我的评估</Link>,
    },
    hasPermission(permissions.evaluationMetricView) && {
      key: 'evaluation-metrics',
      icon: <SnippetsOutlined />,
      label: <Link to="/evaluation-metrics">指标库</Link>,
    },
    hasPermission(permissions.evaluationTemplateView) && {
      key: 'evaluation-templates',
      icon: <SnippetsOutlined />,
      label: <Link to="/evaluation-metric-templates">指标模板</Link>,
    },
    hasPermission(permissions.userView) && {
      key: 'users',
      icon: <TeamOutlined />,
      label: <Link to="/users">用户管理</Link>,
    },
    hasPermission(permissions.roleView) && {
      key: 'roles',
      icon: <SafetyCertificateOutlined />,
      label: <Link to="/roles">角色权限</Link>,
    },
    hasPermission(permissions.auditLogView) && {
      key: 'audit-logs',
      icon: <AuditOutlined />,
      label: <Link to="/audit-logs">审计日志</Link>,
    },
    hasPermission(permissions.objectStorageView) && {
      key: 'object-storage',
      icon: <CloudServerOutlined />,
      label: <Link to="/settings/object-storage">对象存储</Link>,
    },
  ].filter(Boolean) as any[], [hasPermission])

  const defaultPath = useMemo(() => {
    if (hasPermission(permissions.taskView)) return '/tasks'
    if (hasPermission(permissions.artworkView)) return '/artworks'
    if (hasPermission(permissions.hdImageMigrationView)) return '/hd-image-migrations'
    if (hasPermission(permissions.evaluationView)) return '/evaluations'
    if (hasPermission(permissions.evaluationReviewAssignedView)) return '/my-evaluations'
    if (hasPermission(permissions.evaluationMetricView)) return '/evaluation-metrics'
    if (hasPermission(permissions.evaluationTemplateView)) return '/evaluation-metric-templates'
    if (hasPermission(permissions.userView)) return '/users'
    if (hasPermission(permissions.roleView)) return '/roles'
    if (hasPermission(permissions.auditLogView)) return '/audit-logs'
    if (hasPermission(permissions.objectStorageView)) return '/settings/object-storage'
    return '/login'
  }, [hasPermission])

  const selectedKey = useMemo(() => {
    if (location.pathname.startsWith('/artworks')) return 'artworks'
    if (location.pathname.startsWith('/hd-image-migrations')) return 'hd-image-migrations'
    if (location.pathname.startsWith('/settings/object-storage')) return 'object-storage'
    if (location.pathname.startsWith('/my-evaluations')) return 'my-evaluations'
    if (location.pathname.startsWith('/evaluation-metrics')) return 'evaluation-metrics'
    if (location.pathname.startsWith('/evaluation-metric-templates')) return 'evaluation-templates'
    if (location.pathname.startsWith('/evaluations')) return 'evaluations'
    if (location.pathname.startsWith('/users')) return 'users'
    if (location.pathname.startsWith('/roles')) return 'roles'
    if (location.pathname.startsWith('/audit-logs')) return 'audit-logs'
    return 'tasks'
  }, [location.pathname])

  if (location.pathname.startsWith('/expert')) {
    return <ExpertMobileRoutes />
  }

  if (location.pathname === '/login') {
    return <LoginPage />
  }

  const submitPassword = async (values: { oldPassword: string; newPassword: string }) => {
    try {
      await api.changePassword(values)
      message.success('密码已修改，请重新登录')
      setPasswordOpen(false)
      passwordForm.resetFields()
      await logout()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 24, padding: '0 24px' }}>
        <Typography.Title
          level={4}
          style={{
            color: '#fff',
            margin: 0,
            whiteSpace: 'nowrap',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 10,
          }}
        >
          <ArtFetchMark size={28} />
          <span>ArtFetch</span>
        </Typography.Title>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[selectedKey]}
          style={{ flex: 1, minWidth: 0 }}
          items={menuItems}
        />
        {user && (
          <Dropdown
            menu={{
              items: [
                { key: 'password', icon: <UserOutlined />, label: '修改密码', onClick: () => setPasswordOpen(true) },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: logout },
              ],
            }}
          >
            <Button type="text" style={{ color: '#fff' }}>
              <Space>
                <UserOutlined />
                {user.displayName || user.username}
              </Space>
            </Button>
          </Dropdown>
        )}
      </Header>

      <Content style={{ padding: '24px', background: '#f5f5f5' }}>
        <Routes>
          <Route path="/" element={<RequireAuth><Navigate to={defaultPath} replace /></RequireAuth>} />
          <Route path="/tasks" element={<RequireAuth permissions={[permissions.taskView]}><TasksPage /></RequireAuth>} />
          <Route path="/artworks" element={<RequireAuth permissions={[permissions.artworkView]}><ArtworksPage /></RequireAuth>} />
          <Route path="/artworks/:id" element={<RequireAuth permissions={[permissions.artworkView]}><ArtworkDetailPage /></RequireAuth>} />
          <Route path="/artworks/:id/images/:kind" element={<RequireAuth permissions={[permissions.artworkView, permissions.artworkImageView]}><ArtworkImageViewerPage /></RequireAuth>} />
          <Route path="/hd-image-migrations" element={<RequireAuth permissions={[permissions.hdImageMigrationView]}><HdImageMigrationsPage /></RequireAuth>} />
          <Route path="/evaluations" element={<RequireAuth permissions={[permissions.evaluationView]}><EvaluationsPage /></RequireAuth>} />
          <Route path="/evaluations/new" element={<RequireAuth permissions={[permissions.evaluationCreate]}><EvaluationEditorPage /></RequireAuth>} />
          <Route path="/evaluations/:id" element={<RequireAuth><EvaluationDetailPage /></RequireAuth>} />
          <Route path="/evaluations/:id/edit" element={<RequireAuth permissions={[permissions.evaluationUpdate]}><EvaluationEditorPage /></RequireAuth>} />
          <Route path="/evaluations/:id/audit" element={<RequireAuth permissions={[permissions.evaluationAuditView]}><EvaluationAuditPage /></RequireAuth>} />
          <Route path="/evaluations/:evaluationId/artworks/:artworkId/review" element={<RequireAuth permissions={[permissions.evaluationReviewOwnView]}><ExpertReviewPage /></RequireAuth>} />
          <Route path="/my-evaluations" element={<RequireAuth permissions={[permissions.evaluationReviewAssignedView]}><MyEvaluationsPage /></RequireAuth>} />
          <Route path="/evaluation-metrics" element={<RequireAuth permissions={[permissions.evaluationMetricView]}><EvaluationMetricsPage /></RequireAuth>} />
          <Route path="/evaluation-metric-templates" element={<RequireAuth permissions={[permissions.evaluationTemplateView]}><EvaluationTemplatesPage /></RequireAuth>} />
          <Route path="/users" element={<RequireAuth permissions={[permissions.userView]}><UsersPage /></RequireAuth>} />
          <Route path="/roles" element={<RequireAuth permissions={[permissions.roleView]}><RolesPage /></RequireAuth>} />
          <Route path="/audit-logs" element={<RequireAuth permissions={[permissions.auditLogView]}><AuditLogsPage /></RequireAuth>} />
          <Route path="/settings/object-storage" element={<RequireAuth permissions={[permissions.objectStorageView]}><ObjectStorageSettingsPage /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Content>

      <Footer style={{ textAlign: 'center', color: '#888' }}>
        ArtFetch ©{new Date().getFullYear()} — 艺术品数据检索平台
      </Footer>

      <Modal title="修改密码" open={passwordOpen} onCancel={() => setPasswordOpen(false)} onOk={() => passwordForm.submit()} okText="保存">
        <Form form={passwordForm} layout="vertical" onFinish={submitPassword}>
          <Form.Item name="oldPassword" label="原密码" rules={[{ required: true, message: '请输入原密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 8, message: '密码至少 8 位' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  )
}
