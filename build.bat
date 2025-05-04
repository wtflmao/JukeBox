@echo off
echo Cleaning previous build...
call mvn clean
if errorlevel 1 (
    echo Maven clean failed.
    goto end
)

echo Compiling and packaging the project...
call mvn package
if errorlevel 1 (
    echo Maven package failed.
    goto end
)

echo Build successful! The JAR file should be in the 'target' directory.

:end
echo Press any key to exit...
pause > nul 