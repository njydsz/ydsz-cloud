$commonDir = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common'

$testFiles = @(
    "$commonDir\ydsz-pmis-common-json\src\test\java\com\njydsz\pmis\common\json\LargeJsonIntegrationTest.java"
    "$commonDir\ydsz-pmis-common-json\src\test\java\com\njydsz\pmis\common\json\RoundTripIntegrationTest.java"
    "$commonDir\ydsz-pmis-common-json\src\test\java\com\njydsz\pmis\common\json\YdszJsonCoreTest.java"
    "$commonDir\ydsz-pmis-common-json\src\test\java\com\njydsz\pmis\common\json\YdszJsonFeatureTest.java"
)

foreach ($file in $testFiles) {
    if (Test-Path $file) {
        $content = Get-Content $file -Raw -Encoding UTF8
        $original = $content
        $content = $content -replace '@SuppressWarnings\(\s*\{[^}]*\}\s*\)\s*\r?\n', ''
        $content = $content -replace '@SuppressWarnings\(\s*"[^"]*"\s*\)\s*\r?\n', ''
        if ($content -ne $original) {
            [System.IO.File]::WriteAllText($file, $content, [System.Text.UTF8Encoding]::new($false))
            $count = ([regex]::Matches($original, '@SuppressWarnings')).Count
            Write-Host "Removed $count from: $(Split-Path $file -Leaf)"
        }
    }
}
