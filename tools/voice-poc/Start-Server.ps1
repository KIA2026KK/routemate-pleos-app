param([switch]$AllowLan)
$ErrorActionPreference = 'Stop'
$version = '1.13.11'
$directory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../app/build/voice-poc'))
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$archive = Join-Path $directory 'livekit.zip'
$serverDirectory = Join-Path $directory 'server'
$executable = Join-Path $serverDirectory 'livekit-server.exe'
if (-not (Test-Path -LiteralPath $executable)) {
    Invoke-WebRequest -UseBasicParsing "https://github.com/livekit/livekit/releases/download/v$version/livekit_${version}_windows_amd64.zip" -OutFile $archive
    $expected = '5d071ea43339f0140d24a66f0b2ff8a7bf6c4d0dceb19b30abca835670213667'
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
        throw 'LiveKit archive checksum mismatch'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $serverDirectory -Force
}
# Development credentials from LiveKit --dev. Never use on a public server.
$bindAddress = if ($AllowLan) { '0.0.0.0' } else { '127.0.0.1' }
& $executable --dev --bind $bindAddress
if ($LASTEXITCODE -ne 0) { throw 'LiveKit server stopped with an error' }
