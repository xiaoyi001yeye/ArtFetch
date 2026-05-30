import { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Result } from 'antd'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import ExpertMobileLayout from '../../layouts/ExpertMobileLayout'
import ExpertLoginPage from './ExpertLoginPage'
import ExpertProjectsPage from './ExpertProjectsPage'
import ExpertProjectDetailPage from './ExpertProjectDetailPage'
import ExpertArtworkReviewPage from './ExpertArtworkReviewPage'
import ExpertProfilePage from './ExpertProfilePage'

function RequireExpert({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { user, hasPermission } = useAuth()
  if (!user) {
    return <Navigate to="/expert/login" replace state={{ from: location }} />
  }
  if (!hasPermission(permissions.evaluationReviewAssignedView)) {
    return <Result status="403" title="没有权限" subTitle="当前账号未开通专家评估权限。" />
  }
  return <>{children}</>
}

export default function ExpertMobileRoutes() {
  return (
    <Routes>
      <Route path="/expert/login" element={<ExpertLoginPage />} />
      <Route path="/expert" element={<RequireExpert><ExpertMobileLayout /></RequireExpert>}>
        <Route index element={<Navigate to="/expert/projects" replace />} />
        <Route path="projects" element={<ExpertProjectsPage />} />
        <Route path="projects/:projectId" element={<ExpertProjectDetailPage />} />
        <Route path="projects/:projectId/artworks/:artworkId/review" element={<ExpertArtworkReviewPage />} />
        <Route path="profile" element={<ExpertProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/expert/projects" replace />} />
    </Routes>
  )
}
