#!/bin/sh -ex
JAR=/System/Library/LaunchDaemons/org.jenkins-ci.slave.$3.jar
DST=/System/Library/LaunchDaemons/org.jenkins-ci.slave.$3.plist
cp "$1" $DST
cp "$2" $JAR
chmod 644 $DST
launchctl load $DST