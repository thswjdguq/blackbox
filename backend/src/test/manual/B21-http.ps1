# B20과 같은 전용 검사 서버에서 실행한다. 기본 흐름의 계정·세션을 재사용한다.
. "$PSScriptRoot/B20-http.ps1"
$before=$checks.Count
$latest=(Request GET $root $leader $null 200).rounds[0]
$commentPath="$root/$($latest.id)/comments"
$feedback=Request POST $commentPath $member @{content='출처를 업무로 보완'} 201
$convertPath="$commentPath/$($feedback.id)/task"
$created=Request POST $convertPath $member @{title=' 출처 보완 ';completionCriteria=' 검증 완료 '} 201
$duplicate=Request POST $convertPath $member @{title='다른 제목'} 200
if(!$created.created -or $duplicate.created -or $created.taskId -ne $duplicate.taskId){throw 'task retry mismatch'}
$taskPath="/projects/$($p.id)/tasks/$($created.taskId)"
$task=Request GET $taskPath $leader $null 200
if($task.title -ne '출처 보완' -or $task.description -ne '출처를 업무로 보완' -or $task.status -ne 'TODO' -or $task.assignees.Count -ne 0 -or $task.deliverableId -ne $d.id -or $task.requirementId){throw 'created task mismatch'}
$null=Request POST $convertPath $observer @{title='권한 없음'} 403
$null=Request PATCH $taskPath $member @{clearDeliverable=$true} 409
$null=Request PATCH $taskPath $member @{deliverableId=$raceD.id} 409
$null=Request PATCH "$taskPath/status" $member @{status='DONE'} 200
$listed=Request GET $commentPath $observer $null 200
if(($listed|Where-Object id -eq $feedback.id).status -ne 'REFLECTION_PENDING'){throw 'completion must not resolve'}
$null=Request PUT "$commentPath/$($feedback.id)/resolution" $member @{reason='반영 확인'} 200
$null=Request DELETE $taskPath $member $null 204
$null=Request POST $convertPath $member @{title='해결 후 생성'} 409
$listed=Request GET $commentPath $observer $null 200
$deleted=$listed|Where-Object id -eq $feedback.id
if($deleted.linkedTaskId -or $deleted.status -ne 'RESOLVED' -or $deleted.resolution.reason -ne '반영 확인'){throw 'deletion lost resolution'}
$null=Request DELETE "$commentPath/$($feedback.id)/resolution" $member $null 200
$requests=1..8
$responses=$requests|ForEach-Object -Parallel {
    $response=Invoke-WebRequest -Uri "$using:BaseUrl$using:convertPath" -Method POST -WebSession $using:member -ContentType 'application/json' -Body '{"title":"동시 업무 생성"}' -SkipHttpErrorCheck
    $dto=$response.Content|ConvertFrom-Json
    [pscustomobject]@{status=[int]$response.StatusCode;taskId=$dto.taskId;created=$dto.created}
} -ThrottleLimit 8
if(($responses|Where-Object status -eq 201).Count -ne 1 -or ($responses|Where-Object status -eq 200).Count -ne 7 -or ($responses.taskId|Select-Object -Unique).Count -ne 1){throw 'concurrent conversion created duplicates'}
$newTaskId=$responses[0].taskId
$null=Request PUT "$commentPath/$($feedback.id)/resolution" $member @{} 200
$null=Request PUT "$root/$($latest.id)/decision" $leader @{decision='APPROVED'} 200
$null=Request POST $convertPath $member @{title='승인 뒤 재요청'} 200
$null=Request DELETE "/projects/$($p.id)/tasks/$newTaskId" $member $null 204
$null=Request POST $convertPath $member @{title='승인 뒤 새 생성'} 409
$deleteRaces=0
for($i=0;$i -lt 4;$i++) {
    $deleteD=Request POST "/projects/$($p.id)/deliverables" $leader @{title="삭제경쟁$i";dueDate='2026-10-18'} 201
    $deleteRoot="/projects/$($p.id)/deliverables/$($deleteD.id)/reviews"
    $deleteR=Request POST $deleteRoot $leader @{} 201
    $deleteComments="$deleteRoot/$($deleteR.id)/comments"
    $deleteC=Request POST $deleteComments $member @{content='삭제와 재생성'} 201
    $deleteConvert="$deleteComments/$($deleteC.id)/task"
    $oldTask=Request POST $deleteConvert $member @{title='삭제 대상 업무'} 201
    $requests=@(
        @{kind='delete';method='DELETE';path="/projects/$($p.id)/tasks/$($oldTask.taskId)"},
        @{kind='convert';method='POST';path=$deleteConvert}
    )
    $responses=$requests|ForEach-Object -Parallel {
        $args=@{Uri="$using:BaseUrl$($_.path)";Method=$_.method;WebSession=$using:member;SkipHttpErrorCheck=$true}
        if($_.kind -eq 'convert'){$args.ContentType='application/json';$args.Body='{"title":"재생성 업무"}'}
        $response=Invoke-WebRequest @args
        $dto=if($response.Content){$response.Content|ConvertFrom-Json}else{$null}
        [pscustomobject]@{kind=$_.kind;status=[int]$response.StatusCode;taskId=$dto.taskId}
    } -ThrottleLimit 2
    $deleted=$responses|Where-Object kind -eq 'delete';$converted=$responses|Where-Object kind -eq 'convert'
    if($deleted.status -ne 204 -or $converted.status -notin @(200,201)){throw 'delete/convert race unexpected status'}
    $list=Request GET $deleteComments $member $null 200
    if($converted.status -eq 200) {
        if($converted.taskId -ne $oldTask.taskId -or $list[0].linkedTaskId){throw 'existing task deletion policy mismatch'}
    } elseif($converted.taskId -eq $oldTask.taskId -or $list[0].linkedTaskId -ne $converted.taskId) {
        throw 'delete-first regeneration policy mismatch'
    }
    $null=Request GET "/projects/$($p.id)/tasks/$($oldTask.taskId)" $member $null 404
    $deleteRaces++
}
[pscustomobject]@{taskHttpChecks=($checks.Count-$before+8+$deleteRaces*2);concurrentConversions=8;deleteRacePairs=$deleteRaces;passed=$true}|ConvertTo-Json -Compress
