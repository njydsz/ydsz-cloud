# -*- coding: utf-8 -*-
import pathlib
import re

BASE = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-project")
ENTITY_BASE = BASE / "ydsz-project-domain/src/main/java/com/njydsz/project/domain/entity"
SERVER_SRC = BASE / "ydsz-project-server/src/main/java/com/njydsz/project/server"
WEB_SRC = BASE / "ydsz-project-web/src/main/java/com/njydsz/project/web"

entities = []
for f in ENTITY_BASE.rglob("*DO.java"):
    simple_name = f.stem.replace("DO", "")
    sub_domain = ".".join(f.relative_to(ENTITY_BASE).parts[:-1])
    entities.append((simple_name, sub_domain))

entities = [(s, d) for s, d in entities if s != "ProjectInitiation"]
print(f"Generating {len(entities)} entities")

for sn, sd in entities:
    url = re.sub(r'(?<!^)(?=[A-Z])', '/', sn).lower()
    DO = sn + "DO"
    Repo = "I" + sn + "Repository"
    Svc = sn + "Service"
    Impl = sn + "ServiceImpl"
    Ctrl = sn + "Controller"

    d = SERVER_SRC / "service"
    d.mkdir(parents=True, exist_ok=True)
    f1 = d / f"{Svc}.java"
    if not f1.exists():
        f1.write_text(
            "package com.njydsz.project.server.service;\n\n"
            f"import com.njydsz.project.domain.entity.{sd}.{DO};\n\n"
            f"public interface {Svc} {{\n"
            f"    {DO} getById(String id);\n"
            f"    com.baomidou.mybatisplus.core.metadata.IPage<{DO}> page(int pageNum, int pageSize);\n"
            f"    boolean save({DO} entity);\n"
            f"    boolean updateById({DO} entity);\n"
            "    boolean removeById(String id);\n"
            "}\n", encoding="utf-8")

    d2 = SERVER_SRC / "service" / "impl"
    d2.mkdir(parents=True, exist_ok=True)
    f2 = d2 / f"{Impl}.java"
    if not f2.exists():
        f2.write_text(
            "package com.njydsz.project.server.service.impl;\n\n"
            "import com.baomidou.mybatisplus.core.metadata.IPage;\n"
            "import com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n"
            f"import com.njydsz.project.domain.entity.{sd}.{DO};\n"
            f"import com.njydsz.project.domain.repository.{sd}.{Repo};\n"
            f"import com.njydsz.project.server.service.{Svc};\n\n"
            "import lombok.RequiredArgsConstructor;\n\n"
            "import org.springframework.stereotype.Service;\n"
            "import org.springframework.transaction.annotation.Transactional;\n\n"
            "@Service\n@RequiredArgsConstructor\n"
            f"public class {Impl} implements {Svc} {{\n"
            f"    private final {Repo} repository;\n\n"
            f"    public {DO} getById(String id) {{ return repository.getById(id); }}\n"
            f"    public IPage<{DO}> page(int p, int s) {{ return repository.page(new Page<>(p, s)); }}\n"
            f"    @Transactional(rollbackFor = Exception.class)\n    public boolean save({DO} e) {{ return repository.save(e); }}\n"
            f"    @Transactional(rollbackFor = Exception.class)\n    public boolean updateById({DO} e) {{ return repository.updateById(e); }}\n"
            "    @Transactional(rollbackFor = Exception.class)\n    public boolean removeById(String id) { return repository.removeById(id); }\n"
            "}\n", encoding="utf-8")

    d3 = WEB_SRC / "controller"
    d3.mkdir(parents=True, exist_ok=True)
    f3 = d3 / f"{Ctrl}.java"
    if not f3.exists():
        f3.write_text(
            "package com.njydsz.project.web.controller;\n\n"
            "import com.baomidou.mybatisplus.core.metadata.IPage;\n"
            "import com.njydsz.common.audit.annotation.Audit;\n"
            "import com.njydsz.common.audit.enums.AuditAction;\n"
            "import com.njydsz.common.core.response.BaseResponse;\n"
            "import com.njydsz.common.core.response.PageResponse;\n"
            f"import com.njydsz.project.domain.entity.{sd}.{DO};\n"
            f"import com.njydsz.project.server.service.{Svc};\n\n"
            "import lombok.RequiredArgsConstructor;\n\n"
            "import org.springframework.web.bind.annotation.*;\n\n"
            "@RestController\n"
            f"@RequestMapping(\"/api/v1/project/{url}\")\n"
            "@RequiredArgsConstructor\n"
            f"public class {Ctrl} {{\n"
            f"    private final {Svc} service;\n\n"
            f"    @GetMapping(\"/{{id}}\")\n    public BaseResponse<{DO}> getById(@PathVariable String id) {{ return BaseResponse.success(service.getById(id)); }}\n\n"
            f"    @GetMapping(\"/page\")\n    public PageResponse<{DO}> page(@RequestParam(defaultValue=\"1\") int p, @RequestParam(defaultValue=\"10\") int s) {{\n"
            f"        IPage<{DO}> r = service.page(p, s);\n        return PageResponse.success(r.getRecords(), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());\n    }}\n\n"
            f"    @PostMapping\n    @Audit(action=AuditAction.CREATE, module=\"PROJECT\", description=\"Create {sn}\")\n    public BaseResponse<Boolean> save(@RequestBody {DO} e) {{ return BaseResponse.success(service.save(e)); }}\n\n"
            f"    @PutMapping\n    @Audit(action=AuditAction.UPDATE, module=\"PROJECT\", description=\"Update {sn}\")\n    public BaseResponse<Boolean> update(@RequestBody {DO} e) {{ return BaseResponse.success(service.updateById(e)); }}\n\n"
            f"    @DeleteMapping(\"/{{id}}\")\n    @Audit(action=AuditAction.DELETE, module=\"PROJECT\", description=\"Delete {sn}\")\n    public BaseResponse<Boolean> remove(@PathVariable String id) {{ return BaseResponse.success(service.removeById(id)); }}\n"
            "}\n", encoding="utf-8")

print(f"Done! Generated {len(entities)} triples")
