@echo off

REM ----------------------------------------------------------------------------
REM  program : greys
REM  author : hplegend
REM  date : 2019-12-31
REM  notice：注意，要再powershell或者cmd窗口执行，attach vm后才会telent 链接上
REM ----------------------------------------------------------------------------


set ERROR_CODE=0
set HTTP_PORT=8563

REM set BASEDIR=%~dp0
REM 这里要注意，window下要用绝对路径，否则就会加载不到agent。估计是代码写的有问题，classloader没写好
set BASEDIR=D:\hplegend\greysAgent

if ["%~1"]==[""] (
  echo Example:
  echo   %~nx0 452
  echo   %~nx0 452 --ignore-tools # for jdk 9/10/11
  echo(
  echo Need the pid argument, you can run jps to list all java process ids.
  goto exit_bat
)

set AGENT_JAR=%BASEDIR%\greys-agent.jar
set CORE_JAR=%BASEDIR%\greys-core.jar

set PID=%1
set TARGET_TELNET_IP=%2
set TARGET_TELNET_PORT=%3

echo %PID%| findstr /r "^[1-9][0-9]*$">nul

if %errorlevel% neq 0 (
  echo PID is not valid number!
  echo Example:
  echo   %~nx0 452
  echo   %~nx0 452 --ignore-tools # for jdk 9/10/11
  echo(
  echo Need the pid argument, you can run jps to list all java process ids.
  goto exit_bat
)

REM parse extend args
set ignoreTools=0
set exitProcess=0
for %%a in (%*) do (
  if "%%a"=="--no-interact" set exitProcess=1
  if "%%a"=="--ignore-tools" set ignoreTools=1
)

REM from https://stackoverflow.com/a/35445653 
:read_params
if not %1/==/ (
    if not "%__var%"=="" (
        if not "%__var:~0,1%"=="-" (
            endlocal
            goto read_params
        )
        endlocal & set %__var:~1%=%~1
    ) else (
        setlocal & set __var=%~1
    )
    shift
    goto read_params
)

if not "%http-port%"=="" set HTTP_PORT=%http-port%

echo JAVA_HOME: %JAVA_HOME%
echo telnet ip and port: %TARGET_TELNET_IP% %TARGET_TELNET_PORT%
echo http port: %HTTP_PORT%

REM Setup JAVA_HOME
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
if %ignoreTools% == 1 (
  echo Ignore tools.jar, make sure the java version ^>^= 9
) else (
  if not exist "%JAVA_HOME%\lib\tools.jar" (
    echo Can not find lib\tools.jar under %JAVA_HOME%!
    echo If java version ^<^= 1.8, please make sure JAVA_HOME point to a JDK not a JRE.
    echo If java version ^>^= 9, try to run as.bat ^<pid^> --ignore-tools
    goto exit_bat
  )
  set BOOT_CLASSPATH="-Xbootclasspath/a:%JAVA_HOME%\lib\tools.jar"
)

set JAVACMD="%JAVA_HOME%\bin\java"
goto okJava

:noJavaHome
echo The JAVA_HOME environment variable is not defined correctly.
echo It is needed to run this program.
echo NB: JAVA_HOME should point to a JDK not a JRE.
goto exit_bat

:okJava
set JAVACMD="%JAVA_HOME%"\bin\java

REM 执行java命令。JAVACMD 就是 java命令，后面就是一堆参数，这些参数就是main使用的，并通过sock绑定到vm然后进行通信。
%JAVACMD% -Dfile.encoding=UTF-8 %BOOT_CLASSPATH% -jar "%CORE_JAR%" -pid "%PID%"  -core "%CORE_JAR%" -agent "%AGENT_JAR%" -target "%TARGET_TELNET_IP%:%TARGET_TELNET_PORT%"
if %ERRORLEVEL% NEQ 0 goto exit_bat
if %exitProcess%==1 goto exit_bat
goto attachSuccess


:attachSuccess
WHERE telnet
IF %ERRORLEVEL% NEQ 0 (
  ECHO telnet wasn't found, please google how to install telnet under windows.
  ECHO Try to visit http://127.0.0.1:%HTTP_PORT% to connecto arthas server.
  start http://127.0.0.1:%HTTP_PORT%
) else (
  telnet %TARGET_TELNET_IP% %TARGET_TELNET_PORT%
)

:exit_bat
if %exitProcess%==1 exit %ERROR_CODE%
exit /B %ERROR_CODE%
