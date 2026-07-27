#!/usr/bin/env python3
"""Fix variable name references in project controllers: e -> dto"""
import os
import re

PROJECT_WEB = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-project\ydsz-project-web\src\main\java\com\njydsz\project\web\controller'

fixed = 0
for fn in os.listdir(PROJECT_WEB):
    if not fn.endswith('Controller.java'):
        continue
    fp = os.path.join(PROJECT_WEB, fn)
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Fix: postDtoToEntity(e) -> postDtoToEntity(dto)
    content = content.replace('ProjectConverter.INSTANT.postDtoToEntity(e)', 'ProjectConverter.INSTANT.postDtoToEntity(dto)')
    # Fix: putDtoToEntity(e) -> putDtoToEntity(dto)
    content = content.replace('ProjectConverter.INSTANT.putDtoToEntity(e)', 'ProjectConverter.INSTANT.putDtoToEntity(dto)')
    
    if content != original:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(content)
        fixed += 1
        print(f"  Fixed: {fn}")

print(f"\nTotal: {fixed} controllers fixed")
