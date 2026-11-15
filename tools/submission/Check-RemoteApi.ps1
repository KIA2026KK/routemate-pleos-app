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
    $health=Request 'GET' '/actuator/health'
    Check 'health UP' ($health.status -eq 200 -and $health.data.status -eq 'UP')
    $r=Request 'GET' '/api/v1/rooms/me'
    Check 'missing bearer rejected' ($r.status -eq 401 -and $r.data.code -eq 'UNAUTHORIZED')
    $r=Request 'GET' '/api/v1/trips/me' 'invalid-test-bearer'
    Check 'invalid bearer rejected' ($r.status -eq 401 -and $r.cache -like '*no-store*')
    $seeker=Driver $true 31.3
    $near=Driver $true 31.31
    $far=Driver $true 31.34
    $private=Driver $true 31.31
    $empty=Driver $false 31.3
    $r=Request 'POST' '/api/v1/rooms' $empty.token @{name='No trip';isPublic=$true;capacity=2}
    Check 'room requires active trip' ($r.status -eq 428 -and $r.data.code -eq 'TRIP_REQUIRED')
    $r=Request 'POST' '/api/v1/trips' $seeker.token @{destination=@{name='Invalid latitude';latitude=91;longitude=123.4}}
    Check 'invalid coordinates rejected' ($r.status -eq 400)
    $r=Request 'GET' '/api/v1/rooms/nearby?radiusKm=7' $seeker.token
    Check 'unsupported radius rejected' ($r.status -eq 400)
    $r=Request 'POST' '/api/v1/rooms' $seeker.token @{name='Invalid capacity';isPublic=$true;capacity=9}
    Check 'invalid capacity rejected' ($r.status -eq 400)
    $r=Request 'POST' '/api/v1/rooms' $seeker.token @{name='Injected identity';isPublic=$true;capacity=3;userId=[Guid]::NewGuid().ToString()}
    Check 'client identity injection rejected' ($r.status -eq 400)
    $nearRoom=Request 'POST' '/api/v1/rooms' $near.token @{name='Precheck near';isPublic=$true;capacity=2}
    $farRoom=Request 'POST' '/api/v1/rooms' $far.token @{name='Precheck far';isPublic=$true;capacity=3}
    $privateRoom=Request 'POST' '/api/v1/rooms' $private.token @{name='Precheck private';isPublic=$false;capacity=2}
    Check 'own public and private fixtures created' ($nearRoom.status -eq 200 -and $farRoom.status -eq 200 -and $privateRoom.status -eq 200)
    $nearId=$nearRoom.data.id;$farId=$farRoom.data.id;$privateId=$privateRoom.data.id
    $five=Request 'GET' '/api/v1/rooms/nearby?radiusKm=3' $seeker.token
    $ten=Request 'GET' '/api/v1/rooms/nearby?radiusKm=5' $seeker.token
    Check '3km matches final destination' ($five.status -eq 200 -and @($five.data.room.id) -contains $nearId -and @($five.data.room.id) -notcontains $farId)
    Check '5km includes farther destination' ($ten.status -eq 200 -and @($ten.data.room.id) -contains $farId)
    Check 'private room absent from searches' (@($ten.data.room.id) -notcontains $privateId)
    Check 'public results omit VIN and exact coordinates' ($ten.raw -notmatch '"(vin|latitude|longitude|userId|vehicleId|center)"')
    $distance=($five.data|Where-Object {$_.room.id -eq $nearId}).distanceMeters
    Check 'distance uses approximate 1km buckets' ($distance -ge 1000 -and $distance -le 3000 -and $distance % 1000 -eq 0)
    $r=Request 'POST' '/api/v1/voice/token' $seeker.token @{roomId=$nearId}
    Check 'non-member cannot obtain voice token' ($r.status -eq 403)
    $r=Request 'POST' "/api/v1/rooms/$nearId/join" $seeker.token
    Check 'join reaches capacity' ($r.status -eq 200 -and $r.data.participants -eq 2)
    $r=Request 'POST' "/api/v1/rooms/$nearId/join" $seeker.token
    Check 'duplicate join is idempotent' ($r.status -eq 200 -and $r.data.participants -eq 2)
    $r=Request 'POST' "/api/v1/rooms/$nearId/join" $far.token
    Check 'existing participant cannot join second room' ($r.status -eq 409 -and $r.data.code -eq 'ALREADY_IN_ROOM')
    $null=Request 'POST' "/api/v1/rooms/$farId/leave" $far.token
    $r=Request 'POST' "/api/v1/rooms/$nearId/join" $far.token
    Check 'full room rejects additional participant' ($r.status -eq 409 -and $r.data.code -eq 'ROOM_FULL')
    $r=Request 'GET' '/api/v1/rooms/nearby?radiusKm=5' $far.token
    Check 'full room hidden from search' (@($r.data.room.id) -notcontains $nearId)
    $r=Request 'POST' "/api/v1/rooms/$nearId/heartbeat" $seeker.token
    Check 'heartbeat preserves membership' ($r.status -eq 200 -and $r.data.participants -eq 2)
    $r=Request 'POST' '/api/v1/voice/token' $seeker.token @{roomId=$nearId}
    Check 'voice grant from server only and no-store' ($r.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($r.data.token) -and $r.data.url -eq 'wss://routemate-test-eqrjn99b.livekit.cloud' -and $r.cache -like '*no-store*')
    $r=$null
    $old=$seeker.trip
    $body=@{destination=@{name='Revision update';latitude=31.3;longitude=123.4};precondition=@{tripId=$old.tripId;revision=$old.revision}}
    $updated=Request 'POST' '/api/v1/trips' $seeker.token $body
    Check 'destination update preserves trip identity' ($updated.status -eq 200 -and $updated.data.tripId -eq $old.tripId -and $updated.data.revision -eq ($old.revision+1))
    $r=Request 'POST' '/api/v1/trips' $seeker.token $body
    Check 'stale destination update rejected' ($r.status -eq 409 -and $r.data.code -eq 'TRIP_CHANGED')
    $r=Request 'POST' '/api/v1/trips/me/end' $seeker.token
    Check 'trip end succeeds' ($r.status -eq 200 -and $r.data.ended)
    $r=Request 'GET' '/api/v1/rooms/me' $seeker.token
    Check 'trip end clears room membership' ($r.status -eq 200 -and $null -eq $r.data)
    $r=Request 'POST' '/api/v1/voice/token' $seeker.token @{roomId=$nearId}
    Check 'ended trip cannot obtain voice token' ($r.status -eq 403)
    $r=Request 'POST' '/api/v1/trips/me/end' $seeker.token
    Check 'duplicate trip end is idempotent' ($r.status -eq 200)
    $null=Request 'POST' "/api/v1/rooms/$nearId/leave" $near.token
    $r=Request 'POST' "/api/v1/rooms/$nearId/join" $far.token
    Check 'closed empty room cannot be rejoined' ($r.status -eq 404)
} finally {
    $cleaned=0
    foreach($d in $drivers){
        try {$r=Request 'POST' '/api/v1/trips/me/end' $d.token;if($r.status -eq 200){$cleaned++}} catch {Write-Warning 'Synthetic trip cleanup failed'}
    }
    $report=@{checks=@($results.ToArray());passed=@($results|Where-Object {$_.passed}).Count;failed=@($results|Where-Object {-not $_.passed}).Count;fixtures=$drivers.Count;endedTrips=$cleaned;backend=$base;finishedAtUtc=[DateTime]::UtcNow.ToString('o')}
    $report|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $PSScriptRoot '../../app/build/submission/remote-api-3-5-precheck.json') -Encoding UTF8
    Write-Host ('Summary: '+([pscustomobject]$report|Select-Object passed,failed,fixtures,endedTrips|ConvertTo-Json -Compress))
}
