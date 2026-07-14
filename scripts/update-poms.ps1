$ErrorActionPreference = "Stop"
$rootDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

# Find all pom.xml files that reference fastjson2 or jackson
$pomFiles = Get-ChildItem -Path $rootDir -Recurse -Filter "pom.xml" |
    Select-String -Pattern "fastjson2|jackson-databind|jackson-datatype|jackson-core" -List |
    Select-Object -ExpandProperty Path -Unique

Write-Host "Found $($pomFiles.Count) POM files to update"

foreach ($pomFile in $pomFiles) {
    $content = Get-Content $pomFile -Raw -Encoding UTF8
    $original = $content

    # Remove fastjson2 dependencies
    $content = $content -replace '(?s)\s*<dependency>\s*<groupId>com\.alibaba\.fastjson2</groupId>.*?</dependency>', ''

    # Remove jackson-databind dependencies
    $content = $content -replace '(?s)\s*<dependency>\s*<groupId>com\.fasterxml\.jackson\.core</groupId>\s*<artifactId>jackson-databind</artifactId>.*?</dependency>', ''

    # Remove jackson-datatype-jsr310 dependencies
    $content = $content -replace '(?s)\s*<dependency>\s*<groupId>com\.fasterxml\.jackson\.datatype</groupId>.*?</dependency>', ''

    # Remove jackson-core dependencies
    $content = $content -replace '(?s)\s*<dependency>\s*<groupId>com\.fasterxml\.jackson\.core</groupId>.*?</dependency>', ''

    # Remove jackson-annotations dependencies
    $content = $content -replace '(?s)\s*<dependency>\s*<groupId>com\.fasterxml\.jackson\.core</groupId>\s*<artifactId>jackson-annotations</artifactId>.*?</dependency>', ''

    # Add ydsz-pmis-common-json dependency if the file still has dependencies and doesn't already have it
    if (-not ($content -match 'ydsz-pmis-common-json')) {
        # Find the last dependency in the file and add after it
        $lastDepMatch = [regex]::Match($content, '(</dependency>\s*)(?=</dependencies>)')
        if ($lastDepMatch.Success) {
            $insertAt = $lastDepMatch.Index + $lastDepMatch.Length
            $newDep = @"
        <dependency>
            <groupId>com.njydsz.pmis</groupId>
            <artifactId>ydsz-pmis-common-json</artifactId>
        </dependency>
"@
            $content = $content.Substring(0, $insertAt) + $newDep + "`r`n" + $content.Substring($insertAt)
        }
    }

    if ($content -ne $original) {
        Set-Content -Path $pomFile -Value $content -Encoding UTF8 -NoNewline
        Write-Host "  Updated: $(Split-Path $pomFile -Leaf) in $(Split-Path (Split-Path $pomFile -Parent) -Leaf)"
    }
}

Write-Host "`nDone!"
