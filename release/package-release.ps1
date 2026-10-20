# Package the existing Spring Boot JAR; this script DOES NOT build or run tests.
# Usage (PowerShell, from repository root): .\release\package-release.ps1
$ErrorActionPreference = 'Stop'
$version = '1.1.0-alpha.1'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$jar = Join-Path $root "target/sweetcherrysync-$version.jar"
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "Missing $jar. Build first: .\mvnw.cmd -DskipTests package"
}
$stage = Join-Path $root "dist/SweetCherrySync-v$version"
$zip = Join-Path $root "dist/SweetCherrySync-v$version.zip"
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null
$items = @(
    @{ Source=$jar; Name="sweetcherrysync-$version.jar" },
    @{ Source=(Join-Path $root 'README.md'); Name='README.md' },
    @{ Source=(Join-Path $root 'RELEASE_NOTES_1.1.0-alpha.1.md'); Name='RELEASE_NOTES_1.1.0-alpha.1.md' },
    @{ Source=(Join-Path $root 'LICENSE'); Name='LICENSE' },
    @{ Source=(Join-Path $root 'application.example.yml'); Name='application.example.yml' },
    @{ Source=(Join-Path $root 'start.cmd'); Name='start.cmd' },
    @{ Source=(Join-Path $root 'start.sh'); Name='start.sh' }
)
foreach ($entry in $items) {
    if (-not (Test-Path -LiteralPath $entry.Source -PathType Leaf)) { throw "Missing $($entry.Source)" }
    Copy-Item -LiteralPath $entry.Source -Destination (Join-Path $stage $entry.Name)
}
Copy-Item -LiteralPath (Join-Path $root 'documentation') -Destination (Join-Path $stage 'documentation') -Recurse
foreach ($name in @('schema-sqlite.sql','schema-mysql.sql','schema-postgresql.sql','schema-mssql.sql')) {
    Copy-Item -LiteralPath (Join-Path $root $name) -Destination (Join-Path $stage $name)
}
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -CompressionLevel Optimal
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
$shaFile = "$zip.sha256"
Set-Content -LiteralPath $shaFile -Value "$hash  $(Split-Path -Leaf $zip)" -Encoding Ascii
Write-Host "Created: $zip"
Write-Host "SHA-256: $shaFile"
Write-Host "Packaged only the existing JAR; no build/tests executed."
