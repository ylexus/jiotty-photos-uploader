if [%1]==[] goto usage

wmic product where name="Jiotty Photos Uploader" call uninstall
if %errorlevel% neq 0 exit /b %errorlevel%

echo Running as %USERNAME%
"%LOCALAPPDATA%\Microsoft\WindowsApps\MsixPackagingTool.exe" create-package --template build\packaging-resources\windows\out\msix-template.xml
if %errorlevel% neq 0 exit /b %errorlevel%

"C:\Program Files\WindowsApps\Microsoft.MsixPackagingTool_1.2021.709.0_x64__8wekyb3d8bbwe\SDK\signtool.exe" sign /fd SHA256 /sha1 4c06aba4e81303505dc05a06e58f3e3aa42b71bd /tr http://timestamp.comodoca.com /td sha256 build\fullpackage\Windows10-jiotty-photos-uploader-%1.msix
if %errorlevel% neq 0 exit /b %errorlevel%

goto :eof
:usage
@echo Usage: %0 ^<Version^>
exit /B 1