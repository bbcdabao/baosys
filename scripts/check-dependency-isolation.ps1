# Run from any directory after changing Maven dependencies.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
    & mvn -B dependency:tree '-Dscope=runtime' '-DoutputFile=target/dependency-isolation.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Maven dependency resolution failed.' }

    $reactive = @('spring-webflux', 'reactor-core', 'reactive-streams', 'reactor-netty-http', 'context-propagation', 'baosys-common-gateway')
    $servlet = @('spring-webmvc', 'jakarta.servlet-api', 'tomcat-embed-core', 'spring-boot-starter-web', 'baosys-common-resource')
    $checks = @(
        @{ Module = 'baosys-common-api'; Banned = @(); Required = @(); Empty = $true },
        @{ Module = 'baosys-common'; Banned = $reactive + $servlet + @('spring-web', 'spring-security-core', 'baosys-common-security-core'); Required = @('baosys-common-api') },
        @{ Module = 'baosys-common-gateway'; Banned = $servlet; Required = @('baosys-common', 'baosys-common-security-core', 'spring-webflux', 'context-propagation') },
        @{ Module = 'baosys-common-resource'; Banned = $reactive; Required = @('baosys-common', 'baosys-common-security-core', 'spring-webmvc') },
        @{ Module = 'baosys-manager-web'; Banned = $servlet; Required = @('baosys-common-gateway', 'spring-webflux') },
        @{ Module = 'baosys-manager/baosys-manager-service'; Banned = $reactive; Required = @('baosys-common-resource', 'spring-webmvc') }
    )
    foreach ($check in $checks) {
        $lines = Get-Content (Join-Path $check.Module 'target/dependency-isolation.txt')
        # Skip the project coordinate on the first line.
        $dependencies = $lines | Select-Object -Skip 1
        $tree = $dependencies -join "`n"
        if ($check.Empty -and $dependencies) { throw "$($check.Module) must have no runtime dependencies." }
        foreach ($artifact in ($check.Banned + @('baosys-common-security-gateway', 'baosys-common-security-resource'))) {
            if ($tree -match (':' + [regex]::Escape($artifact) + ':')) {
                throw "$($check.Module) unexpectedly depends on $artifact."
            }
        }
        foreach ($artifact in $check.Required) {
            if ($tree -notmatch (':' + [regex]::Escape($artifact) + ':')) {
                throw "$($check.Module) is missing $artifact."
            }
        }
        Write-Output "PASS $($check.Module)"
    }
} finally {
    Pop-Location
}
