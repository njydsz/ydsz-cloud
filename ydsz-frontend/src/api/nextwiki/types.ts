/**
 * @file 网盘知识库类型定义
 * @description 定义文件节点、版本、分享链接、存储配额、标签、回收站、搜索结果等数据结构。
 *              所有 ID 均为 string 类型（后端 VARCHAR(20) 雪花算法字符串）。
 * @module api/nextwiki/types
 */

// ============= 文件管理 =============

/** 文件节点视图对象 */
export interface FileNodeVO {
  /** 节点 ID（雪花算法字符串） */
  id: string
  /** 父节点 ID */
  parentId: string
  /** 文件/文件夹名称 */
  name: string
  /** 节点类型：folder=文件夹，file=文件 */
  nodeType: 'folder' | 'file'
  /** 文件后缀 */
  suffix?: string
  /** 文件大小（字节） */
  size: number
  /** 对象存储 key */
  storageKey?: string
  /** MIME 类型 */
  mimeType?: string
  /** 完整路径 */
  path: string
  /** 是否已收藏 */
  starred: boolean
  /** 分享状态 */
  shareStatus: string
  /** 预览是否就绪 */
  previewReady: boolean
  /** 创建时间 */
  createdAt: string
  /** 更新时间 */
  updatedAt: string
  /** 创建人 */
  createdBy: string
}

/** 文件版本视图对象 */
export interface FileVersionVO {
  /** 版本 ID（雪花算法字符串） */
  id: string
  /** 文件节点 ID */
  fileNodeId: string
  /** 版本号 */
  versionNumber: number
  /** 文件大小（字节） */
  size: number
  /** 版本备注 */
  remark?: string
  /** 是否为当前活跃版本 */
  isActive: boolean
  /** 创建时间 */
  createdAt: string
  /** 创建人 */
  createdBy: string
}

/** 新建文件夹请求 */
export interface CreateFolderDTO {
  /** 父节点 ID */
  parentId: string
  /** 文件夹名称 */
  name: string
}

/** 移动文件请求 */
export interface MoveFileDTO {
  /** 目标父节点 ID */
  targetParentId: string
}

/** 重命名请求 */
export interface RenameDTO {
  /** 新名称 */
  newName: string
}

/** 批量删除请求 */
export interface BatchDeleteDTO {
  /** 要删除的文件节点 ID 列表 */
  ids: string[]
}

/** 批量移动请求 */
export interface BatchMoveDTO {
  /** 要移动的文件节点 ID 列表 */
  ids: string[]
  /** 目标父节点 ID */
  targetParentId: string
}

/** 收藏状态变更请求 */
export interface StarDTO {
  /** 是否收藏 */
  starred: boolean
}

/** 版本回滚请求 */
export interface RollbackVersionDTO {
  /** 要回滚到的版本号 */
  versionNumber: number
}

// ============= 分享 =============

/** 分享链接视图对象 */
export interface ShareLinkVO {
  /** 分享 ID（雪花算法字符串） */
  id: string
  /** 文件节点 ID */
  fileNodeId: string
  /** 分享码 */
  shareCode: string
  /** 分享类型：view=查看，download=下载，edit=编辑 */
  shareType: 'view' | 'download' | 'edit'
  /** 过期时间 */
  expireTime?: string
  /** 最大访问次数 */
  maxAccessCount?: number
  /** 已访问次数 */
  accessCount: number
  /** 状态：active=有效，expired=已过期，revoked=已撤销 */
  status: 'active' | 'expired' | 'revoked'
  /** 是否设置有访问密码 */
  hasPassword: boolean
  /** 创建时间 */
  createdAt: string
}

/** 创建分享请求 */
export interface CreateShareDTO {
  /** 文件节点 ID */
  fileNodeId: string
  /** 分享类型：view=查看，download=下载，edit=编辑 */
  shareType: 'view' | 'download' | 'edit'
  /** 过期时间 */
  expireTime?: string
  /** 最大访问次数 */
  maxAccessCount?: number
  /** 访问密码 */
  password?: string
}

/** 验证分享请求 */
export interface VerifyShareDTO {
  /** 分享码 */
  shareCode: string
  /** 访问密码 */
  password?: string
}

// ============= 预览 =============

/** 文件预览视图对象 */
export interface FilePreviewVO {
  /** 文件节点 ID */
  fileNodeId: string
  /** 预览类型：image=图片，pdf=PDF，text=文本，video=视频，unsupported=不支持 */
  previewType: 'image' | 'pdf' | 'text' | 'video' | 'unsupported'
  /** 预览地址 */
  previewUrl?: string
  /** 缩略图地址 */
  thumbnailUrl?: string
  /** 文本内容（纯文本预览时） */
  content?: string
  /** MIME 类型 */
  mimeType?: string
  /** 预览是否就绪 */
  ready: boolean
}

// ============= 配额 =============

/** 存储配额视图对象 */
export interface StorageQuotaVO {
  /** 配额 ID（雪花算法字符串） */
  id: string
  /** 配额范围类型：user=用户，tenant=租户，project=项目 */
  scopeType: 'user' | 'tenant' | 'project'
  /** 范围对象 ID */
  scopeId: string
  /** 存储容量上限（字节） */
  quotaLimit: number
  /** 已使用存储容量（字节） */
  quotaUsed: number
  /** 文件数量上限 */
  fileCountLimit: number
  /** 已使用文件数量 */
  fileCountUsed: number
}

/** 设置配额请求 */
export interface SetQuotaDTO {
  /** 配额范围类型：user=用户，tenant=租户，project=项目 */
  scopeType: 'user' | 'tenant' | 'project'
  /** 范围对象 ID */
  scopeId: string
  /** 存储容量上限（字节） */
  quotaLimit: number
  /** 文件数量上限 */
  fileCountLimit?: number
}

// ============= 标签 =============

/** 标签视图对象 */
export interface TagVO {
  /** 标签 ID（雪花算法字符串） */
  id: string
  /** 标签名称 */
  name: string
  /** 标签颜色 */
  color: string
  /** 使用次数 */
  usageCount: number
}

/** 创建标签请求 */
export interface CreateTagDTO {
  /** 标签名称 */
  name: string
  /** 标签颜色 */
  color: string
}

/** 绑定标签请求 */
export interface BindTagDTO {
  /** 标签 ID */
  tagId: string
  /** 文件节点 ID */
  fileNodeId: string
}

// ============= 回收站 =============

/** 回收站条目视图对象 */
export interface TrashItemVO {
  /** 记录 ID（雪花算法字符串） */
  id: string
  /** 文件节点 ID */
  fileNodeId: string
  /** 原始文件名 */
  originalName: string
  /** 原始路径 */
  originalPath: string
  /** 节点类型：folder=文件夹，file=文件 */
  nodeType: 'folder' | 'file'
  /** 文件大小（字节） */
  size: number
  /** 删除时间 */
  deletedTime: string
  /** 预计彻底清除时间 */
  purgeTime: string
  /** 状态：in_trash=回收站中，restored=已还原，purged=已彻底删除 */
  status: 'in_trash' | 'restored' | 'purged'
}

// ============= 搜索 =============

/** 搜索结果视图对象 */
export interface SearchResultVO {
  /** 文件节点 ID */
  fileNodeId: string
  /** 文件名称 */
  name: string
  /** 完整路径 */
  path: string
  /** 匹配内容片段 */
  snippet?: string
  /** 文件大小（字节） */
  size: number
  /** MIME 类型 */
  mimeType?: string
}

/** 文件列表查询参数 */
export interface FileListQuery {
  /** 父节点 ID */
  parentId?: string
  /** 搜索关键词 */
  keyword?: string
  /** 节点类型筛选：folder=文件夹，file=文件 */
  nodeType?: 'folder' | 'file'
  /** 收藏状态筛选 */
  starred?: boolean
  /** 排序字段 */
  sortBy?: string
  /** 排序方向：asc=升序，desc=降序 */
  sortOrder?: 'asc' | 'desc'
}
