@echo off
mvn clean package
if errorlevel 1 exit /b %errorlevel%
echo.
echo Built: target\VupeCore-1.0.0.jar
