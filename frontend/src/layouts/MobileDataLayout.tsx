import { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { DatabaseOutlined, FolderOpenOutlined, ProfileOutlined, UserOutlined } from '@ant-design/icons'
import { useAuth } from '../auth/AuthContext'
import { permissions } from '../auth/permissions'

export default function MobileDataLayout({ title, children, hideNav = false }: { title: string; children: ReactNode; hideNav?: boolean }) {
  const { hasPermission } = useAuth()
  const showEvaluations = hasPermission(permissions.evaluationView)
  const showDatasets = hasPermission(permissions.autoEvaluationDatasetView)
  const columnCount = 2 + (showEvaluations ? 1 : 0) + (showDatasets ? 1 : 0)

  return (
    <div className="mobile-data-shell">
      <header className="mobile-data-header">
        <div className="mobile-data-header-inner">
          <h1>{title}</h1>
          <NavLink to="/m/profile" aria-label="我的账号">
            <UserOutlined />
          </NavLink>
        </div>
      </header>
      <main className={hideNav ? 'mobile-data-content mobile-data-content-full' : 'mobile-data-content'}>
        {children}
      </main>
      {!hideNav && (
        <nav className="mobile-data-nav">
          <div className="mobile-data-nav-inner" style={{ gridTemplateColumns: `repeat(${columnCount}, 1fr)` }}>
            <NavLink to="/m/artworks">
              <DatabaseOutlined />
              <span>艺术品</span>
            </NavLink>
            {showEvaluations && (
              <NavLink to="/m/evaluations">
                <ProfileOutlined />
                <span>评估</span>
              </NavLink>
            )}
            {showDatasets && (
              <NavLink to="/m/auto-evaluation/datasets">
                <FolderOpenOutlined />
                <span>训练集</span>
              </NavLink>
            )}
            <NavLink to="/m/profile">
              <UserOutlined />
              <span>我的</span>
            </NavLink>
          </div>
        </nav>
      )}
    </div>
  )
}
