export type TaskType = 'SEARCH' | 'SEARCH_BATCH' | 'ORIGINAL_IMAGE' | 'HD_IMAGE' | 'TRANSACTION_PRICE';
export type TaskStatus = 'PENDING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface Task {
  id: number;
  name: string;
  keyword: string;
  taskType: TaskType;
  parentTaskId?: number | null;
  parentTaskName?: string;
  targetTaskId?: number | null;
  targetTaskName?: string;
  status: TaskStatus;
  currentPage: number;
  totalPages: number;
  totalFetched: number;
  artworkCount: number;
  pendingFailureCount: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
  detailFetchConcurrency: number;
  detailRequestCount: number;
  detailSuccessCount: number;
  detailFailureCount: number;
  avgDetailLatencyMs: number;
  p95DetailLatencyMs: number;
  maxDetailLatencyMs: number;
  lastPageDurationMs: number;
  lastPageItemsPerMinute: number;
  detailFailureRate: number;
  concurrencyAdvice?: string;
  estimatedRemainingMs?: number | null;
}

export interface Artwork {
  id: number;
  taskId: number;
  taskName: string;
  externalId?: string;
  title: string;
  lotNumber?: string;      // 拍品编号
  artist?: string;         // 作者
  medium?: string;         // 材质
  format?: string;         // 形制
  dimensions?: string;     // 尺寸
  valuation?: string;      // 估价
  transactionPrice?: string; // 成交价
  transactionPriceNote?: string; // 未拿到成交价时的原因
  auctionHouse?: string;   // 拍卖公司
  auctionName?: string;    // 拍卖会
  auctionSession?: string; // 拍卖专场
  auctionDate?: string;    // 拍卖日期
  auctionLocation?: string;// 拍卖地点
  previewTime?: string;    // 预展时间
  previewLocation?: string;// 预展地点
  description?: string;
  imageUrl?: string;
  originalImageSourceUrl?: string;
  originalImageStatus?: 'MISSING' | 'DOWNLOADED' | 'FAILED';
  originalImageAvailable: boolean;
  hdImageSourceUrl?: string;
  hdImageStatus?: 'MISSING' | 'DOWNLOADED' | 'FAILED';
  hdImageAvailable: boolean;
  hdImageLastError?: string;
  sourceUrl?: string;
  createdAt: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface CurrentUser {
  id: number;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
}

export interface LoginResponse {
  tokenName: string;
  tokenValue: string;
  tokenPrefix: string;
  expiresIn: number;
  user: CurrentUser;
}

export type UserStatus = 'ENABLED' | 'DISABLED';

export interface AuthUser {
  id: number;
  username: string;
  displayName: string;
  email?: string;
  phone?: string;
  status: UserStatus;
  roles: string[];
  lastLoginAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuthRole {
  id: number;
  code: string;
  name: string;
  description?: string;
  enabled: boolean;
  builtIn: boolean;
  permissions: string[];
  createdAt: string;
  updatedAt: string;
}

export interface AuthPermission {
  id: number;
  code: string;
  name: string;
  module: string;
  resourceType: 'MENU' | 'BUTTON' | 'API' | 'DATA';
  description?: string;
  enabled: boolean;
  builtIn: boolean;
}

export interface AuditLog {
  id: number;
  userId?: number;
  username?: string;
  action: string;
  resourceType?: string;
  resourceId?: string;
  description?: string;
  ipAddress?: string;
  userAgent?: string;
  success: boolean;
  errorMessage?: string;
  createdAt: string;
}

export type EvaluationProjectStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'READY_FOR_REVIEW'
  | 'IN_REVIEW'
  | 'REVIEW_REJECTED'
  | 'COMPLETED'
  | 'CANCELLED'

export type ExpertReviewStatus =
  | 'NOT_STARTED'
  | 'DRAFT'
  | 'SUBMITTED'
  | 'REVIEW_REJECTED'
  | 'RESUBMITTED'

export interface CriterionItem {
  fieldName: string
  fieldLabel: string
  operator: string
  value?: string
  valueTo?: string
  valueType?: string
}

export interface MetricConfig {
  id?: number
  sourceMetricDefinitionId?: number
  sourceTemplateId?: number
  sourceVersion?: number
  code: string
  name: string
  description?: string
  category?: string
  scoreType?: string
  minScore?: number
  maxScore?: number
  scoreStep?: number
  weight?: number
  required: boolean
  inputComponent?: string
  optionValues?: string
  scoringGuide?: string
  scoringRubric?: string
  sortOrder: number
}

export interface EvaluationMetricDefinition {
  id: number
  code: string
  name: string
  description?: string
  category?: string
  applicableArtworkTypes?: string
  scoreType?: string
  minScore?: number
  maxScore?: number
  scoreStep?: number
  defaultWeight?: number
  required: boolean
  inputComponent?: string
  optionValues?: string
  scoringGuide?: string
  scoringRubric?: string
  unit?: string
  tags?: string
  enabled: boolean
  sortOrder: number
  version: number
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface EvaluationMetricTemplate {
  id: number
  name: string
  description?: string
  enabled: boolean
  itemCount: number
  items: MetricConfig[]
  createdAt: string
  updatedAt: string
}

export interface EvaluationProjectExpert {
  id: number
  expertId: number
  expertName: string
  status: string
  completedCount: number
  totalCount: number
  rejectedCount: number
}

export interface EvaluationArtworkItem {
  id: number
  artworkId: number
  status: string
  artwork: Artwork
}

export interface EvaluationProjectListItem {
  id: number
  name: string
  description?: string
  status: EvaluationProjectStatus
  artworkCount: number
  expertCount: number
  expectedReviewCount: number
  completedCount: number
  rejectedReviewCount: number
  auditorName?: string
  experts: string[]
  createdAt: string
  updatedAt: string
}

export interface EvaluationProject {
  id: number
  name: string
  description?: string
  status: EvaluationProjectStatus
  auditorId?: number
  auditorName?: string
  criteria: CriterionItem[]
  artworkCount: number
  expertCount: number
  expectedReviewCount: number
  completedCount: number
  rejectedReviewCount: number
  submittedForReviewAt?: string
  reviewedAt?: string
  auditResult?: 'APPROVED' | 'REJECTED'
  auditComment?: string
  configLockedAt?: string
  createdAt: string
  updatedAt: string
  completedAt?: string
  experts: EvaluationProjectExpert[]
  artworks: EvaluationArtworkItem[]
  metrics: MetricConfig[]
}

export interface ArtworkPreview {
  id: number
  title: string
  artist?: string
  lotNumber?: string
  medium?: string
  valuation?: string
  auctionHouse?: string
  auctionDate?: string
  imageUrl?: string
}

export interface ExpertReviewScore {
  id?: number
  projectMetricId: number
  score?: number
  optionValue?: string
  textValue?: string
  comment?: string
}

export interface ExpertReview {
  id: number
  evaluationId: number
  artworkId: number
  expertId: number
  expertName: string
  finalEstimate?: string
  finalEstimateCurrency?: string
  comment?: string
  status: ExpertReviewStatus
  rejectedReason?: string
  rejectedAt?: string
  resubmittedAt?: string
  submittedAt?: string
  scores: ExpertReviewScore[]
}

export interface ExpertReviewForm {
  evaluationId: number
  evaluationName: string
  evaluationStatus: EvaluationProjectStatus
  artwork: Artwork
  metrics: MetricConfig[]
  review: ExpertReview
}

export interface ArtworkReviewSummary {
  artwork: Artwork
  reviews: ExpertReview[]
}

export interface EvaluationAuditRecord {
  id: number
  evaluationId: number
  expertReviewId?: number
  artworkId?: number
  expertId?: number
  expertName?: string
  auditorId?: number
  auditorName?: string
  result?: 'APPROVED' | 'REJECTED'
  comment?: string
  action?: string
  previousStatus?: string
  nextStatus?: string
  createdAt: string
}
