#! /bin/bash

export `grep adminRef gradle.properties | tr -d [:space:]`

echo 'Removing admin directory'
rm -rf admin

echo 'Cloning https://github.com/Drill4J/admin repository with branch' $adminRef
git clone https://github.com/Drill4J/admin admin --branch $adminRef
