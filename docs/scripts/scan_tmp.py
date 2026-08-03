# -*- coding: utf-8 -*-
"""扫描指定文件的缺注释情况。"""
import sys, os
sys.path.insert(0, r'D:\Code\ydsz\ydsz-pmis\docs\scripts')
import scan_comments as sc

base = r'D:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util'
for f in ['ip/IpAddrUtils.java', 'ip/IpInfoUtils.java', 'bean/BeanCopyUtils.java']:
    p = os.path.join(base, *f.split('/'))
    r = sc.analyze_java(p)
    print(f.split('/')[-1], '| 缺类:', r['no_doc_classes'], '| 缺方法:', r['no_doc_methods'][:8])
