$root = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend'
Get-ChildItem -Path $root -Recurse -Include *.java | ForEach-Object {
    $file = $_.FullName
    $content = Get-Content $file
    $lineNum = 0
    foreach ($line in $content) {
        $lineNum++
        if ($line -match '^\s*import\s') { continue }
        if ($line -match '\{@link') { continue }
        if ($line -match 'com\.njydsz\.pmis\.[a-zA-Z0-9_]+\.[a-zA-Z0-9_]+(\.[A-Z][a-zA-Z0-9_]*)+\b') {
            $rel = $file.Substring($root.Length)
            Write-Output ("{0}:{1}: {2}" -f $rel, $lineNum, $line.Trim())
        }
    }
}
