if [%1]==[] goto usage

wmic product where name="Jiotty Photos Uploader" call uninstall
if %errorlevel% neq 0 exit /b %errorlevel%

"%LOCALAPPDATA%\Microsoft\WindowsApps\MsixPackagingTool.exe" create-package --template build\packaging-resources\windows\out\msix-template.xml
if %errorlevel% neq 0 exit /b %errorlevel%

"C:\Program Files\WindowsApps\Microsoft.MsixPackagingTool_1.2020.709.0_x64__8wekyb3d8bbwe\SDK\signtool.exe" sign /fd SHA256 /sha1 6bbfc8540686d8fc3ae5e394e7b590d903504d39 /tr http://timestamp.comodoca.com /td sha256 build\jpackage\Windows10-jiotty-photos-uploader-%1.msix

goto :eof
:usage
@echo Usage: %0 ^<Version^>
exit /B 1