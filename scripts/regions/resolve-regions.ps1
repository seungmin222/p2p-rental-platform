param([int]$Limit = 0)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$envFile = Join-Path $root 'infra/.env'
$outDir = Join-Path $root 'build/region-seed'
$utf8 = New-Object Text.UTF8Encoding($false)
$invariant = [Globalization.CultureInfo]::InvariantCulture
$apiKey = [Environment]::GetEnvironmentVariable('KAKAO_REST_API_KEY')
if ([string]::IsNullOrWhiteSpace($apiKey) -and (Test-Path $envFile)) {
    foreach ($line in [IO.File]::ReadAllLines($envFile)) {
        if ($line -match '^\s*KAKAO_REST_API_KEY\s*=\s*(.*?)\s*$') {
            $apiKey = $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
}
if ([string]::IsNullOrWhiteSpace($apiKey)) { throw 'KAKAO_REST_API_KEY is missing from infra/.env.' }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$cacheFile = Join-Path $outDir 'resolved.jsonl'
$failedFile = Join-Path $outDir 'unresolved.csv'
$inputFile = Join-Path $PSScriptRoot 'administrative-regions-20261026.csv'
$rows = @(Import-Csv -LiteralPath $inputFile -Encoding UTF8)
$known = @{}
foreach ($row in $rows) { $known[$row.code] = $row }
$resolved = @{}
if (Test-Path $cacheFile) {
    foreach ($line in [IO.File]::ReadAllLines($cacheFile)) {
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            try { $record = $line | ConvertFrom-Json } catch { throw 'Cache is damaged. Keep resolved.jsonl and ask for recovery.' }
            if ($known.ContainsKey([string]$record.code) -and $record.source -eq 'Kakao H center') {
                $resolved[[string]$record.code] = $record
            }
        }
    }
}
function Invoke-Kakao([string]$Path, [hashtable]$Parameters) {
    $query = ($Parameters.GetEnumerator() | ForEach-Object {
        [Uri]::EscapeDataString([string]$_.Key) + '=' + [Uri]::EscapeDataString([string]$_.Value)
    }) -join '&'
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        Start-Sleep -Milliseconds 120
        try {
            return Invoke-RestMethod -Uri ('https://dapi.kakao.com' + $Path + '?' + $query) `
                -Headers @{Authorization = 'KakaoAK ' + $apiKey} -TimeoutSec 20
        } catch {
            $status = 0
            if ($null -ne $_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
            if ($status -eq 401 -or $status -eq 403) { throw "Kakao HTTP $status. Check REST key and Kakao Map API activation. Key was not printed." }
            if ($status -eq 400) { throw 'Kakao HTTP 400. Stop and check request format.' }
            if ($attempt -eq 2) { throw "Kakao HTTP $status / connection error after retries. Saved progress is kept; rerun later." }
            Start-Sleep -Seconds (2 * ($attempt + 1))
        }
    }
}
function Find-Center($row) {
    $address = (@($row.sido, $row.sigungu, $row.name) | Where-Object { $_ }) -join ' '
    $seen = @{}
    # Expand search wording only; exact official H-code verification below is unchanged.
    $shortName = $row.name -replace '제(?=\d)', ''
    $shortAddress = (@($row.sido, $row.sigungu, $shortName) | Where-Object { $_ }) -join ' '
    $queries = New-Object 'System.Collections.Generic.List[object]'
    foreach ($variant in @($address, $shortAddress) | Select-Object -Unique) {
        $queries.Add([pscustomobject]@{mode='address';query=$variant})
        foreach ($suffix in @(' 주민센터', ' 행정복지센터', '')) {
            $queries.Add([pscustomobject]@{mode='keyword';query=($variant + $suffix)})
        }
    }
    foreach ($search in $queries) {
        $mode = $search.mode
        $query = $search.query
        $body = Invoke-Kakao ('/v2/local/search/' + $mode + '.json') @{query = $query; size = '10'}
        foreach ($candidate in @($body.documents)) {
            if ($null -eq $candidate -or -not $candidate.x -or -not $candidate.y) { continue }
            $point = ([string]$candidate.x) + ',' + ([string]$candidate.y)
            if ($seen.ContainsKey($point)) { continue }
            $seen[$point] = $true
            $geo = Invoke-Kakao '/v2/local/geo/coord2regioncode.json' @{x = $candidate.x; y = $candidate.y}
            foreach ($region in @($geo.documents)) {
                if ($region.region_type -ne 'H' -or [string]$region.code -ne [string]$row.code) { continue }
                $lat = [double]::Parse([string]$region.y, $invariant)
                $lng = [double]::Parse([string]$region.x, $invariant)
                if ([double]::IsNaN($lat) -or [double]::IsNaN($lng) -or $lat -le 0 -or $lat -gt 90 -or $lng -le 0 -or $lng -gt 180) { continue }
                return [pscustomobject]@{
                    code = $row.code; sido = $row.sido; sigungu = $row.sigungu; name = $row.name
                    centerLat = $lat; centerLng = $lng; source = 'Kakao H center'
                    checkedAt = [DateTime]::UtcNow.ToString('o')
                }
            }
        }
    }
    return $null
}
$failures = New-Object 'System.Collections.Generic.List[object]'
$attempted = 0
$watch = [Diagnostics.Stopwatch]::StartNew()
Write-Host ("Total: {0}, cached: {1}. Results: build/region-seed" -f $rows.Count, $resolved.Count)
foreach ($row in $rows) {
    if ($resolved.ContainsKey([string]$row.code)) { continue }
    if ($Limit -gt 0 -and $attempted -ge $Limit) { break }
    $attempted++
    $center = Find-Center $row
    if ($null -ne $center) {
        [IO.File]::AppendAllText($cacheFile, ($center | ConvertTo-Json -Compress) + [Environment]::NewLine, $utf8)
        $resolved[[string]$row.code] = $center
    } else {
        $failures.Add([pscustomobject]@{code=$row.code;sido=$row.sido;sigungu=$row.sigungu;name=$row.name;reason='No matching Kakao H code'})
        $failures | Export-Csv -LiteralPath $failedFile -NoTypeInformation -Encoding UTF8
    }
    if ($attempted % 10 -eq 0) {
        Write-Host ("Checked {0}; matched {1}/{2}; unmatched this run {3}; elapsed {4:mm\:ss}" -f $attempted, $resolved.Count, $rows.Count, $failures.Count, $watch.Elapsed)
    }
}
$remaining = @($rows | Where-Object { -not $resolved.ContainsKey([string]$_.code) })
$remaining | Export-Csv -LiteralPath (Join-Path $outDir 'remaining.csv') -NoTypeInformation -Encoding UTF8
Write-Host ("DONE: matched {0}/{1}; remaining {2}. No database changes were made." -f $resolved.Count, $rows.Count, $remaining.Count)
Write-Host 'Send resolved.jsonl and remaining.csv for seed generation. Do not send infra/.env.'
