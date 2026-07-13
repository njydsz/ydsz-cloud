package com.njydsz.pmis.nextwiki.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件分享链接实体
 * <p>
 * 生成带密码和过期时间的分享链接，支持文件/文件夹级别的分享。
 * 区别于系统级 RBAC 权限，这是文件级的临时授权机制。
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>分享码（shareCode）使用 UUID 生成，不可猜测</li>
 *   <li>访问密码使用 BCrypt 加密存储</li>
 *   <li>支持访问次数限制和过期时间</li>
 *   <li>支持提取码（4 位数字）二次验证</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_share_link")
public class ShareLink extends BaseEntity<String> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联的文件节点ID */
    private String fileNodeId;

    /** 分享码（URL 中的唯一标识，UUID 生成） */
    private String shareCode;

    /** 提取码（4 位数字，访问时需要输入） */
    private String extractCode;

    /** 分享类型：view（仅查看）/ download（可下载）/ edit（可编辑） */
    private String shareType;

    /** 过期时间（null 表示永久有效） */
    private LocalDateTime expireTime;

    /** 最大访问次数（null 表示不限） */
    private Integer maxAccessCount;

    /** 已访问次数 */
    private Integer accessCount;

    /** 分享状态：active / expired / revoked */
    private String status;

    /** 分享密码（BCrypt 加密；空表示无密码） */
    private String password;
}
