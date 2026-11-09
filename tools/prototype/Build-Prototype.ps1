param([switch]$Install,[switch]$Verify,[switch]$Screenshots)
$ErrorActionPreference='Stop'
$appPath=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$outputPath=Join-Path $appPath 'app/build/prototype'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
Push-Location $appPath
try {
    & .\gradlew.bat assembleDebug lintDebug -PprototypeDemo=true --console=plain --no-daemon
    if($LASTEXITCODE -ne 0){throw 'Prototype build failed'}
    $metadata=Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    if($metadata.applicationId -ne 'com.routemate.app.prototype'){throw 'Unexpected prototype package'}
    Copy-Item -LiteralPath app/build/outputs/apk/debug/app-debug.apk -Destination (Join-Path $outputPath 'RouteMate-prototype.apk')
    if($Install -or $Verify -or $Screenshots){
        $sdkPath=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
        $adb=Join-Path $sdkPath 'platform-tools/adb.exe'
        $devices=@(& $adb devices | Where-Object{$_ -match '\s+device$'})
        if($devices.Count -ne 1){throw 'Use exactly one Android test device or ordinary emulator'}
        if($Verify -or $Screenshots){
            & .\gradlew.bat assembleDebugAndroidTest -PprototypeDemo=true --console=plain --no-daemon
            if($LASTEXITCODE -ne 0){throw 'Prototype test APK build failed'}
            & $adb install -r app/build/outputs/apk/debug/app-debug.apk
            if($LASTEXITCODE -ne 0){throw 'Prototype installation failed'}
            & $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            if($LASTEXITCODE -ne 0){throw 'Prototype test installation failed'}
            # Gradle's device runner uninstalls the target and deletes screenshots on exit.
            # Run this isolated prototype package directly to preserve the review artifacts.
            try {
                $arguments=@('shell','am','instrument','-w','-e','class','com.routemate.app.CarUxSmokeTest')
                if($Screenshots){$arguments+=@('-e','prototypeScreenshots','true')}
                $arguments+='com.routemate.app.prototype.test/androidx.test.runner.AndroidJUnitRunner'
                $result=& $adb @arguments
                $result | Set-Content -LiteralPath (Join-Path $outputPath 'ui-verification.txt') -Encoding UTF8
                if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(2 tests\)'){throw 'Prototype UI verification failed; inspect ui-verification.txt'}
            } finally { & $adb uninstall com.routemate.app.prototype.test | Out-Null }
        }else{
            & $adb install -r app/build/outputs/apk/debug/app-debug.apk
            if($LASTEXITCODE -ne 0){throw 'Prototype installation failed'}
        }
        if($Screenshots){
            foreach($name in @('dashboard-results.png','dashboard-call.png','dashboard-night.png')){
                & $adb pull ('/sdcard/Android/data/com.routemate.app.prototype/files/'+$name) (Join-Path $outputPath $name)
                if($LASTEXITCODE -ne 0){throw 'Screenshot export failed'}
            }
        }
        & $adb shell am start -n com.routemate.app.prototype/com.routemate.app.MainActivity
        if($LASTEXITCODE -ne 0){throw 'Prototype launch failed'}
    }
    Write-Host ('Prototype APK: '+(Join-Path $outputPath 'RouteMate-prototype.apk'))
} finally { Pop-Location }
