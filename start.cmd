@echo off
setlocal
cd /d "%~dp0"
if not exist "application.yml" (
  echo [ERROR] application.yml is missing.
  echo Copy application.example.yml to application.yml and configure your databases first.
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java 17 or newer is required and must be on PATH.
  exit /b 1
)
java -jar "sweetcherrysync-1.1.0-alpha.1.jar" --spring.config.additional-location=file:./application.yml
exit /b %errorlevel%
