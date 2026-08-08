:; set -eo pipefail
:; SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
:; "$SCRIPT_DIR/cli.sh" "$@"
:; exit $?

@ECHO OFF
powershell -ExecutionPolicy ByPass -NoProfile -File "%~dp0cli.ps1" %*
EXIT /B %ERRORLEVEL%
