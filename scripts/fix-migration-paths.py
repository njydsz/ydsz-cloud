"""
修复 P1 迁移脚本的路径 bug：目标路径缺少 src 目录。

错误路径：ydsz-pmis-project\ydsz-pmis-project-server\main\java\...
正确路径：ydsz-pmis-project\ydsz-pmis-project-server\src\main\java\...

同样修复 resources 目录：
错误路径：ydsz-pmis-project\ydsz-pmis-project-web\main\resources\...
正确路径：ydsz-pmis-project\ydsz-pmis-project-web\src\main\resources\...
"""

import pathlib
import shutil

BACKEND_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend")
PROJECT_ROOT = BACKEND_ROOT / "ydsz-pmis-project"

# 错误的子目录模式（缺少 src）
WRONG_PATTERNS = [
    "main/java",
    "main/resources",
    "test/java",
    "test/resources",
]


def find_and_fix():
    """找到错误路径的文件并移动到正确路径"""
    fixed_count = 0

    for submodule in PROJECT_ROOT.iterdir():
        if not submodule.is_dir() or not submodule.name.startswith("ydsz-pmis-project-"):
            continue

        # 检查是否直接有 main/ 或 test/ 目录（而不是 src/main/ 或 src/test/）
        for wrong_dir_name in ["main", "test"]:
            wrong_dir = submodule / wrong_dir_name
            if not wrong_dir.exists():
                continue

            # 检查这是否是错误路径（同级应该没有 src 目录，或者 src 目录下也有同名目录）
            correct_dir = submodule / "src" / wrong_dir_name

            print(f"检查: {submodule.name}/{wrong_dir_name}")
            print(f"  错误路径: {wrong_dir}")
            print(f"  正确路径: {correct_dir}")

            # 遍历错误目录下的所有文件
            for src_file in wrong_dir.rglob("*"):
                if src_file.is_dir():
                    continue

                # 计算相对于错误目录的路径
                rel_path = src_file.relative_to(wrong_dir)
                # 正确的目标路径
                target_path = correct_dir / rel_path

                # 如果目标已存在，需要判断是否是类名冲突跳过的文件
                if target_path.exists():
                    print(f"  [SKIP-EXISTS] {src_file.relative_to(PROJECT_ROOT)} → 目标已存在")
                    src_file.unlink()
                    continue

                # 移动文件
                target_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(src_file), str(target_path))
                print(f"  [FIX] {src_file.relative_to(PROJECT_ROOT)} → {target_path.relative_to(PROJECT_ROOT)}")
                fixed_count += 1

            # 清理空的错误目录
            try:
                # 从最深层开始删除空目录
                for dirpath in sorted(wrong_dir.rglob("*"), reverse=True):
                    if dirpath.is_dir():
                        try:
                            next(dirpath.iterdir())
                        except StopIteration:
                            dirpath.rmdir()
                            print(f"  [RMDIR] {dirpath.relative_to(PROJECT_ROOT)}")
                # 删除 wrong_dir 本身
                try:
                    next(wrong_dir.iterdir())
                except StopIteration:
                    wrong_dir.rmdir()
                    print(f"  [RMDIR] {wrong_dir.relative_to(PROJECT_ROOT)}")
            except OSError as e:
                print(f"  [WARN] 清理目录失败: {e}")

    print(f"\n修复完成，共移动 {fixed_count} 个文件")


if __name__ == "__main__":
    print("=" * 60)
    print("修复 P1 迁移路径 bug（缺少 src 目录）")
    print("=" * 60)
    find_and_fix()
