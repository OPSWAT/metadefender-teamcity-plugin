#!/usr/bin/env bash

VERSION=$(grep -oP '<version>\K.*(?=</version>)' teamcity-plugin.xml)

docker build -t metadefender-teamcity-plugin .

container=$(docker run -id metadefender-teamcity-plugin)
docker cp $container:/mps/target/metadefender-plugin.zip metadefender-tc-plugin-$VERSION.zip
docker rm -v $container -f

id=$(docker images -q metadefender-teamcity-plugin)
docker rmi $id -f
