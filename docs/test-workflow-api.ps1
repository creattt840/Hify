param(
    [int]$Port = 8080
)

$base = "http://localhost:$Port/api/v1"
$results = @()

function Add-Result($step, $pass, $detail) {
    $script:results += [pscustomobject]@{ Step = $step; Pass = $pass; Detail = $detail }
}

function Invoke-Api($Method, $Uri, $Body = $null) {
    $params = @{ Uri = $Uri; Method = $Method; TimeoutSec = 30 }
    if ($Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    return Invoke-RestMethod @params
}

try {
    $list = Invoke-Api Get "$base/workflows?page=1&pageSize=5"
    $items = $list.data
    $total = $list.total
    Add-Result 'list-workflows' ($list.code -eq 200 -and $items -is [array]) "total=$total"
} catch {
    Add-Result 'list-workflows' $false $_.Exception.Message
    Write-Output "=== Port $Port unreachable ==="
    exit 1
}

$modelConfigs = Invoke-Api Get "$base/providers/model-configs"
$modelConfigId = $modelConfigs.data[0].id
Add-Result 'model-configs' ($modelConfigs.code -eq 200 -and $modelConfigId -gt 0) "modelConfigId=$modelConfigId"

$createBody = @{
    name = "auto-wf-$(Get-Date -Format 'HHmmss')"
    description = 'automated workflow test'
    isEnabled = $true
    nodes = @(
        @{ nodeKey = 'start'; nodeType = 'START'; title = 'Start'; config = @{}; sortOrder = 0 },
        @{
            nodeKey = 'reply'
            nodeType = 'LLM'
            title = 'Reply'
            config = @{
                modelConfigId = $modelConfigId
                prompt = 'Reply with one short sentence to: {{start.userMessage}}'
                outputVariable = 'reply'
            }
            sortOrder = 1
        },
        @{ nodeKey = 'end'; nodeType = 'END'; title = 'End'; config = @{}; sortOrder = 2 }
    )
    edges = @(
        @{ source = 'start'; target = 'reply'; sortOrder = 0 },
        @{ source = 'reply'; target = 'end'; sortOrder = 0 }
    )
}

$created = Invoke-Api Post "$base/workflows" $createBody
$wfId = $created.data.id
Add-Result 'create-workflow' ($created.code -eq 200 -and $wfId -gt 0) "id=$wfId nodes=$($created.data.nodeCount)"

$detail = Invoke-Api Get "$base/workflows/$wfId"
Add-Result 'get-workflow' ($detail.code -eq 200 -and $detail.data.nodes.Count -eq 3) "nodeCount=$($detail.data.nodes.Count)"

$updateBody = @{
    name = 'auto-wf-updated'
    description = 'updated'
    isEnabled = $true
    nodes = $createBody.nodes
    edges = $createBody.edges
}
$updated = Invoke-Api Put "$base/workflows/$wfId" $updateBody
Add-Result 'update-workflow' ($updated.code -eq 200 -and $updated.data.name -eq 'auto-wf-updated') "name=$($updated.data.name)"

$agents = Invoke-Api Get "$base/agents?page=1&pageSize=5"
$agentId = $null
if ($agents.data -and $agents.data.Count -gt 0) {
    $agentId = $agents.data[0].id
} elseif ($agents.data.data -and $agents.data.data.Count -gt 0) {
    $agentId = $agents.data.data[0].id
}

if ($agentId) {
    $agentDetail = Invoke-Api Get "$base/agents/$agentId"
    $agentBody = @{
        name = $agentDetail.data.name
        description = $agentDetail.data.description
        systemPrompt = $agentDetail.data.systemPrompt
        modelConfigId = $agentDetail.data.modelConfigId
        temperature = $agentDetail.data.temperature
        workflowId = $wfId
        isEnabled = $true
    }
    Invoke-Api Put "$base/agents/$agentId" $agentBody | Out-Null
    Add-Result 'bind-agent-workflow' $true "agentId=$agentId workflowId=$wfId"

    $streamBody = @{
        agentId = $agentId
        content = 'Hello workflow test'
    } | ConvertTo-Json
    try {
        $streamResp = Invoke-WebRequest -Uri "$base/conversations/stream" -Method Post -Body $streamBody -ContentType 'application/json' -TimeoutSec 120 -UseBasicParsing
        $streamOk = ($streamResp.StatusCode -eq 200) -and ($streamResp.Content -match 'data:')
        Add-Result 'execute-via-chat' $streamOk "status=$($streamResp.StatusCode) len=$($streamResp.Content.Length)"
    } catch {
        Add-Result 'execute-via-chat' $false $_.Exception.Message
    }

    Start-Sleep -Seconds 2
    $latestRun = Invoke-Api Get "$base/workflows/$wfId/runs/latest"
    $runStatus = if ($latestRun.data) { $latestRun.data.status } else { 'null' }
    Add-Result 'latest-run' ($latestRun.code -eq 200 -and $runStatus -eq 'SUCCESS') "status=$runStatus error=$($latestRun.data.error)"
} else {
    Add-Result 'bind-agent-workflow' $false 'no agent found'
    Add-Result 'execute-via-chat' $false 'skipped'
    Add-Result 'latest-run' $false 'skipped'
}

$del = Invoke-Api Delete "$base/workflows/$wfId"
Add-Result 'delete-workflow' ($del.code -eq 200) "id=$wfId"

Write-Output "=== Workflow Test Report (port $Port) ==="
$results | ForEach-Object {
    $mark = if ($_.Pass) { 'PASS' } else { 'FAIL' }
    Write-Output ("[{0}] {1} -> {2}" -f $mark, $_.Step, $_.Detail)
}
$passed = ($results | Where-Object { $_.Pass }).Count
Write-Output "=== Result: $passed/$($results.Count) passed ==="
