#!/bin/bash

cd ..
for i in {1..3}
do
	screen -dm sbt "runMain bifrost.BifrostApp settings$i.json" 2>&1 | tee ./logs/bifrost_$i.log
done