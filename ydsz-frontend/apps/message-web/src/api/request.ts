/**
 * 消息服务 HTTP 请求客户端 API 模块（前端）
 * <p>封装 ydsz-message 服务的 HTTP 请求客户端，基于 @ydsz/shared-auth 统一注入 Token / TraceId / 错误处理。
 * <p>所有 message 业务 API 均通过该 requestClient 发起 HTTP 调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
/**
 * RequestClient — re-export from @ydsz/shared-auth
 */
export {
  baseRequestClient,
  initSharedRequest,
  requestClient,
} from '@ydsz/shared-auth';
