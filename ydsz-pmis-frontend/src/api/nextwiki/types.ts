/**
 * @file 网盘知识库类型定义
 * @description 定义文件节点、版本、分享链接、存储配额、标签、回收站、搜索结果等数据结构。
 *              所有 ID 均为 string 类型（后端 VARCHAR(20) 雪花算法字符串）。
 * @module api/nextwiki/types
 */

// ============= 文件管理 =============

/** 文件节点视图对象 */
export interface FileNodeVO {
  id: string
  parentId: string
  name: string
  nodeType: 'folder' | 'file'
  suffix?: string
  size: number
  storageKey?: string
  mimeType?: string
  path: string
  starred: boolean
  shareStatus: string
  previewReady: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
}

/** 文件版本视图对象 */
export interface FileVersionVO {
  id: string
  fileNodeId: string
  versionNumber: number
  size: number
  remark?: string
  isActive: boolean
  createdAt: string
  createdBy: string
}

/** 新建文件夹请求 */
export interface CreateFolderDTO {
  parentId: string
  name: string
}

/** 移动文件请求 */
export interface MoveFileDTO {
  targetParentId: string
}

/** 重命名请求 */
export interface RenameDTO {
  newName: string
}

/** 批量删除请求 */
export interface BatchDeleteDTO {
  ids: string[]
}

/** 批量移动请求 */
export interface BatchMoveDTO {
  ids: string[]
  targetParentId: string
}

/** 收藏状态变更请求 */
export interface StarDTO {
  starred: boolean
}

/** 版本回滚请求 */
export interface RollbackVersionDTO {
  versionNumber: number
}

// ============= 分享 =============

/** 分享链接视图对象 */
export interface ShareLinkVO {
  id: string
  fileNodeId: string
  shareCode: string
  shareType: 'view' | 'download' | 'edit'
  expireTime?: string
  maxAccessCount?: number
  accessCount: number
  status: 'active' | 'expired' | 'revoked'
  hasPassword: boolean
  createdAt: string
}

/** 创建分享请求 */
export interface CreateShareDTO {
  fileNodeId: string
  shareType: 'view' | 'download' | 'edit'
  expireTime?: string
  maxAccessCount?: number
  password?: string
}

/** 验证分享请求 */
export interface VerifyShareDTO {
  shareCode: string
  password?: string
}

// ============= 预览 =============

/** 文件预览视图对象 */
export interface FilePreviewVO {
  fileNodeId: string
  previewType: 'image' | 'pdf' | 'text' | 'video' | 'unsupported'
  previewUrl?: string
  thumbnailUrl?: string
  content?: string
  mimeType?: string
  ready: boolean
}

// ============= 配额 =============

/** 存储配额视图对象 */
export interface StorageQuotaVO {
  id: string
  scopeType: 'user' | 'tenant' | 'project'
  scopeId: string
  quotaLimit: number
  quotaUsed: number
  fileCountLimit: number
  fileCountUsed: number
}

/** 设置配额请求 */
export interface SetQuotaDTO {
  scopeType: 'user' | 'tenant' | 'project'
  scopeId: string
  quotaLimit: number
  fileCountLimit?: number
}

// ============= 标签 =============

/** 标签视图对象 */
export interface TagVO {
  id: string
  name: string
  color: string
  usageCount: number
}

/** 创建标签请求 */
export interface CreateTagDTO {
  name: string
  color: string
}

/** 绑定标签请求 */
export interface BindTagDTO {
  tagId: string
  fileNodeId: string
}

// ============= 回收站 =============

/** 回收站条目视图对象 */
export interface TrashItemVO {
  id: string
  fileNodeId: string
  originalName: string
  originalPath: string
  nodeType: 'folder' | 'file'
  size: number
  deletedTime: string
  purgeTime: string
  status: 'in_trash' | 'restored' | 'purged'
}

// ============= 搜索 =============

/** 搜索结果视图对象 */
export interface SearchResultVO {
  fileNodeId: string
  name: string
  path: string
  snippet?: string
  size: number
  mimeType?: string
}

/** 文件列表查询参数 */
export interface FileListQuery {
  parentId?: string
  keyword?: string
  nodeType?: 'folder' | 'file'
  starred?: boolean
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}
