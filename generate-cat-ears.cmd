@echo off
setlocal
cd /d "%~dp0"

set "PYTHON="
py -3 -c "import sys" >nul 2>nul && set "PYTHON=py -3"
if not defined PYTHON python -c "import sys" >nul 2>nul && set "PYTHON=python"
if not defined PYTHON python3 -c "import sys" >nul 2>nul && set "PYTHON=python3"

if not defined PYTHON (
    echo [cat-ears] Python 3 was not found in PATH.
    if "%~1"=="" pause
    exit /b 1
)

%PYTHON% "%~dp0scripts\cat_ears.py" %*
set "STATUS=%ERRORLEVEL%"

if not "%STATUS%"=="0" (
    echo [cat-ears] Failed with exit code %STATUS%.
) else if "%~1"=="" (
    echo [cat-ears] Preview: %~dp0docs\preview
    start "" "%~dp0docs\preview"
)

if "%~1"=="" pause
exit /b %STATUS%
