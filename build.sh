#!/usr/bin/env bash

docker build -t metadefender-teamcity-plugin .

container=$(docker run -id metadefender-teamcity-plugin)
docker cp $container:/mps/target/metadefender-plugin.zip - > metadefender-plugin.zip
docker rm -v $container -f

id=$(docker images -q metadefender-teamcity-plugin)
docker rmi $id -f