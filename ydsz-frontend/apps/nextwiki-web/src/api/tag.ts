/**
 * 文件标签 API 模块（前端）
 * <p>封装文件标签接口，对应后端 {@code /api/v1/nextwiki/tag/*} 端点。
 * <p>支持按业务域/项目/部门给文档打标签，便于检索归类。
 * <p>供「知识库 → 标签管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace TagApi {
  /** 文件标签视图对象 */
  export interface TagVO {
    id: string;
    tagName: string;
    tagColor: string;
    fileCount: number;
    createTime: string;
  }

  /** 标签分页查询参数 */
  export interface TagPageQuery {
    pageNum?: number;
    pageSize?: number;
    tagName?: string;
  }

  /** 标签创建/更新请求参数 */
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
