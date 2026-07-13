# Rename test files
$p = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-cache\src\test\java\com\njydsz\pmis\common\cache\spring'

$f1 = Join-Path $p 'SpringRemiCacheManagerTest.java'
if (Test-Path $f1) {
    Rename-Item $f1 'YdszCacheManagerTest.java'
    Write-Host "Renamed: SpringRemiCacheManagerTest.java -> YdszCacheManagerTest.java"
}

$f2 = Join-Path $p 'SpringRemiCacheTest.java'
if (Test-Path $f2) {
    Rename-Item $f2 'SpringYdszCacheTest.java'
    Write-Host "Renamed: SpringRemiCacheTest.java -> SpringYdszCacheTest.java"
}

Get-ChildItem $p -Filter '*.java' | ForEach-Object { Write-Host $_.Name }
