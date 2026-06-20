import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from '../../auth/RequireAuth'
import { permissions } from '../../auth/permissions'
import MobileArtworksPage from './MobileArtworksPage'
import MobileArtworkDetailPage from './MobileArtworkDetailPage'
import MobileArtworkImageViewerPage from './MobileArtworkImageViewerPage'
import MobileEvaluationsPage from './MobileEvaluationsPage'
import MobileEvaluationNewPage from './MobileEvaluationNewPage'
import MobileEvaluationDetailPage from './MobileEvaluationDetailPage'
import MobileEvaluationAuditPage from './MobileEvaluationAuditPage'
import MobileAutoEvaluationDatasetsPage from './MobileAutoEvaluationDatasetsPage'
import MobileAutoEvaluationDatasetNewPage from './MobileAutoEvaluationDatasetNewPage'
import MobileAutoEvaluationDatasetDetailPage from './MobileAutoEvaluationDatasetDetailPage'
import MobileProfilePage from './MobileProfilePage'
import '../../styles/mobile-data.css'

export default function MobileRoutes() {
  return (
    <Routes>
      <Route path="/m" element={<Navigate to="/m/artworks" replace />} />
      <Route path="/m/artworks" element={<RequireAuth permissions={[permissions.artworkView]}><MobileArtworksPage /></RequireAuth>} />
      <Route path="/m/artworks/:id" element={<RequireAuth permissions={[permissions.artworkView]}><MobileArtworkDetailPage /></RequireAuth>} />
      <Route path="/m/artworks/:id/images/:kind" element={<RequireAuth permissions={[permissions.artworkImageView]}><MobileArtworkImageViewerPage /></RequireAuth>} />
      <Route path="/m/evaluations" element={<RequireAuth permissions={[permissions.evaluationView]}><MobileEvaluationsPage /></RequireAuth>} />
      <Route path="/m/evaluations/new" element={<RequireAuth permissions={[permissions.evaluationCreate, permissions.userView, permissions.evaluationTemplateView]}><MobileEvaluationNewPage /></RequireAuth>} />
      <Route path="/m/evaluations/:id" element={<RequireAuth permissions={[permissions.evaluationView]}><MobileEvaluationDetailPage /></RequireAuth>} />
      <Route path="/m/evaluations/:id/edit" element={<RequireAuth permissions={[permissions.evaluationUpdate, permissions.userView, permissions.evaluationTemplateView]}><MobileEvaluationNewPage /></RequireAuth>} />
      <Route path="/m/evaluations/:id/audit" element={<RequireAuth permissions={[permissions.evaluationAuditView]}><MobileEvaluationAuditPage /></RequireAuth>} />
      <Route path="/m/auto-evaluation/datasets" element={<RequireAuth permissions={[permissions.autoEvaluationDatasetView]}><MobileAutoEvaluationDatasetsPage /></RequireAuth>} />
      <Route path="/m/auto-evaluation/datasets/new" element={<RequireAuth permissions={[permissions.autoEvaluationDatasetCreate]}><MobileAutoEvaluationDatasetNewPage /></RequireAuth>} />
      <Route path="/m/auto-evaluation/datasets/:id" element={<RequireAuth permissions={[permissions.autoEvaluationDatasetView]}><MobileAutoEvaluationDatasetDetailPage /></RequireAuth>} />
      <Route path="/m/profile" element={<RequireAuth><MobileProfilePage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/m/artworks" replace />} />
    </Routes>
  )
}
