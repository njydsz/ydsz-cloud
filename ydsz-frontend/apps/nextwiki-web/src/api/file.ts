/**
 * 文件节点 API 模块（前端）
 * <p>封装文件节点（{@code ydsz_wiki_file_node}）CRUD 接口，对应后端 {@code /api/v1/nextwiki/file/*} 端点。
 * <p>支持目录/文件/快捷方式三种节点类型，文件夹递归、版本管理、ACL 权限。
 * <p>供「知识库 → 文件浏览器」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace FileApi {
  export interface FileVO {
    id: string;
    fileName: string;
    fileSize: number;
    fileType: string;
    uploadBy: string;
    parentId: string;
    status: number;
    createTime: string;
  }

  export interface FilePageQuery {
    pageNum?: number;
    pageSize?: number;
    fileName?: string;
  }

  export interface FileDTO {
    fileName?: string;
    parentId?: string;
  }
}

/** 分页查询 */
export function getFilePageApi(params: FileApi.FilePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: FileApi.FileVO[];
  }>(`/api/v1/nextwiki/files/page`, { params });
}

/** 查询全部列表 */
export function getFileListApi() {
  return requestClient.get<FileApi.FileVO[]>(`/api/v1/nextwiki/files/list`);
}

/** 根据 ID 查询 */
export function getFileByIdApi(id: string) {
  return requestClient.get<FileApi.FileVO>(`/api/v1/nextwiki/files/${id}`);
}

/** 创建 */
export function createFileApi(data: FileApi.FileDTO) {
  return requestClient.post<string>(`/api/v1/nextwiki/files`, data);
}

/** 更新 */
export function updateFileApi(data: FileApi.FileDTO) {
  return requestClient.put<boolean>(`/api/v1/nextwiki/files`, data);
}

/** 删除 */
export function deleteFileApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/nextwiki/files/${id}`);
}
