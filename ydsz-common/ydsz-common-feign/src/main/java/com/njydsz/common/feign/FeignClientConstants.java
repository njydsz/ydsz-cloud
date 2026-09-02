package com.njydsz.common.feign;

/**
 * FeignClient 服务名与路径常量。
 *
 * <p>所有 FeignClient 的 {@code name} 属性和 URL 路径必须使用此类中定义的常量， 禁止硬编码服务名或路径字符串。
 *
 * <p>服务名常量与 Nacos 注册名、Gateway 路由 ID 保持一致，确保全局统一。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class FeignClientConstants {

  private FeignClientConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ======================== 服务名常量 ========================

  /** 系统管理服务 */
  public static final String SYSTEM = "ydsz-system";

  /** 工作流引擎服务 */
  public static final String WORKFLOW = "ydsz-workflow";

  /** 消息中心服务 */
  public static final String MESSAGE = "ydsz-message";

  /** 定时任务服务 */
  public static final String CRONJOB = "ydsz-cronjob";

  /** 规则引擎服务 */
  public static final String LITERULE = "ydsz-literule";

  /** 用户中心服务 */
  public static final String USERINFO = "ydsz-userinfo";

  // ======================== 系统服务路径常量 ========================

  /**
   * 字典项查询路径（P1-6 修正：指向 InternalApiController 内部端点）。
   *
   * <p>原值 {@code /api/v1/dict/item} 是字典项"新增"业务端点，语义完全错误；
   * 内部查询应走 {@code /api/internal/dict/item}（请求体 DictItemGetRequest 与契约一致）。
   */
  public static final String SYSTEM_PATH_DICT_ITEM = "/api/internal/dict/item";

  /** 字典列表查询路径（P1-6 修正：原值 /api/v1/dict/list 在契约中不存在） */
  public static final String SYSTEM_PATH_DICT_LIST = "/api/internal/dict/list";

  /** 系统配置获取路径（P1-6 修正：原值 /api/v1/config/get 在契约中不存在） */
  public static final String SYSTEM_PATH_CONFIG_GET = "/api/internal/config/get";

  /** 应用信息校验路径（P1-6 修正：原值 /api/v1/app/validate 在契约中不存在） */
  public static final String SYSTEM_PATH_APP_VALIDATE = "/api/internal/app/validate";

  // ======================== 消息服务路径常量 ========================

  /** 消息发送路径 */
  public static final String MESSAGE_PATH_SEND = "/api/v1/message/send";

  /** 消息广播路径（P1-6 修正：实际端点位于 /notifications 子路径） */
  public static final String MESSAGE_PATH_BROADCAST = "/api/v1/message/notifications/broadcast";

  /** 实时推送路径（单播，P1-6 修正：实际端点位于 /notifications 子路径） */
  public static final String MESSAGE_PATH_PUSH_REALTIME =
      "/api/v1/message/notifications/push-realtime";

  // ======================== 定时任务服务路径常量 ========================

  /** 定时任务触发路径 */
  public static final String CRONJOB_PATH_TRIGGER = "/api/v1/cronjob/{id}/trigger";

  /** 定时任务详情查询路径 */
  public static final String CRONJOB_PATH_GET = "/api/v1/cronjob/{id}";

  /** 定时任务暂停路径 */
  public static final String CRONJOB_PATH_PAUSE = "/api/v1/cronjob/{id}/pause";

  /** 定时任务恢复路径 */
  public static final String CRONJOB_PATH_RESUME = "/api/v1/cronjob/{id}/resume";

  // ======================== 规则引擎服务路径常量 ========================

  /**
   * 规则评估（dry-run）路径。
   *
   * <p>P0-2 修正：旧值 {@code /ruleEngine/rules/dryRun} 为历史遗留路径，与 literule 实际暴露的
   * {@code RuleAdminController}（{@code @RequestMapping("/api/v1/literule/rules") + @PostMapping("/dry-run")}）不匹配，
   * Feign 调用必然 404 后静默走 fallback。现对齐 kebab-case 新约定。
   */
  public static final String LITERULE_PATH_DRY_RUN = "/api/v1/literule/rules/dry-run";

  /**
   * 规则评估（正式）路径。
   *
   * <p>P0-2 修正：旧值 {@code /ruleEngine/rules/evaluate} 在后端不存在；现指向
   * {@code RuleAdminController#evaluate}（本迭代补建的正式评估端点）。
   */
  public static final String LITERULE_PATH_EVALUATE = "/api/v1/literule/rules/evaluate";

  // ======================== 工作流服务路径常量 ========================

  /** 流程启动路径 */
  public static final String WORKFLOW_PATH_START = "/api/v1/workflow/engine/instance/start";

  /** 按业务查询流程路径 */
  public static final String WORKFLOW_PATH_GET_BY_BUSINESS =
      "/api/v1/workflow/engine/instance/byBusiness";

  /** 流程终止路径 */
  public static final String WORKFLOW_PATH_TERMINATE =
      "/api/v1/workflow/engine/instance/{id}/terminate";

  // ======================== 用户中心内部 API 路径常量 ========================

  /** 用户信息查询路径 */
  public static final String USERINFO_PATH_USER_INFO = "/api/internal/user/info";

  /** 部门树查询路径 */
  public static final String USERINFO_PATH_DEPT_TREE = "/api/internal/dept/tree";

  /** 部门列表查询路径 */
  public static final String USERINFO_PATH_DEPT_LIST = "/api/internal/dept/list";

  /** 按角色查询用户 ID 列表路径 */
  public static final String USERINFO_PATH_USER_LIST_BY_ROLE = "/api/internal/user/list-by-role";

  /** 查询用户角色编码列表路径 */
  public static final String USERINFO_PATH_USER_ROLE_CODES = "/api/internal/user/role-codes";

  /** 查询用户部门 ID 列表路径 */
  public static final String USERINFO_PATH_USER_DEPT_IDS = "/api/internal/user/dept-ids";

  /** 查询用户直属上级路径 */
  public static final String USERINFO_PATH_USER_LEADER = "/api/internal/user/leader";

  /** 按岗位查询用户 ID 列表路径 */
  public static final String USERINFO_PATH_USER_LIST_BY_POSITION =
      "/api/internal/user/list-by-position";

  /** 按部门 ID 查询负责人路径 */
  public static final String USERINFO_PATH_DEPT_LEADER_BY_ID = "/api/internal/dept/leader-by-id";

  /** 按部门编码查询负责人路径 */
  public static final String USERINFO_PATH_DEPT_LEADER_BY_CODE =
      "/api/internal/dept/leader-by-code";

  /** 批量查询用户名称路径 */
  public static final String USERINFO_PATH_USER_BATCH_NAMES = "/api/internal/user/batch-names";

  /** 批量查询部门名称路径 */
  public static final String USERINFO_PATH_DEPT_BATCH_NAMES = "/api/internal/dept/batch-names";

  /** 批量查询角色名称路径 */
  public static final String USERINFO_PATH_ROLE_BATCH_NAMES = "/api/internal/role/batch-names";

  /** 批量查询岗位名称路径 */
  public static final String USERINFO_PATH_POST_BATCH_NAMES = "/api/internal/post/batch-names";

  /** 批量查询公司名称路径 */
  public static final String USERINFO_PATH_COMPANY_BATCH_NAMES =
      "/api/internal/company/batch-names";

  // ======================== Feign 降级统一错误码 ========================

  /**
   * Feign 调用目标服务不可用时的统一错误码。
   *
   * <p>所有 FeignClient FallbackFactory 在服务降级时必须返回此错误码， 禁止返回 {@code YdszResponse.success(null)} 或
   * {@code YdszResponse.success(emptyList)}， 避免调用方误判为成功。
   *
   * <p>错误码与 {@link com.njydsz.common.core.code.YdszResultCode#SERVICE_UNAVAILABLE} 保持一致。
   */
  public static final String FEIGN_SERVICE_UNAVAILABLE = "B10202";
}
