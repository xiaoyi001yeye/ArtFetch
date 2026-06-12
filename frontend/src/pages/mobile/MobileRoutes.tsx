import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from '../../auth/RequireAuth'
import { permissions } from '../../auth/permissions'
import MobileArtworksPage from './MobileArtworksPage'
import MobileArtworkDetailPage from './MobileArtworkDetailPage'
import MobileArtworkImageViewerPage from './MobileArtworkImageViewerPage'
import MobileProfilePage from './MobileProfilePage'
import '../../styles/mobile-data.css'

export default function MobileRoutes() {
  return (
    <Routes>
      <Route path="/m" element={<Navigate to="/m/artworks" replace />} />
      <Route path="/m/artworks" element={<RequireAuth permissions={[permissions.artworkView]}><MobileArtworksPage /></RequireAuth>} />
      <Route path="/m/artworks/:id" element={<RequireAuth permissions={[permissions.artworkView]}><MobileArtworkDetailPage /></RequireAuth>} />
      <Route path="/m/artworks/:id/images/:kind" element={<RequireAuth permissions={[permissions.artworkImageView]}><MobileArtworkImageViewerPage /></RequireAuth>} />
      <Route path="/m/profile" element={<RequireAuth><MobileProfilePage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/m/artworks" replace />} />
    </Routes>
  )
}
