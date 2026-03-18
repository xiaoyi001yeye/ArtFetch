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
  lotNumber?: string;      // 拍品编号
  artist?: string;         // 作者
  medium?: string;         // 材质
  format?: string;         // 形制
  dimensions?: string;     // 尺寸
  valuation?: string;      // 估价
  auctionHouse?: string;   // 拍卖公司
  auctionName?: string;    // 拍卖会
  auctionSession?: string; // 拍卖专场
  auctionDate?: string;    // 拍卖日期
  auctionLocation?: string;// 拍卖地点
  previewTime?: string;    // 预展时间
  previewLocation?: string;// 预展地点
  description?: string;
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
