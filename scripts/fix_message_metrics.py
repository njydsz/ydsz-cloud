#!/usr/bin/env python3
"""P1-2: Merge MessageServiceMetrics into MessageMetrics — replace all references and delete the old class."""

import os
import re

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-message\ydsz-message-server\src\main\java\com\njydsz\message\server"

# File-specific replacements
FILES_TO_UPDATE = [
    # MessageConsumer.java
    "consumer/MessageConsumer.java",
    # MessageAlertService.java
    "service/impl/MessageAlertService.java",
    # MessageServiceImpl.java
    "service/impl/MessageServiceImpl.java",
    # MessageAutoConfiguration.java
    "config/MessageAutoConfiguration.java",
]

REPLACEMENTS = [
    # Package import replacement
    (r'import com\.njydsz\.message\.server\.metrics\.MessageServiceMetrics;',
     'import com.njydsz.message.server.metric.MessageMetrics;'),
    # Class name replacement
    (r'MessageServiceMetrics', 'MessageMetrics'),
    # Variable name replacement (messageServiceMetrics → messageMetrics)
    (r'messageServiceMetrics', 'messageMetrics'),
]


def main():
    modified = 0
    for rel_path in FILES_TO_UPDATE:
        file_path = os.path.join(BASE, rel_path)
        if not os.path.exists(file_path):
            print(f"NOT FOUND: {file_path}")
            continue
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        original = content
        for old, new in REPLACEMENTS:
            content = re.sub(old, new, content)
        if content != original:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            modified += 1
            print(f"MODIFIED: {file_path}")

    # Delete MessageServiceMetrics.java
    old_file = os.path.join(BASE, "metrics", "MessageServiceMetrics.java")
    if os.path.exists(old_file):
        os.remove(old_file)
        print(f"DELETED: {old_file}")

    print(f"\nTotal modified: {modified} files")


if __name__ == '__main__':
    main()
