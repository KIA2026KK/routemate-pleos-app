[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Keystore,
    [string]$Alias='routemate',
    [ValidateSet('Pleos','Phone')][string]$Target='Pleos',
    [string]$BackendUrl='https://routemate-test-api.onrender.com',
    [switch]$CreateKey
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$keyPath=[IO.Path]::GetFullPath($Keystore)
if($keyPath.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Keep the keystore outside the repository.'}
if($BackendUrl -notmatch '^https://[^/]+/?$'){throw 'Use an HTTPS backend origin without credentials, query or path.'}
if(-not $env:JAVA_HOME){throw 'Set JAVA_HOME to a JDK 21 installation.'}
$sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}elseif($env:ANDROID_SDK_ROOT){$env:ANDROID_SDK_ROOT}else{Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
$tools=Join-Path $sdk 'build-tools/35.0.0'
if(-not(Test-Path (Join-Path $tools 'apksigner.bat'))){throw 'Install Android SDK build-tools;35.0.0.'}
function Read-Password([string]$Prompt){
    $secure=Read-Host $Prompt -AsSecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try{[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
    finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$secure.Dispose()}
}
$variables=@('ROUTEMATE_KEYSTORE','ROUTEMATE_KEY_ALIAS','ROUTEMATE_STORE_PASSWORD','ROUTEMATE_KEY_PASSWORD')
$previous=@{};foreach($name in $variables){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
Push-Location $root
try {
    $env:ROUTEMATE_KEYSTORE=$keyPath;$env:ROUTEMATE_KEY_ALIAS=$Alias
    $env:ROUTEMATE_STORE_PASSWORD=Read-Password 'Keystore password (local only)'
    if($env:ROUTEMATE_STORE_PASSWORD.Length -lt 6){throw 'Use a password of at least six characters.'}
    if($CreateKey){
        if(Test-Path -LiteralPath $keyPath){throw 'Keystore already exists; omit -CreateKey to reuse it.'}
        $confirmation=Read-Password 'Repeat keystore password'
        if($confirmation -cne $env:ROUTEMATE_STORE_PASSWORD){throw 'Passwords do not match.'}
        $confirmation=$null
        $env:ROUTEMATE_KEY_PASSWORD=$env:ROUTEMATE_STORE_PASSWORD
        New-Item -ItemType Directory -Path (Split-Path $keyPath) -Force | Out-Null
        & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $keyPath -storetype PKCS12 -alias $Alias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=RouteMate, O=RouteMate Team, C=KR' -storepass:env ROUTEMATE_STORE_PASSWORD -keypass:env ROUTEMATE_KEY_PASSWORD
        if($LASTEXITCODE -ne 0){throw 'Key generation failed.'}
    }else{
        if(-not(Test-Path -LiteralPath $keyPath -PathType Leaf)){throw 'Keystore not found. Use -CreateKey for a new key.'}
        $env:ROUTEMATE_KEY_PASSWORD=Read-Password 'Key password (for PKCS12, normally the keystore password)'
    }
    $provider=if($Target -eq 'Pleos'){'pleos'}else{'mock'}
    & ./gradlew.bat assembleRelease "-PbackendUrl=$BackendUrl" "-PdestinationProvider=$provider" "-PvehicleProvider=$provider" --console=plain --no-daemon --no-configuration-cache
    if($LASTEXITCODE -ne 0){throw 'Release build failed.'}
    $source=Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
    $cert=& "$tools/apksigner.bat" verify --verbose --print-certs $source
    if($LASTEXITCODE -ne 0){throw 'APK signature verification failed.'}
    if(($cert -join "`n") -match 'CN=Android Debug'){throw 'A debug signing certificate is not a submission key.'}
    $badging=& "$tools/aapt2.exe" dump badging $source
    if($LASTEXITCODE -ne 0){throw 'Cannot inspect APK.'}
    $tree=& "$tools/aapt2.exe" dump xmltree $source --file AndroidManifest.xml
    if($LASTEXITCODE -ne 0){throw 'Cannot inspect release manifest.'}
    if(($badging -join "`n") -match 'application-debuggable' -or ($tree -join "`n") -match '(debuggable|testOnly).*0xffffffff'){throw 'Debuggable or testOnly APK rejected.'}
    if(($badging -join "`n") -notmatch "package: name='com.routemate.app' "){throw 'Unexpected application ID.'}
    $folder=if($Target -eq 'Pleos'){Join-Path $root 'apk'}else{Join-Path $root 'app/build/distributions'}
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    $file=if($Target -eq 'Pleos'){'노래말고 사람들과 함께_RouteMate_release.apk'}else{'RouteMate-phone-test-release.apk'}
    $destination=Join-Path $folder $file
    Copy-Item -LiteralPath $source -Destination $destination
    $metadata=Get-Content 'app/build/outputs/apk/release/output-metadata.json' -Raw | ConvertFrom-Json
    $report=[ordered]@{file=$file;target=$Target;applicationId=$metadata.applicationId;versionName=$metadata.elements[0].versionName;versionCode=$metadata.elements[0].versionCode;bytes=(Get-Item $destination).Length;sha256=(Get-FileHash $destination -Algorithm SHA256).Hash;backendUrl=$BackendUrl;destinationProvider=$provider;vehicleProvider=$provider;debuggable=$false;testOnly=$false;signatureVerified=$true;certificateSha256=(@($cert|Where-Object {$_ -match '^Signer #1 certificate SHA-256 digest:'}) -join '');sourceCommit=(git -c "safe.directory=$($root.Replace('\','/'))" rev-parse HEAD);worktreeDirty=[bool](git -c "safe.directory=$($root.Replace('\','/'))" status --porcelain --untracked-files=no);runtimeVerification='Pending final signed release installation and physical Pleos/audio tests'}
    $report|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $folder ($file+'.json')) -Encoding UTF8
    $report.sha256+'  '+$file | Set-Content -LiteralPath (Join-Path $folder ($file+'.sha256')) -Encoding ASCII
    Write-Output ('Signed release: '+$destination)
    Write-Output ('Bytes: '+$report.bytes+'; SHA-256: '+$report.sha256)
    if($report.bytes -gt 100MB){Write-Output 'Over 100 MiB: use a GitHub Release asset instead of git add.'}
    Write-Output 'No upload performed. Install and test this exact signed APK before submission.'
} finally {
    foreach($name in $variables){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
    $confirmation=$null
    Pop-Location
}
