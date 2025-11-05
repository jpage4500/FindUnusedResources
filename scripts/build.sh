#!/bin/bash

cd ..

mvn -B package --file pom.xml

# copy to shared location for use with projects
DRIVE=/Users/jpage/GoogleDrive/bin
if [ -d $DRIVE ]; then
    cp target/FindUnusedResources.jar $DRIVE

    ls -al $DRIVE/FindUnusedResources.jar
fi