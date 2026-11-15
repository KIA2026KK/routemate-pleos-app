$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot '../../app/build/submission') -Force | Out-Null
$base='https://routemate-test-api.onrender.com'
$results=[Collections.Generic.List[object]]::new()
$drivers=[Collections.Generic.List[object]]::new()
function Request([string]$method,[string]$path,[string]$token='',$body=$null) {
    $args=@{Uri=($base+$path);Method=$method;TimeoutSec=35;UseBasicParsing=$true}
    if($token){$args.Headers=@{Authorization=('Bearer '+$token)}}
    if($null -ne $body){$args.ContentType='application/json';$args.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 10 -Compress))}
    try {
        $response=Invoke-WebRequest @args
        $content=$response.Content
        $status=[int]$response.StatusCode
        $cache=$response.Headers['Cache-Control']
    } catch {
        if(-not $_.Exception.Response){throw 'Remote API transport failure'}
        $response=$_.Exception.Response
        $status=[int]$response.StatusCode
        $cache=$response.Headers['Cache-Control']
        $reader=[IO.StreamReader]::new($response.GetResponseStream())
        try {$content=$reader.ReadToEnd()} finally {$reader.Dispose()}
    }
    if($content -is [byte[]]){$content=[Text.Encoding]::UTF8.GetString($content)}
    $json=if([string]::IsNullOrWhiteSpace($content)){$null}else{ConvertFrom-Json -InputObject $content}
    @{status=$status;data=$json;cache=$cache;raw=$content}
}
function Check([string]$name,[bool]$passed) {
    $results.Add(@{name=$name;passed=$passed})
    Write-Host ($name+': '+$passed)
    if(-not $passed){throw ('Check failed: '+$name)}
}
function Driver([bool]$trip,[double]$lat) {
    $session=Request 'POST' '/api/v1/auth/device-sessions'
    if($session.status -ne 201){throw 'Session creation failed'}
    $d=@{token=$session.data.accessToken;trip=$null}
    $drivers.Add($d)
    $vehicle=Request 'POST' '/api/v1/vehicles/me' $d.token @{vin=('KNA'+[Guid]::NewGuid().ToString('N').Substring(0,14));source='MOCK';vinAccessType=$null}
    if($vehicle.status -ne 200){throw 'Synthetic vehicle registration failed'}
    if($trip){
        $r=Request 'POST' '/api/v1/trips' $d.token @{destination=@{name='Final precheck';latitude=$lat;longitude=123.4}}
        if($r.status -ne 200){throw 'Synthetic trip registration failed'}
        $d.trip=$r.data
    }
    $d
}
try {
 $a=Driver $true 35.1374; $b=Driver $true 35.138; $c=Driver $true 35.139; $d=Driver $true 35.139
 $old=Request 'POST' '/api/v1/rooms' $a.token @{name='Invitation precheck';isPublic=$false;capacity=4}
 $target=Request 'POST' '/api/v1/rooms' $c.token @{name='Switch precheck';isPublic=$false;capacity=2}
 $oldId=$old.data.id; $targetId=$target.data.id
 Check 'eight-character invitation returned' ($old.status -eq 200 -and $old.data.inviteCode -match '^[A-Z2-9]{8}$')
 $r=Request 'POST' '/api/v1/rooms/join' $b.token @{code=(' '+$old.data.inviteCode.ToLower()+' ')}
 Check 'private room code join' ($r.status -eq 200 -and $r.data.participants -eq 2)
 $r=Request 'POST' '/api/v1/rooms/join' $b.token @{code=$old.data.inviteCode}
 Check 'code retry does not duplicate membership' ($r.status -eq 200 -and $r.data.participants -eq 2)
 $null=Request 'POST' ('/api/v1/rooms/'+$targetId+'/join') $d.token
 $r=Request 'POST' '/api/v1/rooms/join' $b.token @{code=$target.data.inviteCode;replaceCurrent=$true}
 Check 'full target rejects switch' ($r.status -eq 409 -and $r.data.code -eq 'ROOM_FULL')
 $r=Request 'GET' '/api/v1/rooms/me' $b.token
 Check 'rejected switch retains original room' ($r.data.id -eq $oldId)
 $r=Request 'POST' '/api/v1/voice/token' $b.token @{roomId=$oldId}
 Check 'original room voice authorization retained' ($r.status -eq 200 -and $r.data.token)
 $r=$null
 $null=Request 'POST' ('/api/v1/rooms/'+$targetId+'/leave') $d.token
 $r=Request 'POST' '/api/v1/rooms/join' $b.token @{code=$target.data.inviteCode;replaceCurrent=$true}
 Check 'code switch succeeds after seat available' ($r.status -eq 200 -and $r.data.id -eq $targetId -and $r.data.participants -eq 2)
 $r=Request 'GET' '/api/v1/rooms/me' $a.token
 Check 'old room participant removed' ($r.data.participants -eq 1)
 $r=Request 'POST' ('/api/v1/rooms/'+$oldId+'/switch') $b.token
 Check 'UUID switch remains supported' ($r.status -eq 200 -and $r.data.id -eq $oldId -and $r.data.participants -eq 2)
 $r=Request 'GET' '/api/v1/rooms/me' $c.token
 Check 'single-room membership after switch' ($r.data.participants -eq 1)
 $r=Request 'POST' '/api/v1/rooms/join' $b.token @{code='ZZZZZZZZZZZZZZZZ';replaceCurrent=$true}
 Check 'invalid destination code rejected' ($r.status -eq 404)
 $r=Request 'GET' '/api/v1/rooms/me' $b.token
 Check 'unknown code preserves current room' ($r.data.id -eq $oldId)
 $r=Request 'POST' '/api/v1/rooms/join' $b.token @{code=$target.data.inviteCode}
 Check 'ordinary join cannot add second room' ($r.status -eq 409 -and $r.data.code -eq 'ALREADY_IN_ROOM')
} finally {
 $cleaned=0
 foreach($item in $drivers){try{$r=Request 'POST' '/api/v1/trips/me/end' $item.token;if($r.status -eq 200){$cleaned++}}catch{}}
 [pscustomobject]@{checks=$results.ToArray();passed=@($results|Where-Object passed).Count;failed=@($results|Where-Object {-not $_.passed}).Count;fixtures=$drivers.Count;endedTrips=$cleaned}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $PSScriptRoot '../../app/build/submission/invite-switch-result.json') -Encoding UTF8
 Write-Host ('Cleanup: '+$cleaned+'/'+$drivers.Count)
}