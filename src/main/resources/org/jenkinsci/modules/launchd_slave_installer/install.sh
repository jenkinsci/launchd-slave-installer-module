#!/bin/sh -ex
(
    JAR=/System/Library/LaunchDaemons/org.jenkins-ci.slave.$3.jar
    DST=/System/Library/LaunchDaemons/org.jenkins-ci.slave.$3.plist
    cp "$1" $DST
    cp "$2" $JAR
    chmod 644 $DST
    # su failed for no apparent reasons
    # su - root -c "launchctl load $DST"
    # this seemingly pointless sudo is necessary to let launchctl connect to the system-level launchd instance
    sudo -n -u root launchctl load $DST
    echo success
) 2>&1 | tee /tmp/jenkins-slave-launchd-install.log