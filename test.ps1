$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdk = '../../jdk-23+7/bin'
    $cp = '../../starsector-core/*;jars/PeerlessSkills.jar;../Console Commands/jars/lw_Console.jar;../LazyLib/jars/internal/Kotlin-Runtime.jar'
    $out = Join-Path 'build' ('tests-' + [guid]::NewGuid())
    New-Item -ItemType Directory -Force $out | Out-Null
    $sources = @(Get-ChildItem tools -Filter '*.java' | ForEach-Object FullName)
    & "$jdk/javac.exe" --release 17 -cp $cp -d $out $sources
    if ($LASTEXITCODE) { throw 'Test compilation failed' }
    foreach ($test in @('PeerlessRewardsRegression','PeerlessHelmsmanshipRegression','PeerlessConsoleRegression','PeerlessSkillIconRegression')) {
        & "$jdk/java.exe" '-Xverify:none' '-Dlog4j.defaultInitOverride=true' '-Djava.library.path=../../starsector-core/native/windows' -cp ($out+';'+$cp) "peerless.skills.$test"
        if ($LASTEXITCODE) { throw "$test failed" }
    }
} finally { Pop-Location }
