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
