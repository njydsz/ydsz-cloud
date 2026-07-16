import pathlib, re
p = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-file\src\main\java\com\njydsz\pmis\common\file\storage\AbstractFileStorage.java")
c = p.read_text(encoding="utf-8")
REPLACEMENTS = []
