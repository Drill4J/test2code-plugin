@echo off
for /f "eol=# delims== tokens=2" %%r in ('findstr adminRef gradle.properties') do set adminRef=%%r
set adminRef=%adminRef: =%

echo Removing admin directory
rmdir /s /q admin

echo Cloning https://github.com/Drill4J/admin repository with branch %adminRef%
git clone https://github.com/Drill4J/admin admin --branch %adminRef%
