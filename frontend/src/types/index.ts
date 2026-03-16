export type TaskStatus = 'PENDING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface Task {
  id: number;
  name: string;
  keyword: string;
  status: TaskStatus;
  currentPage: number;
  totalPages: number;
  totalFetched: number;
  artworkCount: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Artwork {
  id: number;
  taskId: number;
  taskName: string;
  externalId?: string;
  title: string;
  artist?: string;
  medium?: string;
  format?: string;
  dimensions?: string;
  valuation?: string;
  year?: string;       // 拍卖日期
  collection?: string; // 拍卖公司
  auctionName?: string;
  auctionSession?: string;
  auctionLocation?: string;
  previewTime?: string;
  previewLocation?: string;
  description?: string;
  category?: string;
  imageUrl?: string;
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
