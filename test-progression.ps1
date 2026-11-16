param([string]$GameRoot = (Split-Path (Split-Path $PSScriptRoot)))
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdk = Join-Path $GameRoot 'jdk-23+7/bin'
    $mods = Join-Path $GameRoot 'mods'
    $runtimeJar = (Get-Content -Raw mod_info.json | ConvertFrom-Json).jars[0]
    $cp = (Join-Path $GameRoot 'starsector-core/*') + ';' + $runtimeJar + ';' +
        (Join-Path $mods 'Console Commands/jars/lw_Console.jar') + ';' +
        (Join-Path $mods 'LazyLib/jars/internal/Kotlin-Runtime.jar') + ';' +
        (Join-Path $mods 'LazyLib/jars/LazyLib.jar') + ';' +
        (Join-Path $mods 'LunaLib/jars/LunaLib.jar')
    $out = Join-Path 'build' ('progression-tests-' + [guid]::NewGuid())
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    # The tracker must compile without starfarer_obf.jar or any internal engine classes.
    $publicCp = (Join-Path $GameRoot 'starsector-core/starfarer.api.jar') + ';' + $runtimeJar
    & "$jdk/javac.exe" --release 17 -cp $publicCp -d $out src/peerless/campaign/PeerlessProgression.java
    if ($LASTEXITCODE) { throw 'Public-API tracker compilation failed' }
    & "$jdk/javac.exe" --release 17 -cp ($out + ';' + $cp) -d $out tools/PeerlessFixture.java tools/PeerlessProgressionRegression.java
    if ($LASTEXITCODE) { throw 'Progression regression compilation failed' }
    & "$jdk/java.exe" '-Dlog4j.defaultInitOverride=true' -cp ($out + ';' + $cp) peerless.skills.PeerlessProgressionRegression
    if ($LASTEXITCODE) { throw 'Public-API progression regression failed' }
    Write-Host "Progression checks passed: $out"
} finally { Pop-Location }
