#!/usr/bin/env python3
"""Add @author ydsz-team to literule *Service.java class-level Javadocs that have @since but no @author."""
import pathlib
import re

LITERULE_DIR = pathlib.Path(
    r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-literule\ydsz-literule-server\src\main\java"
)


def process_file(fpath: pathlib.Path) -> bool:
    text = fpath.read_text(encoding="utf-8")
    if "@author" in text:
        return False
    # Insert @author ydsz-team right before @since 1.0.0 in the first class-level Javadoc
    new_text = re.sub(
        r"( \* @since 1\.0\.0)",
        r" * @author ydsz-team\n *\n * @since 1.0.0",
        text,
        count=1,
    )
    if new_text == text:
        return False
    fpath.write_text(new_text, encoding="utf-8")
    return True


def main():
    count = 0
    for fpath in LITERULE_DIR.rglob("*Service.java"):
        if process_file(fpath):
            print(f"OK: {fpath.relative_to(LITERULE_DIR)}")
            count += 1
    print(f"\nTotal: {count} files updated")


if __name__ == "__main__":
    main()
