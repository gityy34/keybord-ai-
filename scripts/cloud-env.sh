#!/usr/bin/env bash
# Source this file; environment activation is needed in each new cloud shell.
export RAABTA_TOOLCHAINS="${RAABTA_TOOLCHAINS:-/workspace/toolchains}"
export JAVA_HOME="$RAABTA_TOOLCHAINS/jdk-17.0.20+8"
export ANDROID_HOME="$RAABTA_TOOLCHAINS/android-sdk"
export ANDROID_USER_HOME="$RAABTA_TOOLCHAINS/android-user"
export GRADLE_USER_HOME="$RAABTA_TOOLCHAINS/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Duser.home=$RAABTA_TOOLCHAINS/user-home"
if [[ -r /etc/ssl/certs/java/cacerts ]]; then
    # Use the machine's trusted CA store, including its managed egress CA.
    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
