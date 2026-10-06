<#
  Builds PMIS-Docket-Setup-<version>.exe

  Needs on the build PC: JDK 21 (with jpackage), Maven 3.9+, Inno Setup 6.
  Usage (from the project folder):
    powershell -ExecutionPolicy Bypass -File installer\build-installer.ps1 -ServerUrl https://192.168.1.10:8443
  Optional: -ServerCert certs\docket.cer  -> the app will trust your server's own HTTPS certificate.
#>
param(
    [string]$ServerUrl = "https://SERVER-IP:8443",
    [string]$Version = "1.0.0",
    [string]$ServerCert = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    Write-Host "1/4  Building the desktop app..." -ForegroundColor Cyan
    mvn -q -pl desktop -am clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

    Write-Host "2/4  Collecting files..." -ForegroundColor Cyan
    if (Test-Path build) { Remove-Item -Recurse -Force build }
    $inputDir = "build\input"
    New-Item -ItemType Directory -Force $inputDir | Out-Null
    Copy-Item desktop\target\docket-desktop.jar $inputDir
    Copy-Item desktop\target\lib\*.jar $inputDir

    Write-Host "3/4  Packaging app with its own Java runtime (jpackage)..." -ForegroundColor Cyan
    $jp = @(
        "--type", "app-image",
        "--name", "PMIS Docket",
        "--app-version", $Version,
        "--vendor", "PMIS",
        "--description", "PMIS Docket file manager",
        "--input", $inputDir,
        "--main-jar", "docket-desktop.jar",
        "--main-class", "com.pmis.docket.desktop.Launcher",
        "--dest", "build\app",
        "--java-options", "-Xmx1g",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--add-modules", "java.base,java.desktop,java.net.http,java.logging,java.naming,java.xml,java.sql,java.scripting,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata"
    )
    if (Test-Path "installer\assets\docket.ico") { $jp += @("--icon", "installer\assets\docket.ico") }
    & jpackage @jp
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

    if ($ServerCert -ne "") {
        $cacerts = "build\app\PMIS Docket\runtime\lib\security\cacerts"
        & keytool -importcert -noprompt -alias pmis-docket-server -file $ServerCert -keystore $cacerts -storepass changeit
        if ($LASTEXITCODE -ne 0) { throw "Could not add the server certificate." }
        Write-Host "     Server certificate added to the app's trusted list." -ForegroundColor Green
    }

    Write-Host "4/4  Creating the installer (Inno Setup)..." -ForegroundColor Cyan
    $iscc = Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\ISCC.exe"
    if (-not (Test-Path $iscc)) { throw "Inno Setup 6 not found. Install it from https://jrsoftware.org/isdl.php" }
    & $iscc "/DServerUrl=$ServerUrl" "/DAppVersion=$Version" "installer\PMIS-Docket.iss"
    if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed." }

    Write-Host ""
    Write-Host "Done: build\installer\PMIS-Docket-Setup-$Version.exe" -ForegroundColor Green
}
finally {
    Pop-Location
}
