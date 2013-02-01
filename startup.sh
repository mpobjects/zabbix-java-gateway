#!/bin/bash

cd $(dirname $0)
source settings.sh

if [ -n "$PID_FILE" -a -e "$PID_FILE" ]; then
	echo "Zabbix Java Gateway is already running"
	exit 1
fi

JAVA=${JAVA:-java}

JAVA_OPTIONS="-server"
if [ -z "$PID_FILE" ]; then
	JAVA_OPTIONS="$JAVA_OPTIONS -Dlogback.configurationFile=logback-console.xml"
fi

CLASSPATH="lib"
for jar in {lib,bin}/*.jar; do
	if [[ $jar != *junit* ]]; then
		CLASSPATH="$CLASSPATH:$jar"
	fi
done

ZABBIX_OPTIONS=""
if [ -n "$API_USER" ]; then
	ZABBIX_OPTIONS="$ZABBIX_OPTIONS -Dzabbix.apiUser=$API_USER"
fi
if [ -n "$API_PASSWORD" ]; then
	ZABBIX_OPTIONS="$ZABBIX_OPTIONS -Dzabbix.apiPassword=$API_PASSWORD"
fi
if [ -n "$API_HOST" ]; then
ZABBIX_OPTIONS="$ZABBIX_OPTIONS -Dzabbix.apiHost=$API_HOST"
fi
if [ -n "$API_PORT" ]; then
ZABBIX_OPTIONS="$ZABBIX_OPTIONS -Dzabbix.apiPort=$API_PORT"
fi
if [ -n "$PID_FILE" ]; then
	ZABBIX_OPTIONS="$ZABBIX_OPTIONS -Dzabbix.pidFile=$PID_FILE"
fi
if [ -n "$LISTEN_IP" ]; then
	ZABBIX_OPTIONS="$ZABBIX_OPTIONS -Dzabbix.listenIP=$LISTEN_IP"
fi
if [ -n "$LISTEN_PORT" ]; then
	ZABBIX_OPTIONS="$ZABBIX_OPTIONS -Dzabbix.listenPort=$LISTEN_PORT"
fi
if [ -n "$START_POLLERS" ]; then
	ZABBIX_OPTIONS="$ZABBIX_OPTIONS -Dzabbix.startPollers=$START_POLLERS"
fi

COMMAND_LINE="$JAVA $JAVA_OPTIONS -classpath $CLASSPATH $ZABBIX_OPTIONS com.zabbix.gateway.JavaGateway"

if [ -n "$PID_FILE" ]; then
	PID=$(/bin/bash -c "$COMMAND_LINE > /dev/null 2>&1 & echo \$!")
	if ps -p $PID > /dev/null 2>&1; then
		echo $PID > $PID_FILE
	else
		echo "Zabbix Java Gateway did not start"
		exit 1
	fi
else
	exec $COMMAND_LINE
fi
