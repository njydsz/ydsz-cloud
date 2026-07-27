import { requestClient } from '#/api/request';

export namespace TagApi {
  export interface TagVO {
    id: string;
    tagName: string;
    tagColor: string;
    fileCount: number;
    createTime: string;
  }

  export interface TagPageQuery {
    pageNum?: number;
    pageSize?: number;
    tagName?: string;
  }

  export interface TagDTO {
    tagName?: string;
    tagColor?: string;
  }
}

/** 分页查询 */
export function getTagPageApi(params: TagApi.TagPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: TagApi.TagVO[];
  }>(`/api/v1/nextwiki/tags/page`, { params });
}

/** 查询全部列表 */
export function getTagListApi() {
  return requestClient.get<TagApi.TagVO[]>(`/api/v1/nextwiki/tags/list`);
}

/** 根据 ID 查询 */
export function getTagByIdApi(id: string) {
  return requestClient.get<TagApi.TagVO>(`/api/v1/nextwiki/tags/${id}`);
}

/** 创建 */
export function createTagApi(data: TagApi.TagDTO) {
  return requestClient.post<string>(`/api/v1/nextwiki/tags`, data);
}

/** 更新 */
export function updateTagApi(data: TagApi.TagDTO) {
  return requestClient.put<boolean>(`/api/v1/nextwiki/tags`, data);
}

/** 删除 */
export function deleteTagApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/nextwiki/tags/${id}`);
}
