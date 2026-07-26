"""
重命名 ydsz-common 模块下的 native-image 目录路径，移除 pmis 残留。

将以下路径重命名（target 目录由 mvn clean 重建，不动）：
- com.njydsz.pmis/ydsz-common-json/native-image.json -> com.njydsz/ydsz-common-json/native-image.json
- com.njydsz.pmis/ydsz-common-core/native-image.properties -> com.njydsz/ydsz-common-core/native-image.properties
- com.njydsz.pmis.common.redis/native-image.properties -> com.njydsz/ydsz-common-redis/native-image.properties
- com.njydsz.pmis.common.exception/native-image.properties -> com.njydsz/ydsz-common-exception/native-image.properties

完成后删除旧目录树，仅保留新路径。
"""
import pathlib
import shutil

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common")

# (模块名, 旧目录名, 新目录名)
MIGRATIONS = [
    ("ydsz-common-json", "com.njydsz.pmis", "com.njydsz"),
    ("ydsz-common-core", "com.njydsz.pmis", "com.njydsz"),
    ("ydsz-common-redis", "com.njydsz.pmis.common.redis", "com.njydsz"),
    ("ydsz-common-exception", "com.njydsz.pmis.common.exception", "com.njydsz"),
]


def migrate():
    for module, old_dir_name, new_dir_name in MIGRATIONS:
        src_root = ROOT / module / "src" / "main" / "resources" / "META-INF" / "native-image"
        old_dir = src_root / old_dir_name
        new_dir = src_root / new_dir_name
        if not old_dir.exists():
            print(f"[SKIP] {module}: old dir not found: {old_dir}")
            continue

        # 创建新目录
        new_dir.mkdir(parents=True, exist_ok=True)

        # 移动旧目录下所有文件到新目录
        for item in old_dir.iterdir():
            target = new_dir / item.name
            if target.exists():
                # 内容一致则直接删除旧文件；否则覆盖
                target.unlink()
            shutil.move(str(item), str(target))
            print(f"[MOVE] {module}: {item.name} -> {target.relative_to(ROOT)}")

        # 删除旧目录树
        shutil.rmtree(old_dir, ignore_errors=True)
        print(f"[RMTREE] {module}: removed {old_dir.relative_to(ROOT)}")


if __name__ == "__main__":
    migrate()
    print("\n[OK] native-image 目录迁移完成")
