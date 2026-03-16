import { Layout, Menu, Typography } from 'antd'
import { FileSearchOutlined, PictureOutlined, UnorderedListOutlined } from '@ant-design/icons'
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import TasksPage from './pages/TasksPage'
import ArtworksPage from './pages/ArtworksPage'
import ArtworkDetailPage from './pages/ArtworkDetailPage'

const { Header, Content, Footer } = Layout

export default function App() {
  const location = useLocation()

  const selectedKey = location.pathname.startsWith('/artworks') ? 'artworks' : 'tasks'

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 24, padding: '0 24px' }}>
        <Typography.Title level={4} style={{ color: '#fff', margin: 0, whiteSpace: 'nowrap' }}>
          <PictureOutlined style={{ marginRight: 8 }} />
          ArtFetch
        </Typography.Title>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[selectedKey]}
          style={{ flex: 1, minWidth: 0 }}
          items={[
            {
              key: 'tasks',
              icon: <FileSearchOutlined />,
              label: <Link to="/tasks">检索任务</Link>,
            },
            {
              key: 'artworks',
              icon: <UnorderedListOutlined />,
              label: <Link to="/artworks">艺术品数据</Link>,
            },
          ]}
        />
      </Header>

      <Content style={{ padding: '24px', background: '#f5f5f5' }}>
        <Routes>
          <Route path="/" element={<Navigate to="/tasks" replace />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/artworks" element={<ArtworksPage />} />
          <Route path="/artworks/:id" element={<ArtworkDetailPage />} />
        </Routes>
      </Content>

      <Footer style={{ textAlign: 'center', color: '#888' }}>
        ArtFetch ©{new Date().getFullYear()} — 艺术品数据检索平台
      </Footer>
    </Layout>
  )
}
