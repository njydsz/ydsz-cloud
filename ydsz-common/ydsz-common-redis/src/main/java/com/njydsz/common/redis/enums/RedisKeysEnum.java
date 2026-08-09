package com.njydsz.common.redis.enums;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.njydsz.common.util.string.StringUtils;

/**
 * Redis Key 枚举类
 *
 * <p>定义系统中使用的所有 Redis Key 模板，提供统一的 Key 管理。
 * 支持 Key 前缀、过期时间和批量 Key 生成。
 *
 * <p><b>扩展说明：</b>当前 Key 定义在枚举中不可扩展，业务模块如需自定义 Key 前缀和过期时间，
 * 可通过 RedisStringOps 直接操作或自定义 Key 常量类。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>统一前缀：所有 Key 使用 "ydsz:" 作为基础前缀</li>
 *   <li>模板化：使用占位符 {} 定义 Key 结构，便于维护</li>
 *   <li>分组管理：按业务模块分组，同一模块的 Key 集中定义</li>
 *   <li>过期时间：每个 Key 都有默认过期时间，便于内存管理</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 生成单个 Key
 * String key = RedisKeysEnum.USER_INFO.join(userId);
 *
 * // 批量生成 Key
 * List<String> keys = RedisKeysEnum.USER_INFO.batchJoin(userIds);
 *
 * // 获取带过期时间的 Key 信息
 * long expire = RedisKeysEnum.USER_TOKEN.getExpireAt();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RedisKeysEnum {

    // ==================== 用户认证模块 ====================

    /**
     * 用户登录 Token
     * <p>格式：login:token:{userId}:{token}
     * <p>过期时间：2 小时
     */
    USER_TOKEN("login:token:{}:{}", 7200L),

    /**
     * 用户信息缓存
     * <p>格式：user:info:{userId}
     * <p>过期时间：1 小时
     */
    USER_INFO("user:info:{}", 3600L),

    /**
     * 用户权限缓存
     * <p>格式：user:perm:{userId}
     * <p>过期时间：30 分钟
     */
    USER_PERMISSION("user:perm:{}", 1800L),

    /**
     * 用户菜单缓存
     * <p>格式：user:menu:{userId}
     * <p>过期时间：30 分钟
     */
    USER_MENU("user:menu:{}", 1800L),

    /**
     * 用户 Session
     * <p>格式：user:session:{sessionId}
     * <p>过期时间：2 小时
     */
    USER_SESSION("user:session:{}", 7200L),

    /**
     * 用户登录失败计数
     * <p>格式：user:login:fail:{username}
     * <p>过期时间：15 分钟
     */
    USER_LOGIN_FAIL("user:login:fail:{}", 900L),

    /**
     * 密码失败计数
     * <p>格式：user:pwd:fail:{userId}
     * <p>过期时间：15 分钟
     */
    USER_PWD_FAIL("user:pwd:fail:{}", 900L),

    /**
     * 用户在线状态
     * <p>格式：user:online:{userId}
     * <p>过期时间：30 分钟
     */
    USER_ONLINE("user:online:{}", 1800L),

    // ==================== 认证模块 ====================

    /**
     * 图形验证码
     * <p>格式：captcha:image:{key}
     * <p>过期时间：5 分钟
     */
    CAPTCHA_IMAGE("captcha:image:{}", 300L),

    /**
     * 短信验证码
     * <p>格式：captcha:sms:{mobile}
     * <p>过期时间：10 分钟
     */
    CAPTCHA_SMS("captcha:sms:{}", 600L),

    /**
     * 邮件验证码
     * <p>格式：captcha:email:{email}
     * <p>过期时间：15 分钟
     */
    CAPTCHA_EMAIL("captcha:email:{}", 900L),

    /**
     * OAuth2 授权码
     * <p>格式：oauth:code:{code}
     * <p>过期时间：10 分钟
     */
    OAUTH_CODE("oauth:code:{}", 600L),

    /**
     * OAuth2 Access Token
     * <p>格式：oauth:access:{token}
     * <p>过期时间：2 小时
     */
    OAUTH_ACCESS_TOKEN("oauth:access:{}", 7200L),

    /**
     * OAuth2 Refresh Token
     * <p>格式：oauth:refresh:{token}
     * <p>过期时间：7 天
     */
    OAUTH_REFRESH_TOKEN("oauth:refresh:{}", 604800L),

    // ==================== 系统配置模块 ====================

    /**
     * 系统配置缓存
     * <p>格式：sys:config:{key}
     * <p>过期时间：1 小时
     */
    SYS_CONFIG("sys:config:{}", 3600L),

    /**
     * 系统参数缓存
     * <p>格式：sys:param:{key}
     * <p>过期时间：1 小时
     */
    SYS_PARAM("sys:param:{}", 3600L),

    /**
     * 租户配置缓存
     * <p>格式：sys:tenant:{tenantId}
     * <p>过期时间：1 小时
     */
    SYS_TENANT("sys:tenant:{}", 3600L),

    // ==================== 字典模块 ====================

    /**
     * 数据字典
     * <p>格式：sys:dict:{type}
     * <p>过期时间：1 天
     */
    SYS_DICT("sys:dict:{}", 86400L),

    /**
     * 字典项缓存
     * <p>格式：sys:dict:item:{dictId}
     * <p>过期时间：1 天
     */
    SYS_DICT_ITEM("sys:dict:item:{}", 86400L),

    // ==================== 组织机构模块 ====================

    /**
     * 部门信息缓存
     * <p>格式：org:dept:{deptId}
     * <p>过期时间：1 小时
     */
    ORG_DEPT("org:dept:{}", 3600L),

    /**
     * 部门树缓存
     * <p>格式：org:dept:tree:{rootId}
     * <p>过期时间：1 小时
     */
    ORG_DEPT_TREE("org:dept:tree:{}", 3600L),

    /**
     * 岗位信息缓存
     * <p>格式：org:post:{postId}
     * <p>过期时间：1 小时
     */
    ORG_POST("org:post:{}", 3600L),

    // ==================== 工作流模块 ====================

    /**
     * 工作流定义缓存
     * <p>格式：wf:definition:{processKey}
     * <p>过期时间：1 小时
     */
    WF_DEFINITION("wf:definition:{}", 3600L),

    /**
     * 工作流实例
     * <p>格式：wf:instance:{instanceId}
     * <p>过期时间：7 天
     */
    WF_INSTANCE("wf:instance:{}", 604800L),

    /**
     * 审批任务
     * <p>格式：wf:task:{taskId}
     * <p>过期时间：30 天
     */
    WF_TASK("wf:task:{}", 2592000L),

    /**
     * 待办任务列表
     * <p>格式：wf:todo:{userId}
     * <p>过期时间：1 小时
     */
    WF_TODO("wf:todo:{}", 3600L),

    // ==================== 消息通知模块 ====================

    /**
     * 用户消息列表
     * <p>格式：msg:user:{userId}
     * <p>过期时间：30 天
     */
    MSG_USER("msg:user:{}", 2592000L),

    /**
     * 消息详情
     * <p>格式：msg:detail:{msgId}
     * <p>过期时间：30 天
     */
    MSG_DETAIL("msg:detail:{}", 2592000L),

    /**
     * 未读消息计数
     * <p>格式：msg:unread:{userId}
     * <p>过期时间：1 小时
     */
    MSG_UNREAD("msg:unread:{}", 3600L),

    // ==================== 日志审计模块 ====================

    /**
     * 登录日志
     * <p>格式：log:login:{userId}:{date}
     * <p>过期时间：90 天
     */
    LOGIN_LOG("log:login:{}:{}", 7776000L),

    /**
     * 操作日志
     * <p>格式：log:oper:{operId}
     * <p>过期时间：90 天
     */
    LOG_OPER("log:oper:{}", 7776000L),

    /**
     * 审计日志
     * <p>格式：audit:{bizType}:{bizId}
     * <p>过期时间：180 天
     */
    AUDIT_LOG("audit:{}:{}", 15552000L),

    // ==================== 安全防护模块 ====================

    /**
     * 分布式锁
     * <p>格式：lock:{business}:{identifier}
     * <p>过期时间：由调用方指定
     */
    DISTRIBUTED_LOCK("lock:{}:{}", 0L),

    /**
     * 防重复提交
     * <p>格式：idempotent:{token}
     * <p>过期时间：1 分钟
     */
    IDEMPOTENT("idempotent:{}", 60L),

    /**
     * 接口限流
     * <p>格式：ratelimit:api:{uri}:{ip}
     * <p>过期时间：1 分钟
     */
    RATELIMIT_API("ratelimit:api:{}:{}", 60L),

    /**
     * 用户操作限流
     * <p>格式：ratelimit:user:{userId}:{action}
     * <p>过期时间：1 分钟
     */
    RATELIMIT_USER("ratelimit:user:{}:{}", 60L),

    /**
     * IP 黑名单
     * <p>格式：blacklist:ip:{ip}
     * <p>过期时间：1 小时
     */
    BLACKLIST_IP("blacklist:ip:{}", 3600L),

    /**
     * 用户黑名单
     * <p>格式：blacklist:user:{userId}
     * <p>过期时间：1 小时
     */
    BLACKLIST_USER("blacklist:user:{}", 3600L),

    // ==================== 文件文档模块 ====================

    /**
     * 文件元数据
     * <p>格式：doc:meta:{fileId}
     * <p>过期时间：30 天
     */
    DOC_META("doc:meta:{}", 2592000L),

    /**
     * 文件访问令牌
     * <p>格式：doc:token:{token}
     * <p>过期时间：15 分钟
     */
    DOC_TOKEN("doc:token:{}", 900L),

    // ==================== 序列号生成模块 ====================

    /**
     * 序列号段
     * <p>格式：seq:{segmentName}
     * <p>过期时间：永久
     */
    SEQUENCE("seq:{}", -1L),

    // ==================== 缓存更新锁 ====================

    /**
     * 缓存更新锁（用于缓存预热/更新）
     * <p>格式：cache:lock:{cacheName}:{cacheKey}
     * <p>过期时间：10 秒
     */
    CACHE_UPDATE_LOCK("cache:lock:{}:{}", 10L),

    /**
     * 缓存版本号
     * <p>格式：cache:version:{cacheName}
     * <p>过期时间：永久
     */
    CACHE_VERSION("cache:version:{}", -1L);

    /**
     * Redis Key 基础前缀
     */
    private static final String BASE_PREFIX = "ydsz:";

    /**
     * Redis Key 模板（使用 {} 作为占位符）
     */
    private final String keyTemplate;

    /**
     * Key 默认过期时间（秒），-1 表示永久有效
     */
    private final Long expireAt;

    RedisKeysEnum(String keyTemplate, Long expireAt) {
        this.keyTemplate = keyTemplate;
        this.expireAt = expireAt;
    }

    /**
     * 获取完整的 Key（单个参数）
     *
     * @param args 占位符参数
     * @return 完整的 Redis Key
     */
    public String join(Object... args) {
        return BASE_PREFIX + StringUtils.format(this.keyTemplate, args);
    }

    /**
     * 获取原始模板（不含基础前缀）
     *
     * @return 模板字符串
     */
    public String getTemplate() {
        return this.keyTemplate;
    }

    /**
     * 获取带基础前缀的完整模板
     *
     * @return 带前缀的模板字符串
     */
    public String getFullTemplate() {
        return BASE_PREFIX + this.keyTemplate;
    }

    /**
     * 获取默认过期时间
     *
     * @return 过期时间（秒），-1 表示永久有效
     */
    public Long getExpireAt() {
        return this.expireAt;
    }

    /**
     * 判断是否为永久 Key
     *
     * @return 如果是永久 Key 返回 true
     */
    public boolean isPersistent() {
        return this.expireAt == null || this.expireAt < 0;
    }

    /**
     * 批量生成 Key 列表
     *
     * @param argsCollection 占位符参数集合
     * @return 完整的 Redis Key 列表
     */
    public List<String> batchJoin(Collection<?> argsCollection) {
        if (argsCollection == null || argsCollection.isEmpty()) {
            return Collections.emptyList();
        }
        return argsCollection.stream()
                .map(arg -> join(arg))
                .collect(Collectors.toList());
    }

    /**
     * 批量生成 Key 列表（使用相同的占位符参数）
     *
     * @param placeholderValue 占位符值（多个 Key 使用同一个值）
     * @param suffixes          后缀列表（每个后缀生成一个 Key）
     * @return 完整的 Redis Key 列表
     */
    public List<String> batchJoinWithSuffixes(Object placeholderValue, String... suffixes) {
        if (suffixes == null || suffixes.length == 0) {
            return Collections.emptyList();
        }
        String baseKey = join(placeholderValue);
        return Arrays.stream(suffixes)
                .map(suffix -> baseKey + ":" + suffix)
                .collect(Collectors.toList());
    }

    /**
     * 根据占位符数量检查参数是否匹配
     *
     * @param args 参数数组
     * @return 如果参数数量匹配返回 true
     */
    public boolean isArgsMatch(Object... args) {
        if (args == null) {
            return false;
        }
        int placeholders = countPlaceholders();
        return args.length == placeholders;
    }

    /**
     * 统计模板中占位符的数量
     *
     * @return 占位符数量
     */
    private int countPlaceholders() {
        if (keyTemplate == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < keyTemplate.length() - 1; i++) {
            if (keyTemplate.charAt(i) == '{' && keyTemplate.charAt(i + 1) == '}') {
                count++;
            }
        }
        return count;
    }
}
