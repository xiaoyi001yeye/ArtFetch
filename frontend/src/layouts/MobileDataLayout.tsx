import { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { DatabaseOutlined, UserOutlined } from '@ant-design/icons'

export default function MobileDataLayout({ title, children, hideNav = false }: { title: string; children: ReactNode; hideNav?: boolean }) {
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
          <div className="mobile-data-nav-inner">
            <NavLink to="/m/artworks">
              <DatabaseOutlined />
              <span>艺术品</span>
            </NavLink>
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
