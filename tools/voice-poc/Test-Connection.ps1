param(
    [string]$SdkDirectory = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Serial = 'emulator-5554',
    [string]$ServerUrl = 'ws://10.0.2.2:7880',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$adb = Join-Path $SdkDirectory 'platform-tools/adb.exe'
if (-not $SkipBuild) {
    Push-Location $project
    try {
        & .\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Test APK build failed' }
    } finally { Pop-Location }
}
foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $adb -s $Serial install -r (Join-Path $project $apk)
    if ($LASTEXITCODE -ne 0) { throw 'APK install failed' }
}
# Tokens remain process-local; do not print them or put them in Gradle command logs.
$tokens = @('driver-a', 'driver-b', 'driver-c') | ForEach-Object {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'New-Token.ps1') -Identity $_
    if ($LASTEXITCODE -ne 0) { throw 'Token generation failed' }
}
$result = & $adb -s $Serial shell am instrument -w -r `
    -e class com.routemate.app.LiveKitConnectionTest `
    -e livekitUrl $ServerUrl -e livekitTokenA $tokens[0] -e livekitTokenB $tokens[1] -e livekitTokenC $tokens[2] `
    com.routemate.app.test/androidx.test.runner.AndroidJUnitRunner
$result | Write-Output
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(1 test\)') {
    throw 'LiveKit connection test failed; inspect instrumentation output'
}
