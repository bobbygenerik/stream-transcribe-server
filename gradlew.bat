@echo off
REM Minimal Windows gradlew stub. Prefer generating wrapper locally via 'gradle wrapper'
if exist "%~dp0\gradle\wrapper\gradle-wrapper.jar" (
  java -jar "%~dp0\gradle\wrapper\gradle-wrapper.jar" %*
) else (
  echo Gradle wrapper jar not found. Please run 'gradle wrapper' locally to generate it.
  exit /b 1
)
