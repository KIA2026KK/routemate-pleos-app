param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_-]{1,64}$')][string]$Identity,
    [ValidatePattern('^[a-zA-Z0-9_-]{1,64}$')][string]$Room = 'routemate-poc',
    [ValidateRange(1, 60)][int]$Minutes = 15
)
$ErrorActionPreference = 'Stop'
# Local --dev server only. No API secret is ever shipped in the APK.
function ConvertTo-Base64Url([byte[]]$Bytes) {
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$header = @{alg = 'HS256'; typ = 'JWT'} | ConvertTo-Json -Compress
$payload = @{
    iss = 'devkey'; sub = $Identity; nbf = $now - 5; exp = $now + $Minutes * 60
    video = @{
        roomJoin = $true; room = $Room; canSubscribe = $true
        canPublish = $true; canPublishSources = @('microphone'); canPublishData = $false
    }
} | ConvertTo-Json -Depth 5 -Compress
$unsigned = (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))) + '.' +
    (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload)))
$hmac = New-Object Security.Cryptography.HMACSHA256
try {
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes('secret')
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
    Write-Output "$unsigned.$signature"
} finally { $hmac.Dispose() }
