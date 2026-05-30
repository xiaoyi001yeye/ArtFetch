import axios from 'axios';
import type {
  Artwork,
  ArtworkPreview,
  ArtworkReviewSummary,
  AuditLog,
  AuthPermission,
  AuthRole,
  AuthUser,
  CurrentUser,
  CriterionItem,
  EvaluationAuditRecord,
  EvaluationMetricDefinition,
  EvaluationMetricTemplate,
  EvaluationProject,
  EvaluationProjectListItem,
  ExpertReview,
  ExpertReviewForm,
  MetricConfig,
  LoginResponse,
  HdImageMigrationItem,
  HdImageMigrationItemStatus,
  HdImageMigrationTask,
  HdImageMigrationMode,
  HdImageMigrationScopeType,
  ObjectStorageConfig,
  ObjectStorageConfigPayload,
  PageResult,
  Task,
  TaskType,
  UserStatus,
} from '../types';

export type HdImageSyncStatus = 'SYNCED' | 'UNSYNCED' | 'NO_PERMISSION' | 'FAILED';

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

export const AUTH_TOKEN_STORAGE_KEY = 'artfetch.auth.token';
export const getStoredToken = () => localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
export const setStoredToken = (token: string) => localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token);
export const clearStoredToken = () => localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);

api.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      clearStoredToken();
      window.dispatchEvent(new Event('artfetch:unauthorized'));
    }
    const msg = err.response?.data?.message || err.response?.data?.error || err.message || '请求失败';
    return Promise.reject(new Error(msg));
  }
);

// Auth
export const login = (data: { username: string; password: string }) =>
  api.post<LoginResponse>('/auth/login', data).then((r) => r.data);

export const logout = () =>
  api.post('/auth/logout').then((r) => r.data);

export const getCurrentUser = () =>
  api.get<CurrentUser>('/auth/me').then((r) => r.data);

export const changePassword = (data: { oldPassword: string; newPassword: string }) =>
  api.post('/auth/change-password', data).then((r) => r.data);

// Users / roles / permissions
export const listUsers = (page = 0, size = 20) =>
  api.get<PageResult<AuthUser>>('/users', { params: { page, size } }).then((r) => r.data);

export const createUser = (data: { username: string; password: string; displayName: string; email?: string; phone?: string; roles: string[] }) =>
  api.post<AuthUser>('/users', data).then((r) => r.data);

export const updateUser = (id: number, data: { displayName: string; email?: string; phone?: string }) =>
  api.put<AuthUser>(`/users/${id}`, data).then((r) => r.data);

export const updateUserStatus = (id: number, status: UserStatus) =>
  api.put<AuthUser>(`/users/${id}/status`, { status }).then((r) => r.data);

export const resetUserPassword = (id: number, newPassword: string) =>
  api.post(`/users/${id}/reset-password`, { newPassword }).then((r) => r.data);

export const updateUserRoles = (id: number, roles: string[]) =>
  api.put<AuthUser>(`/users/${id}/roles`, { roles }).then((r) => r.data);

export const listRoles = (page = 0, size = 100) =>
  api.get<PageResult<AuthRole>>('/roles', { params: { page, size } }).then((r) => r.data);

export const createRole = (data: { code: string; name: string; description?: string; permissions: string[] }) =>
  api.post<AuthRole>('/roles', data).then((r) => r.data);

export const updateRole = (id: number, data: { name: string; description?: string }) =>
  api.put<AuthRole>(`/roles/${id}`, data).then((r) => r.data);

export const updateRoleStatus = (id: number, enabled: boolean) =>
  api.put<AuthRole>(`/roles/${id}/status`, { enabled }).then((r) => r.data);

export const updateRolePermissions = (id: number, permissions: string[]) =>
  api.put<AuthRole>(`/roles/${id}/permissions`, { permissions }).then((r) => r.data);

export const listPermissions = () =>
  api.get<AuthPermission[]>('/permissions').then((r) => r.data);

export const listAuditLogs = (query: { username?: string; action?: string; success?: boolean; page?: number; size?: number }) =>
  api.get<PageResult<AuditLog>>('/audit-logs', { params: query }).then((r) => r.data);

// Object storage settings
export const listObjectStorageConfigs = () =>
  api.get<ObjectStorageConfig[]>('/settings/object-storage').then((r) => r.data);

export const getObjectStorageConfigForEdit = (id: number) =>
  api.get<ObjectStorageConfig>(`/settings/object-storage/${id}/edit`).then((r) => r.data);

export const createObjectStorageConfig = (data: ObjectStorageConfigPayload) =>
  api.post<ObjectStorageConfig>('/settings/object-storage', data).then((r) => r.data);

export const updateObjectStorageConfig = (id: number, data: ObjectStorageConfigPayload) =>
  api.put<ObjectStorageConfig>(`/settings/object-storage/${id}`, data).then((r) => r.data);

export const testObjectStorageConfig = (id: number) =>
  api.post<{ success: boolean; message: string }>(`/settings/object-storage/${id}/test`).then((r) => r.data);

export const enableObjectStorageConfig = (id: number) =>
  api.post<ObjectStorageConfig>(`/settings/object-storage/${id}/enable`).then((r) => r.data);

export const disableObjectStorageConfig = (id: number) =>
  api.post<ObjectStorageConfig>(`/settings/object-storage/${id}/disable`).then((r) => r.data);

// HD image migrations
export const listHdImageMigrations = (page = 0, size = 20) =>
  api.get<PageResult<HdImageMigrationTask>>('/hd-image-migrations', { params: { page, size } }).then((r) => r.data);

export const createHdImageMigration = (data: {
  name: string
  configId: number
  mode: HdImageMigrationMode
  scopeType: HdImageMigrationScopeType
  targetTaskId?: number
  uploadConcurrency?: number
}) => api.post<HdImageMigrationTask>('/hd-image-migrations', data).then((r) => r.data);

export const getHdImageMigration = (id: number) =>
  api.get<HdImageMigrationTask>(`/hd-image-migrations/${id}`).then((r) => r.data);

export const startHdImageMigration = (id: number) =>
  api.post<HdImageMigrationTask>(`/hd-image-migrations/${id}/start`).then((r) => r.data);

export const pauseHdImageMigration = (id: number) =>
  api.post<HdImageMigrationTask>(`/hd-image-migrations/${id}/pause`).then((r) => r.data);

export const resumeHdImageMigration = (id: number) =>
  api.post<HdImageMigrationTask>(`/hd-image-migrations/${id}/resume`).then((r) => r.data);

export const cancelHdImageMigration = (id: number) =>
  api.post<HdImageMigrationTask>(`/hd-image-migrations/${id}/cancel`).then((r) => r.data);

export const retryFailedHdImageMigration = (id: number) =>
  api.post<HdImageMigrationTask>(`/hd-image-migrations/${id}/retry-failed`).then((r) => r.data);

export const listHdImageMigrationItems = (id: number, query: { status?: HdImageMigrationItemStatus; page?: number; size?: number }) =>
  api.get<PageResult<HdImageMigrationItem>>(`/hd-image-migrations/${id}/items`, { params: query }).then((r) => r.data);

// Evaluation metrics / templates
export const listEvaluationMetrics = (page = 0, size = 100, keyword?: string) =>
  api.get<PageResult<EvaluationMetricDefinition>>('/evaluation-metrics', { params: { page, size, keyword } }).then((r) => r.data);

export const listEnabledEvaluationMetrics = () =>
  api.get<EvaluationMetricDefinition[]>('/evaluation-metrics/enabled').then((r) => r.data);

export const createEvaluationMetric = (data: Partial<EvaluationMetricDefinition>) =>
  api.post<EvaluationMetricDefinition>('/evaluation-metrics', data).then((r) => r.data);

export const updateEvaluationMetric = (id: number, data: Partial<EvaluationMetricDefinition>) =>
  api.put<EvaluationMetricDefinition>(`/evaluation-metrics/${id}`, data).then((r) => r.data);

export const deleteEvaluationMetric = (id: number) =>
  api.delete(`/evaluation-metrics/${id}`).then((r) => r.data);

export const listEvaluationMetricTemplates = (page = 0, size = 100) =>
  api.get<PageResult<EvaluationMetricTemplate>>('/evaluation-metric-templates', { params: { page, size } }).then((r) => r.data);

export const getEvaluationMetricTemplate = (id: number) =>
  api.get<EvaluationMetricTemplate>(`/evaluation-metric-templates/${id}`).then((r) => r.data);

export const getEvaluationMetricTemplateItems = (id: number) =>
  api.get<MetricConfig[]>(`/evaluation-metric-templates/${id}/items`).then((r) => r.data);

export const createEvaluationMetricTemplate = (data: { name: string; description?: string; enabled?: boolean; items: MetricConfig[] }) =>
  api.post<EvaluationMetricTemplate>('/evaluation-metric-templates', data).then((r) => r.data);

export const updateEvaluationMetricTemplate = (id: number, data: { name: string; description?: string; enabled?: boolean; items: MetricConfig[] }) =>
  api.put<EvaluationMetricTemplate>(`/evaluation-metric-templates/${id}`, data).then((r) => r.data);

export const deleteEvaluationMetricTemplate = (id: number) =>
  api.delete(`/evaluation-metric-templates/${id}`).then((r) => r.data);

// Evaluations
export const listEvaluations = (page = 0, size = 20) =>
  api.get<PageResult<EvaluationProjectListItem>>('/evaluations', { params: { page, size } }).then((r) => r.data);

export const listAssignedEvaluations = (page = 0, size = 20) =>
  api.get<PageResult<EvaluationProjectListItem>>('/evaluations/assigned', { params: { page, size } }).then((r) => r.data);

export const previewEvaluationArtworks = (data: { criteria: CriterionItem[]; page?: number; size?: number }) =>
  api.post<PageResult<ArtworkPreview>>('/evaluations/preview-artworks', data).then((r) => r.data);

export const createEvaluation = (data: {
  name: string
  description?: string
  auditorId: number
  criteria: CriterionItem[]
  artworkIds: number[]
  expertIds: number[]
  metrics: MetricConfig[]
}) => api.post<EvaluationProject>('/evaluations', data).then((r) => r.data);

export const getEvaluation = (id: number) =>
  api.get<EvaluationProject>(`/evaluations/${id}`).then((r) => r.data);

export const updateEvaluation = (id: number, data: {
  name: string
  description?: string
  auditorId: number
  criteria: CriterionItem[]
  artworkIds?: number[]
  expertIds?: number[]
  metrics?: MetricConfig[]
}) => api.put<EvaluationProject>(`/evaluations/${id}`, data).then((r) => r.data);

export const deleteEvaluation = (id: number) =>
  api.delete(`/evaluations/${id}`).then((r) => r.data);

export const publishEvaluation = (id: number) =>
  api.post<EvaluationProject>(`/evaluations/${id}/publish`).then((r) => r.data);

export const listEvaluationMetricsForProject = (id: number) =>
  api.get<MetricConfig[]>(`/evaluations/${id}/metrics`).then((r) => r.data);

export const listEvaluationArtworks = (id: number) =>
  api.get<EvaluationProject['artworks']>(`/evaluations/${id}/artworks`).then((r) => r.data);

export const listEvaluationExperts = (id: number) =>
  api.get<EvaluationProject['experts']>(`/evaluations/${id}/experts`).then((r) => r.data);

export const submitEvaluationReview = (id: number) =>
  api.post<EvaluationProject>(`/evaluations/${id}/submit-review`).then((r) => r.data);

export const listEvaluationAuditRecords = (id: number) =>
  api.get<EvaluationAuditRecord[]>(`/evaluations/${id}/audit-records`).then((r) => r.data);

export const approveEvaluation = (id: number, comment?: string) =>
  api.post<EvaluationProject>(`/evaluations/${id}/audit/approve`, { comment }).then((r) => r.data);

export const rejectEvaluationReview = (id: number, reviewId: number, reason: string) =>
  api.post<EvaluationProject>(`/evaluations/${id}/expert-reviews/${reviewId}/audit/reject`, { reason }).then((r) => r.data);

// Expert reviews
export const getMyExpertReview = (evaluationId: number, artworkId: number) =>
  api.get<ExpertReviewForm>(`/evaluations/${evaluationId}/artworks/${artworkId}/my-review`).then((r) => r.data);

export const saveMyExpertReview = (evaluationId: number, artworkId: number, data: {
  finalEstimate?: string
  finalEstimateCurrency?: string
  comment?: string
  scores: ExpertReview['scores']
}) => api.put<ExpertReview>(`/evaluations/${evaluationId}/artworks/${artworkId}/my-review`, data).then((r) => r.data);

export const submitMyExpertReview = (evaluationId: number, artworkId: number, data: {
  finalEstimate?: string
  finalEstimateCurrency?: string
  comment?: string
  scores: ExpertReview['scores']
}) => api.post<ExpertReview>(`/evaluations/${evaluationId}/artworks/${artworkId}/my-review/submit`, data).then((r) => r.data);

export const getArtworkReviewSummary = (evaluationId: number, artworkId: number) =>
  api.get<ArtworkReviewSummary>(`/evaluations/${evaluationId}/artworks/${artworkId}/reviews`).then((r) => r.data);

// Tasks
export const createTask = (data: { name: string; keyword?: string; keywords?: string[]; taskType: TaskType; targetTaskId?: number }) =>
  api.post<Task>('/tasks', data).then((r) => r.data);

export const listTasks = (page = 0, size = 20) =>
  api.get<PageResult<Task>>('/tasks', { params: { page, size } }).then((r) => r.data);

export const getTask = (id: number) =>
  api.get<Task>(`/tasks/${id}`).then((r) => r.data);

export const startTask = (id: number) =>
  api.post<Task>(`/tasks/${id}/start`).then((r) => r.data);

export const pauseTask = (id: number) =>
  api.post<Task>(`/tasks/${id}/pause`).then((r) => r.data);

export const resumeTask = (id: number) =>
  api.post<Task>(`/tasks/${id}/resume`).then((r) => r.data);

export const cancelTask = (id: number) =>
  api.post<Task>(`/tasks/${id}/cancel`).then((r) => r.data);

export const deleteTask = (id: number) =>
  api.delete(`/tasks/${id}`).then((r) => r.data);

// Artworks
export interface ArtworkQuery {
  taskId?: number;
  keyword?: string;
  artist?: string;
  auctionDate?: string;
  lotNumber?: string;
  hdImageSyncStatus?: HdImageSyncStatus;
  page?: number;
  size?: number;
}

export const listArtworks = (query: ArtworkQuery = {}) =>
  api.get<PageResult<Artwork>>('/artworks', { params: query }).then((r) => r.data);

export const getArtwork = (id: number) =>
  api.get<Artwork>(`/artworks/${id}`).then((r) => r.data);

export const originalImageViewUrl = (id: number) =>
  `/api/artworks/${id}/original-image`;

export const hdImageViewUrl = (id: number) =>
  `/api/artworks/${id}/hd-image`;

export const redownloadOriginalImage = (id: number) =>
  api.post<Artwork>(`/artworks/${id}/original-image/redownload`).then((r) => r.data);

export const redownloadHdImage = (id: number) =>
  api.post<Artwork>(`/artworks/${id}/hd-image/redownload`).then((r) => r.data);

export const supplementTransactionPrice = (id: number) =>
  api.post<Artwork>(`/artworks/${id}/transaction-price/supplement`).then((r) => r.data);

export const exportArtworksUrl = (query: Omit<ArtworkQuery, 'page' | 'size'>) => {
  const params = new URLSearchParams();
  if (query.taskId) params.set('taskId', String(query.taskId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.artist) params.set('artist', query.artist);
  if (query.auctionDate) params.set('auctionDate', query.auctionDate);
  if (query.lotNumber) params.set('lotNumber', query.lotNumber);
  if (query.hdImageSyncStatus) params.set('hdImageSyncStatus', query.hdImageSyncStatus);
  return `/api/artworks/export?${params.toString()}`;
};

export const downloadArtworksExport = async (query: Omit<ArtworkQuery, 'page' | 'size'>) => {
  const response = await api.get<Blob>('/artworks/export', {
    params: query,
    responseType: 'blob',
  });
  const disposition = response.headers['content-disposition'] || '';
  const filenameMatch = disposition.match(/filename\*=UTF-8''([^;]+)/);
  const filename = filenameMatch ? decodeURIComponent(filenameMatch[1]) : `artworks_${Date.now()}.xlsx`;
  const objectUrl = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
};

export const createProtectedBlobUrl = async (url: string) => {
  const response = await api.get<Blob>(url.replace(/^\/api/, ''), { responseType: 'blob' });
  return URL.createObjectURL(response.data);
};

export const openProtectedBlob = async (url: string) => {
  const objectUrl = await createProtectedBlobUrl(url);
  window.open(objectUrl, '_blank', 'noopener,noreferrer');
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
};
