# 검사 전용 서버에서만 실행한다. 계정·프로젝트를 추가하며 기존 데이터를 삭제하지 않는다.
param([string]$BaseUrl='http://localhost:19040/api')
$ErrorActionPreference='Stop'
$checks=[System.Collections.Generic.List[string]]::new()
function Request($method,$path,$session,$body,$expected) {
    $args=@{Method=$method;Uri="$BaseUrl$path";SkipHttpErrorCheck=$true}
    if($session){$args.WebSession=$session}
    if($null -ne $body){$args.ContentType='application/json';$args.Body=[System.Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 8 -Compress))}
    $response=Invoke-WebRequest @args
    if([int]$response.StatusCode -ne $expected){throw "$method $path expected=$expected actual=$($response.StatusCode)"}
    $checks.Add("$method $expected")
    # 시각 문자열을 그대로 비교해 저장·재조회 정밀도 차이도 검출한다.
    if($response.Content){return $response.Content|ConvertFrom-Json -DateKind String}
}
$stamp=[guid]::NewGuid().ToString('N');$sessions=@{}
foreach($role in @('leader','member','observer','outsider')) {
    $s=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $null=Request POST '/auth/signup' $s @{email="$role-$stamp@example.com";name="검사$role";password='TestOnlyPassword2026!'} 201
    $sessions[$role]=$s
}
$leader=$sessions.leader;$member=$sessions.member;$observer=$sessions.observer
$p=Request POST '/projects' $leader @{name="B20검사-$stamp"} 201
$null=Request POST '/projects/join' $member @{inviteCode=$p.inviteCode} 201
$om=Request POST '/projects/join' $observer @{inviteCode=$p.inviteCode} 201
$null=Request PATCH "/projects/$($p.id)/members/$($om.memberId)/role" $leader @{role='OBSERVER'} 200
$d=Request POST "/projects/$($p.id)/deliverables" $leader @{title='중간 보고서';dueDate='2026-10-18'} 201
$root="/projects/$($p.id)/deliverables/$($d.id)/reviews"
$r=Request POST $root $leader @{} 201;$path="$root/$($r.id)/comments"
$null=Request GET $path $observer $null 200
$c=Request POST $path $member @{content='  출처를 추가해주세요  '} 201
if($c.content -ne '출처를 추가해주세요' -or $c.status -ne 'OPEN' -or $null -ne $c.resolution){throw 'create response mismatch'}
$null=Request POST $path $observer @{content='관찰자 쓰기'} 403
$null=Request GET $path $sessions.outsider $null 403
$null=Request GET $path $null $null 401
$null=Request POST $path $member @{content='  '} 400
$null=Request PUT "$root/$($r.id)/decision" $leader @{decision='APPROVED'} 409
$resolved=Request PUT "$path/$($c.id)/resolution" $leader @{reason=' 출처 반영 '} 200
$duplicate=Request PUT "$path/$($c.id)/resolution" $member @{reason='덮어쓰기'} 200
if([datetimeoffset]$duplicate.resolution.resolvedAt -ne [datetimeoffset]$resolved.resolution.resolvedAt -or $duplicate.resolution.reason -ne '출처 반영'){throw 'idempotency failed'}
$null=Request DELETE "$path/$($c.id)/resolution" $member $null 200
$count=Request GET $root $observer $null 200
if($count.unresolvedCommentCount -ne 1){throw 'real count failed'}
$null=Request PUT "$path/$($c.id)/resolution" $member @{} 200
$null=Request PUT "$root/$($r.id)/decision" $leader @{decision='APPROVED'} 200
$null=Request POST $path $member @{content='승인 뒤 쓰기'} 409
$null=Request DELETE "$path/$($c.id)/resolution" $member $null 409
$null=Request GET "$root/$([guid]::NewGuid())/comments" $member $null 404
$null=Request DELETE "$path/wrong/resolution" $member $null 400
$null=Request POST $root $leader @{} 201
$null=Request POST $path $member @{content='과거 회차'} 409
$count=Request GET $root $leader $null 200
if($count.unresolvedCommentCount -ne 0){throw 'resolved count failed'}
[pscustomobject]@{httpChecks=$checks.Count;passed=$true;projectId=$p.id;deliverableId=$d.id}|ConvertTo-Json -Compress
