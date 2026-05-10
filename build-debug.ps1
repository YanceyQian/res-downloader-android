$ErrorActionPreference = "Continue"
$projectDir = "E:\res-download\res-downloader-android"
$buildLog = "$env:TEMP\gradle_build_log.txt"

Write-Host "Starting build from: $projectDir"
Write-Host "Log file: $buildLog"

Push-Location $projectDir

try {
    & "$projectDir\gradlew.bat" assembleRelease --rerun-tasks 2>&1 | Tee-Object -FilePath $buildLog
    
    # Filter for relevant error info
    $content = Get-Content $buildLog -Raw
    if ($content -match "mergeReleaseResources.*?BUILD FAILED") {
        Write-Host "`n=== BUILD FAILED ==="
        $content -split "`n" | Where-Object { 
            $_ -match "mergeReleaseResources|Cannot snapshot|values-de|ReparsePoint|IOException" 
        } | ForEach-Object { Write-Host $_ }
    }
} finally {
    Pop-Location
}
