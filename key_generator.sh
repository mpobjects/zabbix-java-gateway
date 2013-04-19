#!/bin/bash

cd $(dirname $0)

JAVA=${JAVA:-java}

JAVA_OPTIONS="-client"

CLASSPATH="lib:bin"
for jar in {lib,bin}/*.jar; do
	if [[ $jar != *junit* ]]; then
		CLASSPATH="$CLASSPATH:$jar"
	fi
done

COMMAND_LINE="$JAVA $JAVA_OPTIONS -classpath $CLASSPATH com.zabbix.security.KeyGenerator"
exec $COMMAND_LINE
