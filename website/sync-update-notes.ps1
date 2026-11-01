Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$websiteDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$updatePath = Join-Path $websiteDir "update.json"

if (-not (Test-Path -LiteralPath $updatePath)) {
    throw "update.json not found: $updatePath"
}

$update = Get-Content -LiteralPath $updatePath -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $update.versionName) {
    throw "versionName is missing in update.json"
}

$notesPath = $null
if ($update.PSObject.Properties.Name -contains "notesUrl" -and $update.notesUrl) {
    if ($update.notesUrl -match "^[a-zA-Z][a-zA-Z0-9+.-]*:") {
        throw "notesUrl must be a local website-relative path for sync: $($update.notesUrl)"
    }
    $notesPath = Join-Path $websiteDir $update.notesUrl
} else {
    $notesPath = Join-Path $websiteDir ("changelog\{0}.md" -f $update.versionName)
}

if (-not (Test-Path -LiteralPath $notesPath)) {
    throw "changelog not found: $notesPath"
}

$markdown = Get-Content -LiteralPath $notesPath -Raw -Encoding UTF8
$body = $markdown -replace "(?s)^---\s*(?:\r?\n).*?(?:\r?\n)---\s*", ""
$items = @(foreach ($line in ($body -split "\r?\n")) {
    $item = $line.Trim()
    if ($item -match "^([-*+]|\d+\.)\s+(.*)$") {
        $Matches[2].Trim()
    }
})

if (-not $items -or $items.Count -eq 0) {
    throw "no changelog list items found: $notesPath"
}

$notes = ($items | ForEach-Object { "- $_" }) -join "`n"

if ($update.PSObject.Properties.Name -contains "notes") {
    $update.notes = $notes
} else {
    $update | Add-Member -NotePropertyName "notes" -NotePropertyValue $notes
}

function ConvertTo-JsonScalar($value) {
    return ($value | ConvertTo-Json -Compress -Depth 10)
}

$jsonLines = @("{")
$properties = @($update.PSObject.Properties)
for ($i = 0; $i -lt $properties.Count; $i++) {
    $property = $properties[$i]
    $comma = if ($i -lt $properties.Count - 1) { "," } else { "" }
    $jsonLines += "  {0}: {1}{2}" -f (ConvertTo-JsonScalar $property.Name), (ConvertTo-JsonScalar $property.Value), $comma
}
$jsonLines += "}"
$json = $jsonLines -join [Environment]::NewLine
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($updatePath, $json + [Environment]::NewLine, $utf8NoBom)

Write-Host "Synced update notes from $notesPath"
