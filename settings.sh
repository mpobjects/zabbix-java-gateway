# This is a configuration file for Zabbix Java Gateway.
# It is sourced by startup.sh and shutdown.sh scripts.

### Option: zabbix.listenIP
#	IP address to listen on.
#
# Mandatory: no
# Default:
# LISTEN_IP="0.0.0.0"

### Option: zabbix.listenPort
#	Port to listen on.
#
# Mandatory: no
# Range: 1024-32767
# Default:
# LISTEN_PORT=10052

### Option: zabbix.pidFile
#	Name of PID file.
#	If omitted, Zabbix Java Gateway is started as a console application.
#
# Mandatory: no
# Default:
# PID_FILE=

PID_FILE="/tmp/zabbix_java.pid"

### Option: zabbix.startPollers
#	Number of worker threads to start.
#   0 specifies a demand based thread pool will be used.
#
# Mandatory: no
# Range: 0-1000
# Default:
# START_POLLERS=0

### Option: zabbix.apiUser
#	The API username to use
#
# Mandatory: no
# Default:
# API_USER=admin

### Option: zabbix.apiPassword
#	The API password to use
#
# Mandatory: no
# Default:
# API_PASSWORD=zabbix

### Option: zabbix.apiHost
#   The host name or IP address
#   of the Zabbix front end which
#   the API connection will use.
#
# Mandatory: no
# Default:
# API_HOST=localhost

### Option: zabbix.apiPort
#   The Zabbix frontend port for API usage.
#
# Mandatory: no
# Default:
# API_PORT=80

