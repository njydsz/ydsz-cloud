/**
 * @file 网盘知识库模块 Mock 数据处理器
 * @description 为 nextwiki 文件管理、分享、回收站、配额、搜索、标签等 API 路径提供 Mock 数据。
 *              所有 ID 均为 string 类型（模拟后端雪花算法字符串）。
 * @module mock/handlers/nextwiki
 */
import type { MockHandler } from './types'

/** 生成雪花 ID 模拟值 */
function genId(prefix: string, idx: number): string {
  return `${prefix}${String(Date.now()).slice(-6)}${String(idx).padStart(4, '0')}`
}

/** 模拟文件列表数据 */
const mockFileList = Array.from({ length: 20 }, (_, i) => {
  const isFolder = i % 3 === 0
  return {
    id: genId('fn', i + 1),
    parentId: i < 5 ? '0' : genId('fn', (i % 5) + 1),
    name: isFolder ? `文件夹${i + 1}` : `文档${i + 1}.${['pdf', 'docx', 'xlsx', 'png', 'mp4'][i % 5]}`,
    nodeType: isFolder ? 'folder' : 'file',
    suffix: isFolder ? undefined : ['pdf', 'docx', 'xlsx', 'png', 'mp4'][i % 5],
    size: isFolder ? 0 : (i + 1) * 102400,
    mimeType: isFolder ? undefined : ['application/pdf', 'application/msword', 'application/vnd.ms-excel', 'image/png', 'video/mp4'][i % 5],
    path: `/全部文件/文件夹${(i % 5) + 1}`,
    starred: i % 4 === 0,
    shareStatus: i % 5 === 0 ? 'shared' : 'none',
    previewReady: !isFolder && i % 3 !== 0,
    createdAt: '2026-07-10 10:00:00',
    updatedAt: '2026-07-14 09:30:00',
    createdBy: 'user001',
  }
})

/** 模拟分享列表数据 */
const mockShareList = Array.from({ length: 10 }, (_, i) => ({
  id: genId('sl', i + 1),
  fileNodeId: genId('fn', i + 1),
  shareCode: `SHARE${String(i + 1).padStart(6, '0')}`,
  shareType: (['view', 'download', 'edit'] as const)[i % 3],
  expireTime: i % 3 === 0 ? undefined : '2026-12-31 23:59:59',
  maxAccessCount: i % 2 === 0 ? 100 : undefined,
  accessCount: (i + 1) * 5,
  status: (['active', 'expired', 'revoked'] as const)[i % 3],
  hasPassword: i % 2 === 1,
  createdAt: '2026-07-01 12:00:00',
}))

/** 模拟回收站数据 */
const mockTrashList = Array.from({ length: 8 }, (_, i) => ({
  id: genId('tr', i + 1),
  fileNodeId: genId('fn', i + 1),
  originalName: i % 2 === 0 ? `已删除文件${i + 1}.pdf` : `已删除文件夹${i + 1}`,
  originalPath: `/全部文件/文件夹${(i % 3) + 1}`,
  nodeType: i % 2 === 0 ? 'file' : 'folder',
  size: (i + 1) * 51200,
  deletedTime: '2026-07-12 15:30:00',
  purgeTime: '2026-07-27 15:30:00',
  status: 'in_trash' as const,
}))

/** 模拟标签数据 */
const mockTagList = [
  { id: 'tag001', name: '重要', color: '#F56C6C', usageCount: 12 },
  { id: 'tag002', name: '项目文档', color: '#409EFF', usageCount: 8 },
  { id: 'tag003', name: '合同', color: '#E6A23C', usageCount: 5 },
  { id: 'tag004', name: '设计稿', color: '#67C23A', usageCount: 3 },
  { id: 'tag005', name: '已归档', color: '#909399', usageCount: 15 },
]

/**
 * 网盘知识库模块 Mock 处理器集合
 * @returns 覆盖文件列表、上传、分享、回收站、配额、搜索、标签等接口的 Mock 处理器
 */
export const nextwikiHandlers: MockHandler[] = [
  // ===== 文件列表查询 =====
  {
    method: 'GET',
    path: '/nextwiki/files/list',
    handler: ({ query }) => {
      const starred = query.starred === 'true'
      const keyword = query.keyword || ''
      let list = [...mockFileList]
      if (starred) list = list.filter((f) => f.starred)
      if (keyword) list = list.filter((f) => f.name.includes(keyword))
      return {
        list,
        total: list.length,
        page: Number(query.page || 1),
        size: Number(query.size || 10),
        pages: Math.ceil(list.length / Number(query.size || 10)),
      }
    },
  },
  // ===== 上传文件 =====
  {
    method: 'POST',
    path: '/nextwiki/files/upload',
    handler: () => genId('fn', Date.now()),
  },
  // ===== 新建文件夹 =====
  {
    method: 'POST',
    path: '/nextwiki/files/folders',
    handler: () => genId('fn', Date.now()),
  },
  // ===== 分享列表 =====
  {
    method: 'GET',
    path: '/nextwiki/share/list',
    handler: ({ query }) => {
      const page = Number(query.page || 1)
      const size = Number(query.size || 10)
      return {
        list: mockShareList.slice((page - 1) * size, page * size),
        total: mockShareList.length,
        page,
        size,
        pages: Math.ceil(mockShareList.length / size),
      }
    },
  },
  // ===== 创建分享 =====
  {
    method: 'POST',
    path: '/nextwiki/share/create',
    handler: ({ body }) => {
      const data = body as { fileNodeId: string; shareType: string }
      return {
        id: genId('sl', Date.now()),
        fileNodeId: data.fileNodeId,
        shareCode: `SHARE${String(Date.now()).slice(-6)}`,
        shareType: data.shareType,
        expireTime: undefined,
        maxAccessCount: undefined,
        accessCount: 0,
        status: 'active',
        hasPassword: false,
        createdAt: '2026-07-14 10:00:00',
      }
    },
  },
  // ===== 回收站列表 =====
  {
    method: 'GET',
    path: '/nextwiki/trash/list',
    handler: ({ query }) => {
      const page = Number(query.page || 1)
      const size = Number(query.size || 10)
      return {
        list: mockTrashList.slice((page - 1) * size, page * size),
        total: mockTrashList.length,
        page,
        size,
        pages: Math.ceil(mockTrashList.length / size),
      }
    },
  },
  // ===== 配额信息 =====
  {
    method: 'GET',
    path: '/nextwiki/quota/info',
    handler: () => ({
      id: 'qta001',
      scopeType: 'user',
      scopeId: 'user001',
      quotaLimit: 10737418240,
      quotaUsed: 3221225472,
      fileCountLimit: 10000,
      fileCountUsed: 156,
    }),
  },
  // ===== 搜索 =====
  {
    method: 'GET',
    path: '/nextwiki/search',
    handler: ({ query }) => {
      const keyword = query.keyword || ''
      const results = keyword
        ? mockFileList
            .filter((f) => f.name.includes(keyword))
            .map((f) => ({
              fileNodeId: f.id,
              name: f.name,
              path: f.path,
              snippet: `...包含"${keyword}"的文件内容片段...`,
              size: f.size,
              mimeType: f.mimeType,
            }))
        : []
      return { list: results, total: results.length }
    },
  },
  // ===== 标签列表 =====
  {
    method: 'GET',
    path: '/nextwiki/tag/list',
    handler: () => mockTagList,
  },
  // ===== 文件版本列表 =====
  {
    method: 'GET',
    path: '/nextwiki/files/{id}/versions',
    handler: () =>
      Array.from({ length: 3 }, (_, i) => ({
        id: `ver${String(i + 1).padStart(3, '0')}`,
        fileNodeId: 'fn0001',
        versionNumber: 3 - i,
        size: (3 - i) * 102400,
        remark: i === 0 ? undefined : `v${3 - i} 修订`,
        isActive: i === 0,
        createdAt: '2026-07-14 09:30:00',
        createdBy: 'user001',
      })),
  },
  // ===== 文件预览 =====
  {
    method: 'GET',
    path: '/nextwiki/preview/{id}',
    handler: () => ({
      fileNodeId: 'fn0001',
      previewType: 'image',
      previewUrl: 'https://picsum.photos/800/600',
      thumbnailUrl: 'https://picsum.photos/200/150',
      content: undefined,
      mimeType: 'image/png',
      ready: true,
    }),
  },
]
