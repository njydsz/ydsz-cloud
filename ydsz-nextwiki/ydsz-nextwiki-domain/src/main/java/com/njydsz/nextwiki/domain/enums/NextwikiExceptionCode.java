package com.njydsz.nextwiki.domain.enums;

import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * 网盘知识库模块异常码枚举
 * <p>
 * 采用三段式错误码结构（W 段位 + 模块号(2位) + 序号(3位)），便于按域分类与日志检索：
 * <ul>
 *   <li>W01*** - 文件操作错误（上传/下载/删除/路径/大小/后缀/存储等）</li>
 *   <li>W02*** - 版本错误（版本不存在/版本无效/版本超限）</li>
 *   <li>W03*** - 分享错误（链接不存在/过期/访问受限/提取码/密码）</li>
 *   <li>W04*** - 配额错误（空间不足/文件数超限/配额不存在）</li>
 *   <li>W05*** - 权限错误（无权限）</li>
 *   <li>W06*** - 回收站错误（条目不存在/已清理/状态非法）</li>
 *   <li>W07*** - 标签错误（标签不存在/已存在/名称为空）</li>
 *   <li>W08*** - 预览错误（未就绪/生成失败）</li>
 *   <li>W09*** - 系统错误（内部错误/锁竞争）</li>
 * </ul>
 *
 * <p><b>稳定性：</b>错误码是业务契约，修改/废弃必须保留向前兼容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum NextwikiExceptionCode implements ExceptionCode {

    // 文件相关 W01xxx
    /** 文件节点不存在 */
    FILE_NOT_FOUND("W01001", "nextwiki.file.not.found", 404),
    /** 文件名为空 */
    FILE_NAME_EMPTY("W01002", "nextwiki.file.name.empty"),
    /** 文件名无效 */
    FILE_NAME_INVALID("W01003", "nextwiki.file.name.invalid"),
    /** 文件大小超过限制 */
    FILE_TOO_LARGE("W01004", "nextwiki.file.too.large"),
    /** 文件类型不允许 */
    FILE_TYPE_NOT_ALLOWED("W01005", "nextwiki.file.type.not.allowed"),
    /** 同名文件/目录已存在 */
    FILE_ALREADY_EXISTS("W01006", "nextwiki.file.already.exists"),
    /** 父目录不存在 */
    FILE_FOLDER_NOT_FOUND("W01007", "nextwiki.folder.not.found", 404),
    /** 不能将目录移动到自身或其子目录下 */
    FILE_MOVE_TO_SELF("W01008", "nextwiki.file.move.to.self"),
    /** 目标父节点不是目录 */
    FILE_PARENT_NOT_FOLDER("W01009", "nextwiki.parent.not.folder"),
    /** 上传文件为空 */
    FILE_UPLOAD_EMPTY("W01010", "nextwiki.file.upload.empty"),
    /** 文件病毒扫描未通过 */
    FILE_VIRUS_DETECTED("W01011", "nextwiki.file.virus.detected", 422),
    /** 文件存储未配置 */
    FILE_STORAGE_NOT_CONFIGURED("W01012", "nextwiki.storage.not.configured", 500),
    /** 文件下载失败 */
    FILE_DOWNLOAD_FAILED("W01013", "nextwiki.file.download.failed", 500),
    /** 签名URL无效或已过期 */
    SIGN_URL_EXPIRED("W01014", "nextwiki.sign.url.expired"),
    /** 下载限流 */
    RATE_LIMIT_EXCEEDED("W01015", "nextwiki.rate.limit.exceeded", 429),
    /** 同名文件冲突 */
    FILE_NAME_CONFLICT("W01016", "nextwiki.file.name.conflict"),
    /** 分享验证失败次数过多，已被临时锁定 */
    SHARE_LOCKED("W03006", "nextwiki.share.locked", 429),
    /** 文件已被锁定 */
    FILE_LOCKED("W01017", "nextwiki.file.locked"),
    /** 文件未锁定 */
    FILE_NOT_LOCKED("W01018", "nextwiki.file.not.locked"),
    /** 分片上传未找到 */
    CHUNK_UPLOAD_NOT_FOUND("W01019", "nextwiki.chunk.upload.not.found", 404),
    /** 分片上传已完成 */
    CHUNK_UPLOAD_COMPLETED("W01020", "nextwiki.chunk.upload.completed"),
    /** 分片不完整 */
    CHUNK_INCOMPLETE("W01021", "nextwiki.chunk.incomplete"),

    // 版本相关 W02xxx
    /** 版本不存在 */
    VERSION_NOT_FOUND("W02001", "nextwiki.version.not.found", 404),
    /** 版本无效 */
    VERSION_INVALID("W02002", "nextwiki.version.invalid"),
    /** 版本数超过限制 */
    VERSION_EXCEED_LIMIT("W02003", "nextwiki.version.exceed.limit"),

    // 分享相关 W03xxx
    /** 分享链接不存在 */
    SHARE_NOT_FOUND("W03001", "nextwiki.share.not.found", 404),
    /** 分享链接已失效/过期 */
    SHARE_EXPIRED("W03002", "nextwiki.share.expired"),
    /** 分享链接访问次数已用尽 */
    SHARE_ACCESS_LIMIT("W03003", "nextwiki.share.access.limit"),
    /** 提取码错误 */
    SHARE_EXTRACT_CODE_ERROR("W03004", "nextwiki.share.extract.code.error"),
    /** 密码错误 */
    SHARE_PASSWORD_ERROR("W03005", "nextwiki.share.password.error"),

    // 配额相关 W04xxx
    /** 存储空间不足 */
    QUOTA_INSUFFICIENT("W04001", "nextwiki.quota.insufficient"),
    /** 文件数量已达上限 */
    QUOTA_FILE_LIMIT("W04002", "nextwiki.quota.file.limit"),
    /** 配额记录不存在 */
    QUOTA_NOT_FOUND("W04003", "nextwiki.quota.not.found", 404),

    // 权限相关 W05xxx
    /** 权限不足 */
    PERMISSION_DENIED("W05001", "nextwiki.permission.denied", 403),

    // 回收站相关 W06xxx
    /** 回收站条目不存在 */
    TRASH_NOT_FOUND("W06001", "nextwiki.trash.not.found", 404),
    /** 回收站条目已被清理 */
    TRASH_ALREADY_PURGED("W06002", "nextwiki.trash.already.purged"),
    /** 回收站条目状态不允许操作 */
    TRASH_INVALID_STATUS("W06003", "nextwiki.trash.invalid.status"),

    // 标签相关 W07xxx
    /** 标签不存在 */
    TAG_NOT_FOUND("W07001", "nextwiki.tag.not.found", 404),
    /** 标签已存在 */
    TAG_ALREADY_EXISTS("W07002", "nextwiki.tag.already.exists"),
    /** 标签名称为空 */
    TAG_NAME_EMPTY("W07003", "nextwiki.tag.name.empty"),

    // 预览相关 W08xxx
    /** 预览未就绪 */
    PREVIEW_NOT_READY("W08001", "nextwiki.preview.not.ready"),
    /** 预览生成失败 */
    PREVIEW_GENERATION_FAILED("W08002", "nextwiki.preview.generation.failed", 500),

    // 系统错误 W09xxx
    /** 系统内部错误 */
    INTERNAL_ERROR("W09001", "nextwiki.internal.error", 500),
    /** 操作正在处理中（锁竞争） */
    LOCK_BUSY("W09002", "nextwiki.lock.busy");

    /** 错误码（业务契约，不应轻易变更） */
    private final String code;
    /** 国际化 key */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    NextwikiExceptionCode(String code, String key) {
        this(code, key, 400);
    }

    NextwikiExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
