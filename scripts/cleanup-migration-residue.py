"""
清理 JobSla 链路（已迁移到 JobAlertRule）+ 时 javadoc + pmis 残留。

清理清单：

A. 删除 JobSla 整个链路（后端）：
   - JobSla.java (entity)
   - JobSlaVO.java
   - JobSlaSaveDTO.java
   - JobSlaPostDTO.java
   - JobSlaPutDTO.java
   - JobSlaController.java
   - JobSlaService.java
   - JobSlaServiceImpl.java

B. 修改 CronjobConverter.java - 删 6 个 JobSla 相关 import + 4 个方法

C. 修改 PermissionCodes.java - 删 4 个 CRONJOB_SLA_* 常量

D. 修改 2 个 SQL 文件 - 删 ydsz_job_sla 表 DDL

E. 删除前端 SLA 链路：
   - views/sla/index.vue
   - views/sla/sla-form.vue
   - api/sla.ts

F. 修改 workflow.ts - 移除 SLA 路由

G. 清理过时 javadoc（4 个文件中的 ydsz_job_slow_log 描述）

H. 清理 FileOperatedEventListener 的 ydsz-pmis-common-socket 残留 + import 错位

I. 更新 ydsz-cronjob/README.md 移除 ydsz_job_sla 行
"""

import pathlib
import re
import sys

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis")


def read(p):
    return p.read_text(encoding="utf-8")


def write(p, c):
    p.write_text(c, encoding="utf-8")


# ===== A. 删除 JobSla 8 个文件 =====
def delete_jobsla_files():
    files = [
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/entity/JobSla.java",
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/vo/JobSlaVO.java",
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/JobSlaSaveDTO.java",
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/post/JobSlaPostDTO.java",
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/put/JobSlaPutDTO.java",
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/JobSlaController.java",
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/JobSlaService.java",
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/impl/JobSlaServiceImpl.java",
    ]
    for f in files:
        p = ROOT / f
        if p.exists():
            p.unlink()
            print(f"[OK] 删除 {p.name}")
        else:
            print(f"[WARN] {p.name} 不存在")


# ===== B. CronjobConverter 清理 =====
def fix_cronjob_converter():
    p = ROOT / "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/converter/CronjobConverter.java"
    content = read(p)

    # 1) 删除 6 个 JobSla 相关 import
    imports_to_remove = [
        "import com.njydsz.cronjob.domain.entity.alert.JobSla;\n",
        "import com.njydsz.cronjob.domain.vo.JobSlaVO;\n",
        "import com.njydsz.cronjob.domain.dto.post.JobSlaPostDTO;\n",
        "import com.njydsz.cronjob.domain.dto.put.JobSlaPutDTO;\n",
        "import com.njydsz.cronjob.domain.dto.alert.JobSlaSaveDTO;\n",
    ]
    for imp in imports_to_remove:
        if imp in content:
            content = content.replace(imp, "")
        else:
            # dto.alert.JobSlaSaveDTO 可能不存在（实际是 dto/JobSlaSaveDTO）
            if "JobSlaSaveDTO" in imp:
                content = re.sub(
                    r"import com\.njydsz\.cronjob\.domain\.dto(?:\.alert)?\.JobSlaSaveDTO;\n",
                    "",
                    content,
                )

    # 2) 删除 JobSla 相关的 4 个方法块（entityToVO, jobSlaListToVO, postDtoToEntity, putDtoToEntity）
    # entityToVO + jobSlaListToVO（紧邻在一起）
    pattern_vo = re.compile(
        r"    // ===== JobSla =====\n"
        r"    JobSlaVO entityToVO\(JobSla entity\);\n"
        r"    List<JobSlaVO> jobSlaListToVO\(List<JobSla> entities\);\n\n"
    )
    content, c1 = pattern_vo.subn("", content)

    # postDtoToEntity
    pattern_post = re.compile(
        r"    // ===== JobSla PostDTO → Entity =====\n"
        r"    @Mapping\(target = \"id\", ignore = true\)\n"
        r"    @Mapping\(target = \"deleted\", ignore = true\)\n"
        r"    @Mapping\(target = \"revision\", ignore = true\)\n"
        r"    @Mapping\(target = \"tenantId\", ignore = true\)\n"
        r"    @Mapping\(target = \"createdBy\", ignore = true\)\n"
        r"    @Mapping\(target = \"createdAt\", ignore = true\)\n"
        r"    @Mapping\(target = \"updatedBy\", ignore = true\)\n"
        r"    @Mapping\(target = \"updatedAt\", ignore = true\)\n"
        r"    JobSla postDtoToEntity\(JobSlaPostDTO dto\);\n\n"
    )
    content, c2 = pattern_post.subn("", content)

    # putDtoToEntity
    pattern_put = re.compile(
        r"    // ===== JobSla PutDTO → Entity =====\n"
        r"    @Mapping\(target = \"deleted\", ignore = true\)\n"
        r"    @Mapping\(target = \"revision\", ignore = true\)\n"
        r"    @Mapping\(target = \"tenantId\", ignore = true\)\n"
        r"    @Mapping\(target = \"updatedBy\", ignore = true\)\n"
        r"    @Mapping\(target = \"updatedAt\", ignore = true\)\n"
        r"    JobSla putDtoToEntity\(JobSlaPutDTO dto\);\n\n"
    )
    content, c3 = pattern_put.subn("", content)

    assert c1 == 1 and c2 == 1 and c3 == 1, f"CronjobConverter: vo={c1} post={c2} put={c3}（期望各 1）"
    write(p, content)
    print(f"[OK] CronjobConverter.java: 删除 JobSla 相关 {c1+c2+c3} 个方法 + {len(imports_to_remove)} 个 import")


# ===== C. PermissionCodes 清理 =====
def fix_permission_codes():
    p = ROOT / "ydsz-backend/ydsz-common/ydsz-common-auth/src/main/java/com/njydsz/common/permission/PermissionCodes.java"
    content = read(p)

    pattern = re.compile(
        r"    /\*\* CRONJOB_SLA_CREATE \*/\n"
        r"    public static final String CRONJOB_SLA_CREATE = \"cronjob:sla:create\";\n\n"
        r"    /\*\* CRONJOB_SLA_DELETE \*/\n"
        r"    public static final String CRONJOB_SLA_DELETE = \"cronjob:sla:delete\";\n\n"
        r"    /\*\* CRONJOB_SLA_UPDATE \*/\n"
        r"    public static final String CRONJOB_SLA_UPDATE = \"cronjob:sla:update\";\n\n"
        r"    /\*\* CRONJOB_SLA_VIEW \*/\n"
        r"    public static final String CRONJOB_SLA_VIEW = \"cronjob:sla:view\";\n\n"
    )
    new_content, count = pattern.subn("", content)
    assert count == 1, f"PermissionCodes: 期望删除 1 个块，实际 {count}"
    write(p, new_content)
    print("[OK] PermissionCodes.java: 删除 4 个 CRONJOB_SLA_* 权限码")


# ===== D. SQL 清理 =====
def fix_sql_files():
    # V1.0.0_cronjob.sql: 删除 ydsz_job_sla 表（已标注"已废弃 — P2-2-merge 合并到 ydsz_job_alert_rule"）
    p1 = ROOT / "deploy/sql/modules/V1.0.0_cronjob.sql"
    content1 = read(p1)

    # 删除整个 ydsz_job_sla 表块
    pattern1 = re.compile(
        r"-- \[P2-7\] 任务 SLA 管理表 ydsz_job_sla（已废弃 — P2-2-merge 合并到 ydsz_job_alert_rule）\n"
        r"-- 旧字段 max_duration_ms/max_fail_rate/min_success_rate 已合并到 alert_rule 的 alert_type+threshold\n"
        r"-- 由 AlertScanner 统一扫描 source_type='SLA' 的规则并触发告警。\n"
        r"-- 保留 DDL 是为了兼容已部署环境（项目当前未上线，未来可删除）。\n"
        r"CREATE TABLE IF NOT EXISTS ydsz_job_sla\(\n"
        r"[^;]+"
        r"\);\n",
        re.MULTILINE
    )
    new_content1, c1 = pattern1.subn("", content1)
    if c1 == 1:
        write(p1, new_content1)
        print("[OK] V1.0.0_cronjob.sql: 删除 ydsz_job_sla 表 DDL")
    else:
        # 尝试更宽松的匹配
        print(f"[WARN] V1.0.0_cronjob.sql: 严格匹配失败 (c={c1})，尝试宽松匹配")
        pattern1_loose = re.compile(
            r"-- \[P2-7\] 任务 SLA 管理表 ydsz_job_sla[^\n]*\n"
            r"(?:-- [^\n]*\n)*"
            r"CREATE TABLE IF NOT EXISTS ydsz_job_sla\([^;]+\);\n",
            re.MULTILINE
        )
        new_content1, c1 = pattern1_loose.subn("", content1)
        if c1 >= 1:
            write(p1, new_content1)
            print(f"[OK] V1.0.0_cronjob.sql: 宽松匹配删除 ydsz_job_sla 表 (c={c1})")
        else:
            print(f"[FAIL] V1.0.0_cronjob.sql: 无法删除 ydsz_job_sla 表 (c={c1})")
            # 打印文件相关行
            for i, line in enumerate(content1.split('\n')):
                if 'job_sla' in line.lower():
                    print(f"  {i+1}: {line[:100]}")

    # V1.0.0.sql: 同样删除
    p2 = ROOT / "deploy/sql/V1.0.0.sql"
    content2 = read(p2)

    # 找到 ydsz_job_sla 的位置 - 可能在 ydsz_job_alert_rule 之前
    # 这个文件是 V1.0.0.sql，是合并后的文件，ydsz_job_sla 应该已经在里面
    pattern2 = re.compile(
        r"-- \[P2-7\] 任务 SLA 管理表 ydsz_job_sla[^\n]*\n"
        r"(?:-- [^\n]*\n)*"
        r"CREATE TABLE IF NOT EXISTS ydsz_job_sla\([^;]+\);\n",
        re.MULTILINE
    )
    new_content2, c2 = pattern2.subn("", content2)
    if c2 >= 1:
        write(p2, new_content2)
        print(f"[OK] V1.0.0.sql: 删除 ydsz_job_sla 表 DDL (c={c2})")
    else:
        print(f"[WARN] V1.0.0.sql: 未找到 ydsz_job_sla 表 (c={c2})")


# ===== E. 前端 SLA 视图清理 =====
def delete_frontend_sla_files():
    files = [
        "ydsz-frontend/apps/workflow-web/src/views/sla/index.vue",
        "ydsz-frontend/apps/workflow-web/src/views/sla/sla-form.vue",
        "ydsz-frontend/apps/workflow-web/src/api/sla.ts",
    ]
    for f in files:
        p = ROOT / f
        if p.exists():
            p.unlink()
            print(f"[OK] 删除 {p.name}")
        else:
            print(f"[WARN] {p.name} 不存在")

    # 删除空目录
    sla_dir = ROOT / "ydsz-frontend/apps/workflow-web/src/views/sla"
    if sla_dir.exists() and sla_dir.is_dir():
        try:
            sla_dir.rmdir()
            print(f"[OK] 删除空目录 views/sla")
        except OSError:
            print(f"[INFO] views/sla 非空，跳过")


# ===== F. workflow.ts 路由清理 =====
def fix_workflow_routes():
    p = ROOT / "ydsz-frontend/apps/workflow-web/src/router/routes/modules/workflow.ts"
    content = read(p)

    pattern = re.compile(
        r"      \{\n"
        r"        name: 'SlaManagement',\n"
        r"        path: 'sla',\n"
        r"        component: \(\) => import\('#/views/sla/index\.vue'\),\n"
        r"        meta: \{ icon: 'lucide:alarm-clock', title: 'SLA管理' \},\n"
        r"      \},\n"
    )
    new_content, count = pattern.subn("", content)
    assert count == 1, f"workflow.ts: 期望删除 1 个 SLA 路由，实际 {count}"
    write(p, new_content)
    print("[OK] workflow.ts: 移除 SLA 路由")


# ===== G. 时 javadoc 清理 =====
def fix_slow_threshold_javadoc():
    """修正 Job.java/JobPostDTO/JobPutDTO/JobSaveDTO 中错误的 slowThresholdMs javadoc"""
    files = [
        ("ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/entity/Job.java",
         "    /**\n     * 慢任务阈值（毫秒，P6-3）。\n     *\n     * <p>null 表示不检测慢任务；执行耗时超过此值时记入 ydsz_job_slow_log。\n     */",
         "    /**\n     * 慢任务阈值（毫秒，P6-3）。\n     *\n     * <p>null 表示不检测慢任务；执行耗时超过此值时由 SlowTaskDetector\n     * 标记 {@code ydsz_job_log.is_slow=1}，用于性能趋势分析。\n     */"),
        ("ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/post/JobPostDTO.java",
         "    @Schema(description = \"慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值记入 ydsz_job_slow_log\")",
         "    @Schema(description = \"慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1\")"),
        ("ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/put/JobPutDTO.java",
         "    @Schema(description = \"慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值记入 ydsz_job_slow_log\")",
         "    @Schema(description = \"慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1\")"),
        ("ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/JobSaveDTO.java",
         "    @Schema(description = \"慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值记入 ydsz_job_slow_log\")",
         "    @Schema(description = \"慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1\")"),
    ]
    for rel_path, old, new in files:
        p = ROOT / rel_path
        content = read(p)
        if old in content:
            content = content.replace(old, new)
            write(p, content)
            print(f"[OK] {p.name}: 修正 slowThresholdMs javadoc")
        else:
            print(f"[WARN] {p.name}: 未找到匹配的 javadoc 字符串")


# ===== H. FileOperatedEventListener 修复 =====
def fix_file_operated_listener():
    p = ROOT / "ydsz-backend/ydsz-nextwiki/ydsz-nextwiki-server/src/main/java/com/njydsz/nextwiki/server/listener/FileOperatedEventListener.java"
    content = read(p)

    # 1) 修正 ydsz-pmis-common-socket 残留
    new_content = content.replace(
        "        // P1-8: 通过 WebSocket 推送文件变更通知（如果 ydsz-pmis-common-socket 可用）\n"
        "        // TODO: 注入 WebSocketMessageSender 推送实时通知\n",
        "        // P1-8: 通过 WebSocket 推送文件变更通知（如果 ydsz-common-socket 可用）\n"
        "        // TODO: 注入 WebSocketMessageSender 推送实时通知\n"
    )

    # 2) 修正 import 错位（把第 15-19 行的错位 import 移到文件顶部）
    # 当前 import 顺序错乱（中间插入了 import org.springframework.beans.factory.annotation.Autowired; 和 import java.util.UUID;）
    # 修正为标准顺序：先 java.* / javax.* / jakarta.* / org.springframework.* / com.* / lombok.*
    new_content = re.sub(
        r"import lombok\.RequiredArgsConstructor;\n"
        r"import lombok\.extern\.slf4j\.Slf4j;\n\n"
        r"import com\.njydsz\.nextwiki\.domain\.entity\.AuditLog;\n"
        r"import com\.njydsz\.nextwiki\.domain\.repository\.AuditLogRepository;\n"
        r"import com\.njydsz\.nextwiki\.server\.service\.ContentExtractionApplicationService;\n"
        r"import org\.springframework\.beans\.factory\.annotation\.Autowired;\n"
        r"import java\.util\.UUID;\n",
        "import java.util.UUID;\n"
        "\n"
        "import org.springframework.beans.factory.annotation.Autowired;\n"
        "\n"
        "import com.njydsz.nextwiki.domain.entity.AuditLog;\n"
        "import com.njydsz.nextwiki.domain.repository.AuditLogRepository;\n"
        "import com.njydsz.nextwiki.server.service.ContentExtractionApplicationService;\n"
        "\n"
        "import lombok.RequiredArgsConstructor;\n"
        "import lombok.extern.slf4j.Slf4j;\n",
        new_content
    )

    assert new_content != content, "FileOperatedEventListener 替换失败"
    write(p, new_content)
    print("[OK] FileOperatedEventListener.java: 清理 pmis 残留 + 修正 import 顺序")


# ===== I. README 清理 =====
def fix_cronjob_readme():
    p = ROOT / "ydsz-backend/ydsz-cronjob/README.md"
    content = read(p)

    new_content = content.replace(
        "| **SLA** | `ydsz_job_sla` | SLA 规则（P1-P4 + 飞书/钉钉/邮件） |\n",
        ""
    )
    if new_content != content:
        write(p, new_content)
        print("[OK] ydsz-cronjob/README.md: 移除 ydsz_job_sla 行")
    else:
        print("[WARN] ydsz-cronjob/README.md: 未找到 SLA 行")


# ===== Main =====
def main():
    print("=" * 60)
    print("阶段 A: 删除 JobSla 后端 8 个文件")
    print("=" * 60)
    delete_jobsla_files()

    print("\n" + "=" * 60)
    print("阶段 B: 清理 CronjobConverter")
    print("=" * 60)
    fix_cronjob_converter()

    print("\n" + "=" * 60)
    print("阶段 C: 清理 PermissionCodes")
    print("=" * 60)
    fix_permission_codes()

    print("\n" + "=" * 60)
    print("阶段 D: 清理 SQL")
    print("=" * 60)
    fix_sql_files()

    print("\n" + "=" * 60)
    print("阶段 E: 删除前端 SLA 文件")
    print("=" * 60)
    delete_frontend_sla_files()

    print("\n" + "=" * 60)
    print("阶段 F: 清理 workflow.ts 路由")
    print("=" * 60)
    fix_workflow_routes()

    print("\n" + "=" * 60)
    print("阶段 G: 修正过时 javadoc")
    print("=" * 60)
    fix_slow_threshold_javadoc()

    print("\n" + "=" * 60)
    print("阶段 H: 清理 FileOperatedEventListener")
    print("=" * 60)
    fix_file_operated_listener()

    print("\n" + "=" * 60)
    print("阶段 I: 清理 README")
    print("=" * 60)
    fix_cronjob_readme()

    print("\n" + "=" * 60)
    print("全部清理完成。")
    print("=" * 60)


if __name__ == "__main__":
    main()
