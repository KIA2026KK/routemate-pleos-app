param(
    [string]$BackendPath = (Join-Path $PSScriptRoot '../../../routemate-backend'),
    [ValidateRange(1, 5)][int]$Runs = 2,
    [switch]$UseRunningStack
)
$ErrorActionPreference = 'Stop'
$appPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$BackendPath = [IO.Path]::GetFullPath($BackendPath)
$outputPath = Join-Path $appPath 'app/build/mvp'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$backendProcess = $null
$voiceProcess = $null
$composeStarted = $false
$composeProject = "routemate-mvp-$PID"
$composeFile = Join-Path $BackendPath 'compose.mvp.yaml'
$testApplicationId = 'com.routemate.app.mvpverify'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$testInstallAttempted = $false
$docker = if (Get-Command docker.exe -ErrorAction SilentlyContinue) { (Get-Command docker.exe).Source } else { 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' }
$savedEnvironment = @{}
$environmentNames = @('LIVEKIT_SERVER_URL','LIVEKIT_PUBLIC_URL','LIVEKIT_API_KEY','LIVEKIT_API_SECRET','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','REDIS_HOST','REDIS_PORT','REDIS_PASSWORD','MVP_DB_PASSWORD','MVP_REDIS_PASSWORD')
function Invoke-Gradle([string]$Directory, [string[]]$Arguments) {
    Push-Location $Directory
    try { & .\gradlew.bat @Arguments --console=plain --no-daemon; if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed' } }
    finally { Pop-Location }
}
function Wait-Health([string]$Url) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        try { if ((Invoke-RestMethod $Url -TimeoutSec 3).status -eq 'UP') { return } } catch { }
        if ($backendProcess -and $backendProcess.HasExited) { throw 'Backend exited; inspect app/build/mvp logs' }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Backend health timeout'
}
try {
    $sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $adb = Join-Path $sdkPath 'platform-tools/adb.exe'
    $devices = & $adb devices
    if (-not ($devices -match '^emulator-\d+\s+device$')) { throw 'Start one ordinary Android emulator before running this script' }
    if (@($devices | Where-Object { $_ -match '\s+device$' }).Count -ne 1) { throw 'Exactly one Android test device is required' }
    if (-not $UseRunningStack) {
        foreach ($port in @(7880,18080,15432,16379)) {
            $listener = [Net.Sockets.TcpClient]::new()
            try {
                $pending=$listener.BeginConnect('127.0.0.1',$port,$null,$null)
                if ($pending.AsyncWaitHandle.WaitOne(300) -and $listener.Connected) { throw "Port $port is already occupied; use -UseRunningStack or stop your own test stack" }
            } finally { $listener.Dispose() }
        }
        Invoke-Gradle $BackendPath @('test','bootJar')
        $executable = Join-Path $appPath 'app/build/voice-poc/server/livekit-server.exe'
        if (-not (Test-Path -LiteralPath $executable)) { throw 'Prepare the verified local LiveKit binary with tools/voice-poc/Start-Server.ps1 first' }
        $voiceProcess = Start-Process -FilePath $executable -ArgumentList '--dev','--bind','127.0.0.1' -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $outputPath 'livekit.out.log') -RedirectStandardError (Join-Path $outputPath 'livekit.err.log')
        foreach ($name in $environmentNames) { $savedEnvironment[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
        $env:MVP_DB_PASSWORD=[Guid]::NewGuid().ToString('N'); $env:MVP_REDIS_PASSWORD=[Guid]::NewGuid().ToString('N')
        $composeStarted=$true
        & $docker compose -f $composeFile -p $composeProject up -d --wait
        if($LASTEXITCODE -ne 0){throw 'Isolated database stack failed to start'}
        $env:DB_HOST='127.0.0.1'; $env:DB_PORT='15432'; $env:DB_NAME='routemate_mvp'; $env:DB_USER='routemate'; $env:DB_PASSWORD=$env:MVP_DB_PASSWORD
        $env:REDIS_HOST='127.0.0.1'; $env:REDIS_PORT='16379'; $env:REDIS_PASSWORD=$env:MVP_REDIS_PASSWORD
        # Public local --dev credentials only, inherited by the backend process and never the APK.
        $env:LIVEKIT_SERVER_URL='http://127.0.0.1:7880'; $env:LIVEKIT_PUBLIC_URL='ws://10.0.2.2:7880'
        $env:LIVEKIT_API_KEY='devkey'; $env:LIVEKIT_API_SECRET='secret'
        $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
        $jar = Join-Path $BackendPath 'build/libs/routemate-backend-0.0.1-SNAPSHOT.jar'
        $backendProcess = Start-Process -FilePath $java -ArgumentList '-jar',('"'+$jar+'"'),'--server.port=18080','--server.address=127.0.0.1','--routemate.vehicle.allow-mock=true','--debug=false' -WorkingDirectory $BackendPath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $outputPath 'backend.out.log') -RedirectStandardError (Join-Path $outputPath 'backend.err.log')
    }
    Wait-Health 'http://127.0.0.1:18080/actuator/health'
    Invoke-Gradle $appPath @('testDebugUnitTest','lintDebug','assembleDebug','-PbackendUrl=http://10.0.2.2:18080',"-PmvpTestApplicationId=$testApplicationId")
    for ($iteration=1; $iteration -le $Runs; $iteration++) {
        Write-Host "MVP actual connection run $iteration/$Runs"
        $arguments=@('connectedDebugAndroidTest','-PbackendUrl=http://10.0.2.2:18080',"-PmvpTestApplicationId=$testApplicationId",'-Pandroid.testInstrumentationRunnerArguments.mvpE2E=true','-Pandroid.testInstrumentationRunnerArguments.tripE2E=true')
        if ($iteration -gt 1) { $arguments += '-Pandroid.testInstrumentationRunnerArguments.class=com.routemate.app.MvpConnectionTest,com.routemate.app.MvpUiConnectionTest' }
        $testInstallAttempted=$true
        $iterationStarted=Get-Date
        try { Invoke-Gradle $appPath $arguments }
        finally {
            $reports=Join-Path $appPath 'app/build/outputs/androidTest-results/connected'
            $fresh=Get-ChildItem -LiteralPath $reports -Recurse -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | Where-Object {$_.LastWriteTime -ge $iterationStarted}
            if($fresh){Copy-Item -LiteralPath $reports -Destination (Join-Path $outputPath ("run-$iteration-"+(Get-Date -Format 'yyyyMMdd-HHmmss'))) -Recurse}
        }
    }
    Write-Host 'MVP verification passed'
} finally {
    # Process objects refer only to children started by this invocation. Never stop an existing stack/device.
    foreach ($process in @($backendProcess,$voiceProcess)) { if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id } }
    if($testInstallAttempted){ & $adb uninstall ($testApplicationId+'.test') 2>$null | Out-Null; & $adb uninstall $testApplicationId 2>$null | Out-Null }
    if($composeStarted){ & $docker compose -f $composeFile -p $composeProject down; if($LASTEXITCODE -ne 0){Write-Warning 'Inspect the isolated MVP stack cleanup'} }
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name,$savedEnvironment[$name],'Process') }
}
