[CmdletBinding()]
Param(
    [Parameter(Position = 0, Mandatory = $false, ValueFromRemainingArguments = $true)]
    [string[]] $CliArguments
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"
$ConfirmPreference = "None"
trap {
    Write-Error $_ -ErrorAction Continue
    exit 1
}

function Invoke-External([string] $Executable, [string[]] $Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

$CliRoot = $PSScriptRoot
Set-Location -LiteralPath $CliRoot
$GameHome = [IO.Path]::GetFullPath((Join-Path $CliRoot "..\.."))
$Java = Join-Path $GameHome "jre\bin\java.exe"
$GameLib = Join-Path $GameHome "starsector-core"
$ModsDirectory = Join-Path $GameHome "mods"

if (!(Test-Path -LiteralPath $Java -PathType Leaf)) {
    throw "game java runtime not found at $Java"
}

if (!(Test-Path -LiteralPath $GameLib -PathType Container)) {
    throw "game library directory not found at $GameLib"
}

$EcjJar = Get-ChildItem -LiteralPath (Join-Path $CliRoot "jars") -Filter "ecj*.jar" -File |
    Sort-Object Name |
    Select-Object -Last 1
if ($null -eq $EcjJar) {
    throw "no ECJ compiler jar found in jars\"
}

$GameJars = Get-ChildItem -LiteralPath $GameLib -Filter "*.jar" -File | Sort-Object FullName
$CompilerClasspathEntries = @($EcjJar.FullName) + @($GameJars | ForEach-Object { $_.FullName })
$CompilerClasspath = $CompilerClasspathEntries -join [IO.Path]::PathSeparator

$SourceDirectories = @(
    (Join-Path $CliRoot "microforge-cli\src\main\java"),
    (Join-Path $CliRoot "microforge-core\src\main\java")
)
$SourceFiles = @(
    Get-ChildItem -LiteralPath $SourceDirectories -Filter "*.java" -File -Recurse |
        Sort-Object FullName
)
if ($SourceFiles.Count -eq 0) {
    throw "no Java sources found under microforge-cli\src\main\java or microforge-core\src\main\java"
}

$BuildDirectory = Join-Path $CliRoot "out\microforge-cli"
$ClassesDirectory = Join-Path $BuildDirectory "classes"
$MainClass = Join-Path $ClassesDirectory "razen\microforge\cli\Main.class"
$Stamp = Join-Path $BuildDirectory ".built"
$NeedsBuild = !(Test-Path -LiteralPath $Stamp -PathType Leaf) -or
    !(Test-Path -LiteralPath $MainClass -PathType Leaf)

if (!$NeedsBuild) {
    $StampTime = (Get-Item -LiteralPath $Stamp).LastWriteTimeUtc
    $BuildInputs = @($SourceFiles) + @((Get-Item -LiteralPath $PSCommandPath))
    $NeedsBuild = $null -ne ($BuildInputs |
        Where-Object { $_.LastWriteTimeUtc -gt $StampTime } |
        Select-Object -First 1)
}

if ($NeedsBuild) {
    Write-Output "building cli..."
    if (Test-Path -LiteralPath $BuildDirectory) {
        Remove-Item -LiteralPath $BuildDirectory -Recurse -Force
    }
    New-Item -ItemType Directory -Path $ClassesDirectory -Force | Out-Null

    $CompilerArguments = @(
        "-jar", $EcjJar.FullName,
        "-17",
        "-encoding", "UTF-8",
        "-proc:none",
        "-cp", $CompilerClasspath,
        "-time",
        "-d", $ClassesDirectory
    ) + @($SourceFiles | ForEach-Object { $_.FullName })
    Invoke-External -Executable $Java -Arguments $CompilerArguments
    New-Item -ItemType File -Path $Stamp -Force | Out-Null
}

$RuntimeClasspath = @(
    $ClassesDirectory,
    $EcjJar.FullName,
    (Join-Path $GameLib "*")
) -join [IO.Path]::PathSeparator

$RunArguments = @(
    "-XX:+UnlockDiagnosticVMOptions",
    "-XX:-BytecodeVerificationLocal",
    "-XX:-BytecodeVerificationRemote",
    "-Drazen.microforge.cli.path.game=$GameLib",
    "-Drazen.microforge.cli.path.ecj=$($EcjJar.FullName)",
    "-Dcom.fs.starfarer.settings.paths.mods=$ModsDirectory",
    "-cp", $RuntimeClasspath,
    "razen.microforge.cli.Main"
) + @($CliArguments)

& $Java @RunArguments
exit $LASTEXITCODE
