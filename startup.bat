@echo off
setlocal DisableDelayedExpansion
set "BAOSYS_STARTUP_FILE=%~f0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$source = [IO.File]::ReadAllText($env:BAOSYS_STARTUP_FILE); & ([scriptblock]::Create(($source -split '(?m)^# POWERSHELL_START\r?$', 2)[1]))"
exit /b %errorlevel%
# POWERSHELL_START
$ErrorActionPreference = 'Stop'
$processes = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$exitCode = 0
try {
    $rootDir = Split-Path -LiteralPath $env:BAOSYS_STARTUP_FILE
    $outDir = Join-Path $rootDir 'out'
    if (-not (Test-Path -LiteralPath $outDir -PathType Container)) {
        throw 'out directory is missing. Run mvn install first.'
    }
    Set-Location -LiteralPath $outDir
    $jars = @(Get-ChildItem -LiteralPath $outDir -Filter '*.jar' -File | Sort-Object Name)
    if ($jars.Count -eq 0) { throw 'No JAR files in out. Run mvn install first.' }
    $java = (Get-Command java.exe -CommandType Application -ErrorAction Stop).Source
    $keyPath = Join-Path $outDir 'signing-jwks.json'
    if (-not (Test-Path -LiteralPath $keyPath -PathType Leaf)) {
        $jshell = (Get-Command jshell.exe -CommandType Application -ErrorAction Stop).Source
        & $jshell '-R-Dsigning.key.output=signing-jwks.json' (Join-Path $rootDir 'scripts\generate-signing-key.jsh')
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $keyPath -PathType Leaf)) {
            throw 'Signing key generation failed.'
        }
        if ((Get-Item -LiteralPath $keyPath).Length -eq 0) { throw 'Signing key file is empty.' }
    }
    $env:JWT_JWK_SET_LOCATION = ([System.Uri]$keyPath).AbsoluteUri
    $env:DEFAULT_ADMIN_ENABLED = 'true'
    $env:DEFAULT_ADMIN_USERNAME = 'admin'
    $env:DEFAULT_ADMIN_PASSWORD = 'LocalAdmin123!'

    foreach ($jar in $jars) {
        Write-Host "Starting $($jar.Name)"
        $process = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar.FullName + '"')) -WorkingDirectory $outDir -NoNewWindow -PassThru
        $processes.Add($process)
    }
    Write-Host 'Applications started. Press Ctrl+C to stop all applications.'
    :monitor while ($true) {
        foreach ($process in $processes) {
            if ($process.HasExited) {
                $exitCode = $process.ExitCode
                break monitor
            }
        }
        Start-Sleep -Milliseconds 200
    }
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    $exitCode = 1
} finally {
    # PowerShell executes finally on Ctrl+C as well as on startup failures.
    # Only stop processes created by this invocation; never kill java.exe globally.
    foreach ($process in $processes) {
        if (-not $process.HasExited) {
            Stop-Process -InputObject $process -Force -ErrorAction SilentlyContinue
            $process.WaitForExit()
        }
        $process.Dispose()
    }
}
exit $exitCode
