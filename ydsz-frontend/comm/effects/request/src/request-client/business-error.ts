/**
 * 业务错误类
 * @description 用于封装后端返回的业务错误信息，保留完整的错误上下文
 */
export class BusinessError extends Error {
  /** 业务错误码 */
  code: number;
  /** 响应数据 */
  data: any;
  /** HTTP 状态码 */
  statusCode: number;

  constructor(
    message: string,
    options: { code?: number; data?: any; statusCode?: number } = {},
  ) {
    super(message);
    this.name = 'BusinessError';
    this.code = options.code ?? -1;
    this.data = options.data ?? null;
    this.statusCode = options.statusCode ?? -1;
  }
}
