@echo off
echo Compiling field extractor demo...

javac -cp ".;extractor;%USERPROFILE%\.m2\repository\org\jsoup\jsoup\1.17.2\jsoup-1.17.2.jar" FieldExtractorDemo.java extractor\*.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed!
    echo Make sure jsoup JAR is available from your Maven repository.
    echo Check: %USERPROFILE%\.m2\repository\org\jsoup\jsoup\1.17.2\jsoup-1.17.2.jar
    pause
    exit /b 1
)

echo.
echo Compilation complete. Running demo...
echo =========================================
java -cp ".;extractor;%USERPROFILE%\.m2\repository\org\jsoup\jsoup\1.17.2\jsoup-1.17.2.jar" FieldExtractorDemo
pause
