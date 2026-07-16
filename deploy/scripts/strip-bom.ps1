<#
.SYNOPSIS
    批量移除 Java 文件中的 UTF-8 BOM 字符
.DESCRIPTION
    递归扫描指定目录下所有 .java 文件，移除文件开头的 UTF-8 BOM (EF BB BF)。
    使用无 BOM 的 UTF-8 编码重新写入文件。
.PARAMETER Path
    要扫描的根目录路径，默认为脚本所在项目的 backend 目录
.EXAMPLE
    .\strip-bom.ps1 -Path "D:\Code\ydsz\ydsz\ydsz-backend"
#>
param(
    [string]$Path = "D:\Code\ydsz\ydsz\ydsz-backend"
)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$count = 0
$total = 0

Get-ChildItem -Path $Path -Recurse -Filter "*.java" | ForEach-Object {
    $total++
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $content = $utf8NoBom.GetString($bytes, 3, $bytes.Length - 3)
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8NoBom)
        Write-Output "BOM removed: $($_.FullName)"
        $count++
    }
}

Write-Output ""
Write-Output "Scan complete: $total files scanned, $count files had BOM removed."
