/**
 * 文件分享 API 模块（前端）
 * <p>封装文件分享链接接口，对应后端 {@code /api/v1/nextwiki/share/*} 端点。
 * <p>支持公开分享、密码保护、有效期、访问次数限制。
 * <p>供「知识库 → 分享管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace ShareApi {
  export interface ShareVO {
    id: string;
    fileName: string;
    shareTo: string;
    permission: string;
    expireDate: string;
    status: number;
    createTime: string;
  }

  export interface SharePageQuery {
    pageNum?: number;
    pageSize?: number;
    fileName?: string;
  }

  export interface ShareDTO {
    fileId?: string;
    shareTo?: string;
    permission?: string;
    expireDate?: string;
  }
}

/** 分页查询 */
export function getSharePageApi(params: ShareApi.SharePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ShareApi.ShareVO[];
  }>(`/api/v1/nextwiki/shares/page`, { params });
}

/** 查询全部列表 */
export function getShareListApi() {
  return requestClient.get<ShareApi.ShareVO[]>(`/api/v1/nextwiki/shares/list`);
}

/** 根据 ID 查询 */
export function getShareByIdApi(id: string) {
  return requestClient.get<ShareApi.ShareVO>(`/api/v1/nextwiki/shares/${id}`);
}

/** 创建 */
export function createShareApi(data: ShareApi.ShareDTO) {
  return requestClient.post<string>(`/api/v1/nextwiki/shares`, data);
}

/** 更新 */
export function updateShareApi(data: ShareApi.ShareDTO) {
  return requestClient.put<boolean>(`/api/v1/nextwiki/shares`, data);
}

/** 删除 */
export function deleteShareApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/nextwiki/shares/${id}`);
}
