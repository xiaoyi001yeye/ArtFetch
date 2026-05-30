import { FileDoneOutlined, UserOutlined } from '@ant-design/icons'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import '../styles/expert-mobile.css'

export default function ExpertMobileLayout() {
  const location = useLocation()
  const reviewing = location.pathname.endsWith('/review')

  return (
    <div className="expert-mobile-shell">
      <main className={`expert-mobile-content${reviewing ? ' expert-review-content' : ''}`}>
        <Outlet />
      </main>
      {!reviewing && (
        <nav className="expert-mobile-nav">
          <div className="expert-mobile-nav-inner">
            <NavLink to="/expert/projects">
              <FileDoneOutlined />
              我的评估
            </NavLink>
            <NavLink to="/expert/profile">
              <UserOutlined />
              我的
            </NavLink>
          </div>
        </nav>
      )}
    </div>
  )
}
