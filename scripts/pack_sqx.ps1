# Empaqueta una carpeta "fuente" de SQX en un .sqx (zip con rutas "/").
# Uso: powershell -File pack_sqx.ps1 -Src "...\fuente" -Out "...\Nombre.sqx"
param([Parameter(Mandatory)] [string]$Src, [Parameter(Mandatory)] [string]$Out)
$ErrorActionPreference = 'Stop'

$mf = Join-Path $Src 'META-INF\MANIFEST.MF'
if (-not (Test-Path $mf)) { New-Item -ItemType Directory -Force (Split-Path $mf) | Out-Null; [IO.File]::WriteAllText($mf, "Manifest-Version: 1.0`r`n`r`n") }
$ver = Join-Path $Src 'version.txt'
if (-not (Test-Path $ver)) { [IO.File]::WriteAllText($ver, "1") }

$xmlPath = Join-Path $Src 'strategy_Portfolio.xml'
$bytes = [IO.File]::ReadAllBytes($xmlPath)
if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { throw "strategy_Portfolio.xml tiene BOM: guardalo en UTF-8 sin BOM" }
$x = [xml](Get-Content $xmlPath -Raw -Encoding UTF8)
$ids = @($x.StrategyFile.Strategy.Variables.variable | ForEach-Object { $_.id })
$used = [regex]::Matches([IO.File]::ReadAllText($xmlPath), '>([0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12})<') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
$missing = @($used | Where-Object { $ids -notcontains $_ })
if ($missing.Count) { throw "GUID usados sin variable: $($missing -join ', ')" }
"XML OK ({0} variables: {1})" -f $ids.Count, (($x.StrategyFile.Strategy.Variables.variable | ForEach-Object { "$($_.name)=$($_.value)" }) -join ', ')

if (Test-Path $Out) { Remove-Item $Out -Confirm:$false }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open($Out, 'Create')
try {
  foreach ($f in @('META-INF/MANIFEST.MF', 'version.txt', 'strategy_Portfolio.xml')) {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $Src $f), $f) | Out-Null
  }
} finally { $zip.Dispose() }
"Creado $Out"
