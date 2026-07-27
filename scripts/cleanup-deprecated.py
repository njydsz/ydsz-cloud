"""
清理全仓库 @Deprecated 代码。

清理清单：
1. UserInfoConverter.java：删除 7 个未调用的 postDtoToEntity 方法
2. ProjectConverter.java：删除未调用的 dtoToEntity 方法
3. WeightedShardingStrategy.java：删除整个文件
4. TagAffinityShardingStrategy.java：删除整个文件
5. MessageServiceClient.java + MessageServiceClientFallbackFactory.java：删除两个文件
6. BytesUtil.java：删除 SIMD_ENABLED 字段 + isSimdEnabled 方法
7. VectorSimdUtil.java：删除 VECTOR_API_AVAILABLE 字段 + isVectorApiAvailable 方法
"""

import pathlib
import re
import sys

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis")


def read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: pathlib.Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")


# ---------- 1. UserInfoConverter：删除 7 个 postDtoToEntity 方法块 ----------
def fix_user_info_converter() -> None:
    p = ROOT / "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/converter/UserInfoConverter.java"
    content = read(p)

    # 每个 postDtoToEntity 方法块结构：/** @deprecated 使用 {@link #postDtoToEntity} */ + @Deprecated + 8 个 @Mapping + 方法签名
    # 用正则精确删除每段
    pattern = re.compile(
        r"    /\*\* @deprecated 使用 \{@link #postDtoToEntity\} \*/\n"
        r"    @Deprecated\n"
        r"(?:    @Mapping\(target = \"[^\"]+\", ignore = true\)\n){8}"
        r"    \w+ postDtoToEntity\(\w+ dto\);\n\n"
    )
    new_content, count = pattern.subn("", content)
    assert count == 7, f"UserInfoConverter: 期望删除 7 个 postDtoToEntity，实际删除 {count}"
    write(p, new_content)
    print(f"[OK] UserInfoConverter.java: 删除 {count} 个 postDtoToEntity 方法")


# ---------- 2. ProjectConverter：删除 dtoToEntity 方法 ----------
def fix_project_converter() -> None:
    p = ROOT / "ydsz-backend/ydsz-project/ydsz-project-domain/src/main/java/com/njydsz/project/domain/converter/ProjectConverter.java"
    content = read(p)

    pattern = re.compile(
        r"    /\*\*\n"
        r"     \* @deprecated 使用 \{@link #postDtoToEntity\} 或 \{@link #putDtoToEntity\}\n"
        r"     \*/\n"
        r"    @Deprecated\n"
        r"    @Mapping\(target = \"stage\", ignore = true\)\n"
        r"    @Mapping\(target = \"currentGate\", ignore = true\)\n"
        r"    @Mapping\(target = \"status\", ignore = true\)\n"
        r"    @Mapping\(target = \"actualStartDate\", ignore = true\)\n"
        r"    @Mapping\(target = \"actualEndDate\", ignore = true\)\n"
        r"    ProjectInitiation dtoToEntity\(ProjectInitiationDTO dto\);\n\n"
    )
    new_content, count = pattern.subn("", content)
    assert count == 1, f"ProjectConverter: 期望删除 1 个 dtoToEntity，实际删除 {count}"
    write(p, new_content)
    print(f"[OK] ProjectConverter.java: 删除 {count} 个 dtoToEntity 方法")


# ---------- 3 & 4. 删除整个 sharding 策略文件 ----------
def delete_weighted_sharding() -> None:
    p = ROOT / "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/WeightedShardingStrategy.java"
    if p.exists():
        p.unlink()
        print(f"[OK] 删除 {p.name}")
    else:
        print(f"[WARN] {p.name} 不存在")


def delete_tag_affinity_sharding() -> None:
    p = ROOT / "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/TagAffinityShardingStrategy.java"
    if p.exists():
        p.unlink()
        print(f"[OK] 删除 {p.name}")
    else:
        print(f"[WARN] {p.name} 不存在")


# ---------- 5. 删除 MessageServiceClient + FallbackFactory + 联动清理 ----------
def delete_message_service_client() -> None:
    f1 = ROOT / "ydsz-backend/ydsz-common/ydsz-common-feign/src/main/java/com/njydsz/common/feign/MessageServiceClient.java"
    f2 = ROOT / "ydsz-backend/ydsz-common/ydsz-common-feign/src/main/java/com/njydsz/common/feign/fallback/MessageServiceClientFallbackFactory.java"
    if f1.exists():
        f1.unlink()
        print(f"[OK] 删除 {f1.name}")
    if f2.exists():
        f2.unlink()
        print(f"[OK] 删除 {f2.name}")


def fix_notification_client_javadoc() -> None:
    p = ROOT / "ydsz-backend/ydsz-common/ydsz-common-feign/src/main/java/com/njydsz/common/feign/NotificationClient.java"
    content = read(p)

    # 删除整个 P1-5 段落（javadoc 里的"已将 MessageServiceClient 合并至此"段）
    new_content = content.replace(
        ' * <p><b>P1-5</b>：已将 {@link MessageServiceClient} 的多通道消息发送能力合并至此，\n'
        ' * 统一为单一通知入口。原 {@code MessageServiceClient} 已标记为 {@code @Deprecated}。\n',
        ""
    )
    # 删除 sendMessage 方法的 javadoc 中关于 "P1-5: 由原 MessageServiceClient#send 合并而来" 的描述
    new_content = new_content.replace(
        '     * <p>P1-5: 由原 {@link MessageServiceClient#send(MessageRequest)} 合并而来。\n'
        '     * 通过 message 模块路由到具体通道实现，支持所有渠道。\n',
        "     * <p>通过 message 模块路由到具体通道实现，支持所有渠道（邮件 / 短信 / Webhook / 站内信等）。\n"
    )
    assert new_content != content, "NotificationClient.java 替换失败"
    write(p, new_content)
    print("[OK] NotificationClient.java: 清理 MessageServiceClient 相关 javadoc")


def fix_alert_dispatcher_javadoc() -> None:
    p = ROOT / "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/AlertDispatcher.java"
    content = read(p)

    new_content = content.replace(
        "    /** 统一通知客户端（P1-5: 替代原 MessageServiceClient + NotificationClient 双入口） */\n",
        "    /** 统一通知客户端 */\n"
    )
    new_content = new_content.replace(
        "        // 原来既直接调用 MessageServiceClient.send() 又发布 UnifiedAlertEvent，\n"
        "        // 而 UnifiedAlertDispatcher 消费事件后再次调用 MessageServiceClient.send()，导致同一告警被发送两次。\n"
        "        // 现在移除事件发布，改为直接调用 NotificationClient.broadcast() 实现实时广播。\n",
        "        // 通知实时广播，由前端 WebSocket 推送给在线用户。\n"
    )
    assert new_content != content, "AlertDispatcher.java 替换失败"
    write(p, new_content)
    print("[OK] AlertDispatcher.java: 清理 MessageServiceClient 相关注释")


def fix_common_feign_readme() -> None:
    p = ROOT / "ydsz-backend/ydsz-common/ydsz-common-feign/README.md"
    content = read(p)
    new_content = content.replace(
        "| `NotificationClientFallbackFactory` | 通知客户端降级 |\n"
        "| `MessageServiceClientFallbackFactory` | 消息服务客户端降级 |\n",
        "| `NotificationClientFallbackFactory` | 通知客户端降级 |\n"
    )
    new_content = new_content.replace(
        "| `NotificationClient` | 通知服务客户端 |\n"
        "| `MessageServiceClient` | 消息服务客户端 |\n"
        "| `MessageRequest` / `MessageResult` | 消息请求 / 响应模型 |\n",
        "| `NotificationClient` | 通知服务客户端（统一入口，支持多通道消息发送） |\n"
        "| `MessageRequest` / `MessageResult` | 消息请求 / 响应模型 |\n"
    )
    assert new_content != content, "common-feign README 替换失败"
    write(p, new_content)
    print("[OK] ydsz-common-feign/README.md: 清理 MessageServiceClient 相关条目")


def fix_workflow_sql_comment() -> None:
    p = ROOT / "deploy/sql/modules/V1.0.0_workflow.sql"
    content = read(p)
    new_content = content.replace(
        "--   工作流通知请通过 MessageServiceClient (common/feign) 调用 message 服务。\n",
        "--   工作流通知请通过 NotificationClient (common/feign) 调用 message 服务。\n"
    )
    assert new_content != content, "V1.0.0_workflow.sql 替换失败"
    write(p, new_content)
    print("[OK] V1.0.0_workflow.sql: 修正 MessageServiceClient → NotificationClient 注释")


# ---------- 6. BytesUtil：删除 SIMD_ENABLED 字段 + isSimdEnabled 方法 ----------
def fix_bytes_util() -> None:
    p = ROOT / "ydsz-backend/ydsz-common/ydsz-common-json/src/main/java/com/njydsz/common/json/bytecode/BytesUtil.java"
    content = read(p)

    # 1) 删除 SIMD_ENABLED 字段块
    field_pattern = re.compile(
        r"    /\*\*\n"
        r"     \* SIMD 是否真正启用 — 始终为 false\n"
        r"     \*\n"
        r"     \* <p>保留以兼容旧 API。当前实现依赖 JIT 自动向量化，\n"
        r"     \* 不再使用显式 Vector API 调用。</p>\n"
        r"     \*/\n"
        r"    public static final boolean SIMD_ENABLED = false;\n\n"
    )
    new_content, c1 = field_pattern.subn("", content)

    # 2) 删除 isSimdEnabled 方法块
    method_pattern = re.compile(
        r"    /\*\*\n"
        r"     \* 检测 SIMD 是否真正启用\n"
        r"     \*\n"
        r"     \* @return 始终返回 false\n"
        r"     \* @deprecated 当前实现依赖 JIT 自动向量化，不再使用 Vector API\n"
        r"     \*/\n"
        r"    @Deprecated\(since = \"1.0.0\"\)\n"
        r"    public static boolean isSimdEnabled\(\) \{\n"
        r"        return SIMD_ENABLED;\n"
        r"    \}\n\n"
    )
    new_content, c2 = method_pattern.subn("", new_content)

    assert c1 == 1 and c2 == 1, f"BytesUtil: 字段删除 {c1}，方法删除 {c2}（期望各 1）"
    write(p, new_content)
    print(f"[OK] BytesUtil.java: 删除 SIMD_ENABLED 字段 + isSimdEnabled 方法")


# ---------- 7. VectorSimdUtil：删除 VECTOR_API_AVAILABLE + isVectorApiAvailable ----------
def fix_vector_simd_util() -> None:
    p = ROOT / "ydsz-backend/ydsz-common/ydsz-common-json/src/main/java/com/njydsz/common/json/bytecode/VectorSimdUtil.java"
    content = read(p)

    field_pattern = re.compile(
        r"    /\*\*\n"
        r"     \* Vector API 是否可用 — 始终返回 false\n"
        r"     \*\n"
        r"     \* <p>保留此字段以保持向后兼容性。当前实现不再使用 Vector API，\n"
        r"     \* 依赖 JIT 自动向量化。该字段将在 2.0.0 版本移除。</p>\n"
        r"     \*/\n"
        r"    public static final boolean VECTOR_API_AVAILABLE = false;\n\n"
    )
    new_content, c1 = field_pattern.subn("", content)

    method_pattern = re.compile(
        r"    /\*\*\n"
        r"     \* Vector API 是否可用（保留以兼容旧 API，始终返回 false）\n"
        r"     \*\n"
        r"     \* @return 始终返回 false\n"
        r"     \* @deprecated 当前实现依赖 JIT 自动向量化，不再使用 Vector API\n"
        r"     \*/\n"
        r"    @Deprecated\(since = \"1.0.0\"\)\n"
        r"    public static boolean isVectorApiAvailable\(\) \{\n"
        r"        return VECTOR_API_AVAILABLE;\n"
        r"    \}\n\n"
    )
    new_content, c2 = method_pattern.subn("", new_content)

    assert c1 == 1 and c2 == 1, f"VectorSimdUtil: 字段删除 {c1}，方法删除 {c2}（期望各 1）"
    write(p, new_content)
    print(f"[OK] VectorSimdUtil.java: 删除 VECTOR_API_AVAILABLE 字段 + isVectorApiAvailable 方法")


# ---------- 联动：cronjob README 移除 WeightedShardingStrategy 目录描述 ----------
def fix_cronjob_readme() -> None:
    p = ROOT / "ydsz-backend/ydsz-cronjob/README.md"
    if not p.exists():
        return
    content = read(p)
    new_content = content.replace(
        "│   │   │   ├── WeightedShardingStrategy.java # 加权分片\n",
        ""
    )
    if new_content != content:
        write(p, new_content)
        print("[OK] ydsz-cronjob/README.md: 移除 WeightedShardingStrategy 目录条目")
    else:
        print("[INFO] ydsz-cronjob/README.md: 无需调整")


# ---------- 联动：LoadAwareShardingStrategy.java 移除 "与 WeightedShardingStrategy 的区别" 段 ----------
def fix_load_aware_javadoc() -> None:
    p = ROOT / "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/LoadAwareShardingStrategy.java"
    if not p.exists():
        return
    content = read(p)
    new_content = content.replace(
        " * <h3>与 WeightedShardingStrategy 的区别</h3>\n"
        " * <ul>\n"
        " *   <li>WeightedShardingStrategy 仅考虑 CPU 和运行任务数（2 维）</li>\n"
        " *   <li>LoadAwareShardingStrategy 扩展到 4 维，并引入历史成功率反馈机制</li>\n"
        " *   <li>支持自适应权重调整：节点连续失败时自动降权</li>\n"
        " * </ul>\n",
        ""
    )
    if new_content != content:
        write(p, new_content)
        print("[OK] LoadAwareShardingStrategy.java: 移除与 WeightedShardingStrategy 的对比 javadoc")
    else:
        print("[INFO] LoadAwareShardingStrategy.java: 无需调整")


def main() -> None:
    fix_user_info_converter()
    fix_project_converter()
    delete_weighted_sharding()
    delete_tag_affinity_sharding()
    delete_message_service_client()
    fix_notification_client_javadoc()
    fix_alert_dispatcher_javadoc()
    fix_common_feign_readme()
    fix_workflow_sql_comment()
    fix_bytes_util()
    fix_vector_simd_util()
    fix_cronjob_readme()
    fix_load_aware_javadoc()
    print("\n全部清理完成。")


if __name__ == "__main__":
    main()
