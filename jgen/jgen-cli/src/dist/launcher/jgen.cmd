@echo off
rem JGen launcher for Windows. See bin/jgen for the blueprint folders searched.
setlocal
set "JGEN_HOME=%~dp0.."
if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA=java"
)
set "LOADER_PATH=%JGEN_HOME%\blueprints,%USERPROFILE%\.jgen\blueprints"
if defined JGEN_BLUEPRINTS set "LOADER_PATH=%LOADER_PATH%,%JGEN_BLUEPRINTS%"
"%JAVA%" %JAVA_OPTS% "-Dloader.path=%LOADER_PATH%" -jar "%JGEN_HOME%\lib\jgen-cli.jar" %*
exit /b %ERRORLEVEL%
