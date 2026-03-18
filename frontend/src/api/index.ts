import axios from 'axios';
import type { Artwork, PageResult, Task } from '../types';

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const msg = err.response?.data?.error || err.message || '请求失败';
    return Promise.reject(new Error(msg));
  }
);

// Tasks
export const createTask = (data: { name: string; keyword: string }) =>
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
  page?: number;
  size?: number;
}

export const listArtworks = (query: ArtworkQuery = {}) =>
  api.get<PageResult<Artwork>>('/artworks', { params: query }).then((r) => r.data);

export const getArtwork = (id: number) =>
  api.get<Artwork>(`/artworks/${id}`).then((r) => r.data);

export const exportArtworksUrl = (query: Omit<ArtworkQuery, 'page' | 'size'>) => {
  const params = new URLSearchParams();
  if (query.taskId) params.set('taskId', String(query.taskId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.artist) params.set('artist', query.artist);
  if (query.auctionDate) params.set('auctionDate', query.auctionDate);
  if (query.lotNumber) params.set('lotNumber', query.lotNumber);
  return `/api/artworks/export?${params.toString()}`;
};
