# P3-1 安全版: 仅替换 DO 文件中 @TableId 注解下的 Long id -> String id
# 同步 IdType.AUTO -> IdType.ASSIGN_ID
# 不触碰其他字段
$ErrorActionPreference = "Stop"
$base = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$entityFiles = Get-ChildItem -Path $base -Recurse -Filter "*DO.java" -File

$totalChanged = 0
foreach ($f in $entityFiles) {
    $content = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $orig = $content

    # 1) @TableId ... \n  private Long id;  -> @TableId ... \n  private String id;
    # 使用 -replace (简单文本替换) 而非 regex，避免 `${1}` 递归展开陷阱
    $content = $content -replace "@TableId\(type = IdType\.AUTO\)\s*(\r?\n\s*)private Long id;", "@TableId(type = IdType.ASSIGN_ID)`$1private String id;"
    $content = $content -replace "@TableId\s*(\r?\n\s*)private Long id;", "@TableId`$1private String id;"
    $content = $content -replace "private Long id;", "private String id;"
    $content = $content -replace "IdType\.AUTO", "IdType.ASSIGN_ID"

    if ($content -ne $orig) {
        [System.IO.File]::WriteAllText($f.FullName, $content, [System.Text.Encoding]::UTF8)
        $totalChanged++
    }
}
Write-Host ("[OK] DO primary key migration done, modified {0} files" -f $totalChanged)
