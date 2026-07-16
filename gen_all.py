import pathlib, os
BASE = pathlib.Path(os.environ.get("FILE_BASE", r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-file\src\main\java\com\njydsz\pmis\common\file"))
def w(rel, content):
    p = BASE / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")
    print(f"  {rel}: {p.stat().st_size} bytes")
print("BASE:", BASE.exists())

# === NoOpVirusScanner.java ===
w("virus/NoOpVirusScanner.java", """package com.njydsz.pmis.common.file.virus;\n\nimport java.io.InputStream;\n\nimport lombok.extern.slf4j.Slf4j;\n\n/**\n * NoOp virus scanner (default fallback).\n *\n * @author ydsz-pmis-team\n * @since 1.1.0\n */\n@Slf4j\npublic class NoOpVirusScanner implements VirusScanner {\n\n    @Override\n    public ScanResult scan(InputStream inputStream, String fileName) {\n        log.debug("VirusScan NoOp skipping: {}", fileName);\n        return ScanResult.CLEAN;\n    }\n\n    @Override\n    public boolean isAvailable() {\n        return true;\n    }\n}\n""")
