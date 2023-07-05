#!/bin/bash
count=0
while true; do
	read -rs -n1 key
	count=$((count+1))
	echo $count
done
