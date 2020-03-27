@REM akka-sample-cluster-scala launcher script
@REM
@REM Environment:
@REM JAVA_HOME - location of a JDK home dir (optional if java on path)
@REM CFG_OPTS  - JVM options (optional)
@REM Configuration:
@REM AKKA_SAMPLE_CLUSTER_SCALA_config.txt found in the AKKA_SAMPLE_CLUSTER_SCALA_HOME.
@setlocal enabledelayedexpansion
@setlocal enableextensions

@echo off


if "%AKKA_SAMPLE_CLUSTER_SCALA_HOME%"=="" (
  set "APP_HOME=%~dp0\\.."

  rem Also set the old env name for backwards compatibility
  set "AKKA_SAMPLE_CLUSTER_SCALA_HOME=%~dp0\\.."
) else (
  set "APP_HOME=%AKKA_SAMPLE_CLUSTER_SCALA_HOME%"
)

set "APP_LIB_DIR=%APP_HOME%\lib\"

rem Detect if we were double clicked, although theoretically A user could
rem manually run cmd /c
for %%x in (!cmdcmdline!) do if %%~x==/c set DOUBLECLICKED=1

rem FIRST we load the config file of extra options.
set "CFG_FILE=%APP_HOME%\AKKA_SAMPLE_CLUSTER_SCALA_config.txt"
set CFG_OPTS=
call :parse_config "%CFG_FILE%" CFG_OPTS

rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
set _JAVA_OPTS=%JAVA_OPTS%
if "!_JAVA_OPTS!"=="" set _JAVA_OPTS=!CFG_OPTS!

rem We keep in _JAVA_PARAMS all -J-prefixed and -D-prefixed arguments
rem "-J" is stripped, "-D" is left as is, and everything is appended to JAVA_OPTS
set _JAVA_PARAMS=
set _APP_ARGS=

set "APP_CLASSPATH=%APP_LIB_DIR%\net.atos.git.ncf.akka-sample-cluster-scala-0.1.jar;%APP_LIB_DIR%\org.scala-lang.scala-library-2.12.6.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-actor_2.12-2.5.25.jar;%APP_LIB_DIR%\org.scala-lang.modules.scala-java8-compat_2.12-0.8.0.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-remote_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-stream_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-protobuf_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.ssl-config-core_2.12-0.3.8.jar;%APP_LIB_DIR%\org.scala-lang.modules.scala-parser-combinators_2.12-1.1.2.jar;%APP_LIB_DIR%\io.netty.netty-3.10.6.Final.jar;%APP_LIB_DIR%\io.aeron.aeron-driver-1.15.1.jar;%APP_LIB_DIR%\org.agrona.agrona-0.9.31.jar;%APP_LIB_DIR%\io.aeron.aeron-client-1.15.1.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-cluster_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-cluster-metrics_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-cluster-tools_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-coordination_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-multi-node-testkit_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-testkit_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-slf4j_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-persistence_2.12-2.5.25.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-discovery_2.12-2.5.25.jar;%APP_LIB_DIR%\com.github.scullxbones.akka-persistence-mongo-scala_2.12-2.3.1.jar;%APP_LIB_DIR%\com.github.scullxbones.akka-persistence-mongo-common_2.12-2.3.1.jar;%APP_LIB_DIR%\nl.grons.metrics4-akka_a25_2.12-4.0.8.jar;%APP_LIB_DIR%\nl.grons.metrics4-scala_2.12-4.0.8.jar;%APP_LIB_DIR%\io.dropwizard.metrics.metrics-core-4.1.0.jar;%APP_LIB_DIR%\org.slf4j.slf4j-api-1.7.26.jar;%APP_LIB_DIR%\io.dropwizard.metrics.metrics-healthchecks-4.1.0.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-persistence-query_2.12-2.5.25.jar;%APP_LIB_DIR%\org.mongodb.mongodb-driver-core-3.11.0.jar;%APP_LIB_DIR%\org.mongodb.bson-3.11.0.jar;%APP_LIB_DIR%\org.mongodb.scala.mongo-scala-driver_2.12-2.7.0.jar;%APP_LIB_DIR%\org.mongodb.scala.mongo-scala-bson_2.12-2.7.0.jar;%APP_LIB_DIR%\org.mongodb.mongodb-driver-async-3.11.0.jar;%APP_LIB_DIR%\org.scala-lang.scala-reflect-2.12.6.jar;%APP_LIB_DIR%\io.netty.netty-buffer-4.1.41.Final.jar;%APP_LIB_DIR%\io.netty.netty-common-4.1.41.Final.jar;%APP_LIB_DIR%\io.netty.netty-transport-4.1.41.Final.jar;%APP_LIB_DIR%\io.netty.netty-resolver-4.1.41.Final.jar;%APP_LIB_DIR%\io.netty.netty-handler-4.1.41.Final.jar;%APP_LIB_DIR%\io.netty.netty-codec-4.1.41.Final.jar;%APP_LIB_DIR%\org.reactivestreams.reactive-streams-1.0.3.jar;%APP_LIB_DIR%\com.lightbend.akka.management.akka-management_2.12-1.0.5.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-http_2.12-10.1.10.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-http-core_2.12-10.1.10.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-parsing_2.12-10.1.10.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-http-spray-json_2.12-10.1.10.jar;%APP_LIB_DIR%\io.spray.spray-json_2.12-1.3.5.jar;%APP_LIB_DIR%\com.lightbend.akka.management.akka-management-cluster-http_2.12-1.0.5.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-cluster-sharding_2.12-2.5.23.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-distributed-data_2.12-2.5.23.jar;%APP_LIB_DIR%\org.lmdbjava.lmdbjava-0.6.1.jar;%APP_LIB_DIR%\com.github.jnr.jnr-ffi-2.1.7.jar;%APP_LIB_DIR%\com.github.jnr.jffi-1.2.16.jar;%APP_LIB_DIR%\com.github.jnr.jffi-1.2.16-native.jar;%APP_LIB_DIR%\org.ow2.asm.asm-5.0.3.jar;%APP_LIB_DIR%\org.ow2.asm.asm-commons-5.0.3.jar;%APP_LIB_DIR%\org.ow2.asm.asm-tree-5.0.3.jar;%APP_LIB_DIR%\org.ow2.asm.asm-analysis-5.0.3.jar;%APP_LIB_DIR%\org.ow2.asm.asm-util-5.0.3.jar;%APP_LIB_DIR%\com.github.jnr.jnr-x86asm-1.0.2.jar;%APP_LIB_DIR%\com.github.jnr.jnr-constants-0.9.9.jar;%APP_LIB_DIR%\com.lightbend.akka.discovery.akka-discovery-kubernetes-api_2.12-1.0.5.jar;%APP_LIB_DIR%\com.lightbend.akka.management.akka-management-cluster-bootstrap_2.12-1.0.5.jar;%APP_LIB_DIR%\io.circe.circe-core_2.12-0.12.1.jar;%APP_LIB_DIR%\io.circe.circe-numbers_2.12-0.12.1.jar;%APP_LIB_DIR%\org.typelevel.cats-core_2.12-2.0.0.jar;%APP_LIB_DIR%\org.typelevel.cats-macros_2.12-2.0.0.jar;%APP_LIB_DIR%\org.typelevel.cats-kernel_2.12-2.0.0.jar;%APP_LIB_DIR%\io.circe.circe-generic_2.12-0.12.1.jar;%APP_LIB_DIR%\com.chuusai.shapeless_2.12-2.3.3.jar;%APP_LIB_DIR%\org.typelevel.macro-compat_2.12-1.1.1.jar;%APP_LIB_DIR%\io.circe.circe-parser_2.12-0.12.1.jar;%APP_LIB_DIR%\io.circe.circe-jawn_2.12-0.12.1.jar;%APP_LIB_DIR%\org.typelevel.jawn-parser_2.12-0.14.2.jar;%APP_LIB_DIR%\com.github.julien-truffaut.monocle-core_2.12-2.0.0.jar;%APP_LIB_DIR%\org.typelevel.cats-free_2.12-2.0.0.jar;%APP_LIB_DIR%\com.github.julien-truffaut.monocle-macro_2.12-2.0.0.jar;%APP_LIB_DIR%\com.github.julien-truffaut.monocle-unsafe_2.12-2.0.0.jar;%APP_LIB_DIR%\org.typelevel.alleycats-core_2.12-2.0.0.jar;%APP_LIB_DIR%\org.apache.logging.log4j.log4j-api-2.11.2.jar;%APP_LIB_DIR%\org.apache.logging.log4j.log4j-core-2.11.2.jar;%APP_LIB_DIR%\org.apache.logging.log4j.log4j-slf4j-impl-2.11.2.jar;%APP_LIB_DIR%\org.apache.logging.log4j.log4j-api-scala_2.12-11.0.jar;%APP_LIB_DIR%\com.github.pureconfig.pureconfig_2.12-0.12.1.jar;%APP_LIB_DIR%\com.github.pureconfig.pureconfig-core_2.12-0.12.1.jar;%APP_LIB_DIR%\com.github.pureconfig.pureconfig-macros_2.12-0.12.1.jar;%APP_LIB_DIR%\com.typesafe.config-1.3.4.jar;%APP_LIB_DIR%\com.github.pureconfig.pureconfig-generic_2.12-0.12.1.jar;%APP_LIB_DIR%\com.github.pureconfig.pureconfig-akka_2.12-0.12.1.jar;%APP_LIB_DIR%\com.github.pureconfig.pureconfig-cats_2.12-0.12.1.jar;%APP_LIB_DIR%\com.github.pureconfig.pureconfig-circe_2.12-0.12.1.jar;%APP_LIB_DIR%\io.kamon.sigar-loader-1.6.6-rev002.jar;%APP_LIB_DIR%\com.slamdata.matryoshka-core_2.12-0.21.3.jar;%APP_LIB_DIR%\org.typelevel.scala-library-2.12.1.jar;%APP_LIB_DIR%\com.slamdata.slamdata-predef_2.12-0.0.6.jar;%APP_LIB_DIR%\org.scalaz.scalaz-core_2.12-7.2.15.jar;%APP_LIB_DIR%\org.scalactic.scalactic_2.12-3.0.1.jar;%APP_LIB_DIR%\com.github.mpilquist.simulacrum_2.12-0.11.0.jar"
set "APP_MAIN_CLASS=sample.cluster.k8s.Runner"
set "SCRIPT_CONF_FILE=%APP_HOME%\conf\application.ini"

rem Bundled JRE has priority over standard environment variables
if defined BUNDLED_JVM (
  set "_JAVACMD=%BUNDLED_JVM%\bin\java.exe"
) else (
  if "%JAVACMD%" neq "" (
    set "_JAVACMD=%JAVACMD%"
  ) else (
    if "%JAVA_HOME%" neq "" (
      if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
    )
  )
)

if "%_JAVACMD%"=="" set _JAVACMD=java

rem Detect if this java is ok to use.
for /F %%j in ('"%_JAVACMD%" -version  2^>^&1') do (
  if %%~j==java set JAVAINSTALLED=1
  if %%~j==openjdk set JAVAINSTALLED=1
)

rem BAT has no logical or, so we do it OLD SCHOOL! Oppan Redmond Style
set JAVAOK=true
if not defined JAVAINSTALLED set JAVAOK=false

if "%JAVAOK%"=="false" (
  echo.
  echo A Java JDK is not installed or can't be found.
  if not "%JAVA_HOME%"=="" (
    echo JAVA_HOME = "%JAVA_HOME%"
  )
  echo.
  echo Please go to
  echo   http://www.oracle.com/technetwork/java/javase/downloads/index.html
  echo and download a valid Java JDK and install before running akka-sample-cluster-scala.
  echo.
  echo If you think this message is in error, please check
  echo your environment variables to see if "java.exe" and "javac.exe" are
  echo available via JAVA_HOME or PATH.
  echo.
  if defined DOUBLECLICKED pause
  exit /B 1
)

rem if configuration files exist, prepend their contents to the script arguments so it can be processed by this runner
call :parse_config "%SCRIPT_CONF_FILE%" SCRIPT_CONF_ARGS

call :process_args %SCRIPT_CONF_ARGS% %%*

set _JAVA_OPTS=!_JAVA_OPTS! !_JAVA_PARAMS!

if defined CUSTOM_MAIN_CLASS (
    set MAIN_CLASS=!CUSTOM_MAIN_CLASS!
) else (
    set MAIN_CLASS=!APP_MAIN_CLASS!
)

rem Call the application and pass all arguments unchanged.
"%_JAVACMD%" !_JAVA_OPTS! !AKKA_SAMPLE_CLUSTER_SCALA_OPTS! -cp "%APP_CLASSPATH%" %MAIN_CLASS% !_APP_ARGS!

@endlocal

exit /B %ERRORLEVEL%


rem Loads a configuration file full of default command line options for this script.
rem First argument is the path to the config file.
rem Second argument is the name of the environment variable to write to.
:parse_config
  set _PARSE_FILE=%~1
  set _PARSE_OUT=
  if exist "%_PARSE_FILE%" (
    FOR /F "tokens=* eol=# usebackq delims=" %%i IN ("%_PARSE_FILE%") DO (
      set _PARSE_OUT=!_PARSE_OUT! %%i
    )
  )
  set %2=!_PARSE_OUT!
exit /B 0


:add_java
  set _JAVA_PARAMS=!_JAVA_PARAMS! %*
exit /B 0


:add_app
  set _APP_ARGS=!_APP_ARGS! %*
exit /B 0


rem Processes incoming arguments and places them in appropriate global variables
:process_args
  :param_loop
  call set _PARAM1=%%1
  set "_TEST_PARAM=%~1"

  if ["!_PARAM1!"]==[""] goto param_afterloop


  rem ignore arguments that do not start with '-'
  if "%_TEST_PARAM:~0,1%"=="-" goto param_java_check
  set _APP_ARGS=!_APP_ARGS! !_PARAM1!
  shift
  goto param_loop

  :param_java_check
  if "!_TEST_PARAM:~0,2!"=="-J" (
    rem strip -J prefix
    set _JAVA_PARAMS=!_JAVA_PARAMS! !_TEST_PARAM:~2!
    shift
    goto param_loop
  )

  if "!_TEST_PARAM:~0,2!"=="-D" (
    rem test if this was double-quoted property "-Dprop=42"
    for /F "delims== tokens=1,*" %%G in ("!_TEST_PARAM!") DO (
      if not ["%%H"] == [""] (
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
      ) else if [%2] neq [] (
        rem it was a normal property: -Dprop=42 or -Drop="42"
        call set _PARAM1=%%1=%%2
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
        shift
      )
    )
  ) else (
    if "!_TEST_PARAM!"=="-main" (
      call set CUSTOM_MAIN_CLASS=%%2
      shift
    ) else (
      set _APP_ARGS=!_APP_ARGS! !_PARAM1!
    )
  )
  shift
  goto param_loop
  :param_afterloop

exit /B 0
