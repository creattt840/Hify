param(
    [int]$Port = 8080
)

$base = "http://localhost:$Port/api/v1"
$results = @()

function Add-Result($step, $pass, $detail) {
    $script:results += [pscustomobject]@{ Step = $step; Pass = $pass; Detail = $detail }
}

try {
    $r1 = Invoke-RestMethod -Uri "$base/knowledge-bases?page=1&pageSize=5" -Method Get -TimeoutSec 10
    Add-Result "list-kb" ($r1.code -eq 200) "total=$($r1.total)"
} catch {
    Add-Result "list-kb" $false $_.Exception.Message
    Write-Output "=== Port $Port - backend unreachable ==="
    exit 1
}

$kbBody = @{ name = "auto-test-$(Get-Date -Format 'HHmmss')"; description = "automated test" } | ConvertTo-Json
$kb = Invoke-RestMethod -Uri "$base/knowledge-bases" -Method Post -Body $kbBody -ContentType "application/json; charset=utf-8"
$kbId = $kb.data.id
Add-Result "create-kb" ($kb.code -eq 200 -and $kbId -gt 0) "id=$kbId"

$kbDetail = Invoke-RestMethod -Uri "$base/knowledge-bases/$kbId" -Method Get
Add-Result "get-kb" ($kbDetail.code -eq 200) "name=$($kbDetail.data.name)"

$updateBody = @{ name = "auto-test-updated"; description = "updated"; isEnabled = $true } | ConvertTo-Json
$kbUpd = Invoke-RestMethod -Uri "$base/knowledge-bases/$kbId" -Method Put -Body $updateBody -ContentType "application/json; charset=utf-8"
Add-Result "update-kb" ($kbUpd.code -eq 200) "name=$($kbUpd.data.name)"

$filePath = "E:\todo\docs\test-knowledge.txt"
$boundary = [System.Guid]::NewGuid().ToString()
$fileBytes = [System.IO.File]::ReadAllBytes($filePath)
$fileEnc = [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($fileBytes)
$LF = "`r`n"
$bodyLines = (
    "--$boundary",
    "Content-Disposition: form-data; name=`"file`"; filename=`"test-knowledge.txt`"",
    "Content-Type: text/plain",
    "",
    $fileEnc,
    "--$boundary--",
    ""
) -join $LF
$upload = Invoke-RestMethod -Uri "$base/knowledge-bases/$kbId/documents" -Method Post -Body $bodyLines -ContentType "multipart/form-data; boundary=$boundary"
$docId = $upload.data.id
Add-Result "upload-doc" ($upload.code -eq 200 -and $docId -gt 0) "docId=$docId status=$($upload.data.status)"

$finalStatus = "UNKNOWN"
$errorMsg = ""
$chunkCount = 0
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 3
    $doc = Invoke-RestMethod -Uri "$base/documents/$docId" -Method Get
    $finalStatus = $doc.data.status
    $errorMsg = $doc.data.errorMsg
    $chunkCount = $doc.data.chunkCount
    if ($finalStatus -in @("DONE", "FAILED")) { break }
}
Add-Result "process-doc" ($finalStatus -eq "DONE") "status=$finalStatus chunks=$chunkCount error=$errorMsg"

$chunks = Invoke-RestMethod -Uri "$base/documents/$docId/chunks" -Method Get
$apiChunkCount = if ($chunks.data) { $chunks.data.Count } else { 0 }
Add-Result "chunks-api" ($chunks.code -eq 200 -and $apiChunkCount -gt 0) "apiChunks=$apiChunkCount"

$env:PGPASSWORD = '123456'
$pgOut = & 'E:\postgresql\bin\psql.exe' -h localhost -U postgres -d hify_knowledge -p 5432 -t -A -c "SELECT COUNT(*) FROM t_knowledge_chunk WHERE document_id=$docId AND deleted=0;"
$pgCount = [int]($pgOut | Select-Object -First 1)
$pgEmbed = & 'E:\postgresql\bin\psql.exe' -h localhost -U postgres -d hify_knowledge -p 5432 -t -A -c "SELECT COUNT(*) FROM t_knowledge_chunk WHERE document_id=$docId AND deleted=0 AND embedding IS NOT NULL;"
$pgEmbedCount = [int]($pgEmbed | Select-Object -First 1)
Add-Result "pg-chunks" ($pgCount -gt 0) "pgChunks=$pgCount withEmbedding=$pgEmbedCount"

$providers = Invoke-RestMethod -Uri "$base/providers?page=1&pageSize=10" -Method Get
$openaiProviders = @($providers.data | Where-Object { $_.providerType -in @('openai','openai_compatible') })
Add-Result "embedding-provider" ($openaiProviders.Count -gt 0) "openaiCompatible=$($openaiProviders.Count)"

$delDoc = Invoke-RestMethod -Uri "$base/documents/$docId" -Method Delete
Add-Result "delete-doc" ($delDoc.code -eq 200) "docId=$docId"

$delKb = Invoke-RestMethod -Uri "$base/knowledge-bases/$kbId" -Method Delete
Add-Result "delete-kb" ($delKb.code -eq 200) "kbId=$kbId"

$pgAfterDel = & 'E:\postgresql\bin\psql.exe' -h localhost -U postgres -d hify_knowledge -p 5432 -t -A -c "SELECT COUNT(*) FROM t_knowledge_chunk WHERE kb_id=$kbId AND deleted=0;"
$pgAfterDelCount = [int]($pgAfterDel | Select-Object -First 1)
Add-Result "pg-cleanup" ($pgAfterDelCount -eq 0) "activeChunks=$pgAfterDelCount"

Write-Output "=== Knowledge Test Report (port $Port) ==="
$results | ForEach-Object {
    $mark = if ($_.Pass) { "PASS" } else { "FAIL" }
    Write-Output ("[{0}] {1} -> {2}" -f $mark, $_.Step, $_.Detail)
}
$passed = ($results | Where-Object { $_.Pass }).Count
Write-Output "=== Result: $passed/$($results.Count) passed ==="
