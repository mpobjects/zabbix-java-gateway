# Zabbix Java Gateway Improvements

This project mainly started because my company (JDA Software) liked Jolokia a lot and we wanted to integrate it with Zabbix. The goal then of this project was to add some improvements to the Zabbix Java Gateway which is used to retrieve JMX data for the [Zabbix](http://www.zabbix.com) monitoring tool. Some of these features are fairly non-standard so I don't expect them to make their way into Zabbix standard product but at the same time there's some improvements which I'm hoping can be pushed back to the standard Zabbix Java Gateway product. The key features so far are:

* Configurable protocol and endpoint
* Jolokia support which includes JMX Operations
* Better discovery for JMX dynamic mbeans
* Published Metrics via JMX about the Java Gateway's performance
* Improved security

# Configurable protocol and endpoint

One of the limitations of the Zabbix Java Gateway is that both the protocol and JMX endpoint are hardcoded. So if you have your MBean server exposed on an endpoint other than the default "/jmxrmi" then it will not work correctly. The goal here was to make this configurable without having to make schema changes to Zabbix. To do this the JMX protocol and endpoint can now be configured via the Host macros {$JMX_PROTOCOL} and {$JMX_ENDPOINT}.

## JMX Endpoint Setup

First, the Zabbix Java Gateway's settings.sh file needs to be configured so that the API_USER and API_PASSWORD properties are defined with a valid Zabbix user as we'll need to interact with the Zabbix Server via the Zabbix API.

Second, if I have a Host setup which is monitoring a Java server which has exposed the MBean server on the endpoint "/admin" I would define a host level macro in Zabbix called {$JMX_ENDPOINT} and give it the value "/admin". Then when the Zabbix Java Gateway goes to fulfill a request on this host it will determine that the endpoint for the host has been defined as "/admin" and will use that accordingly. The {$JMX_PROTOCOL} if not defined will default to the standard JMX protocol, it isn't used currently unless you want to enable Jolokia integration (see the next section).

## Limitations

* Assumes your Zabbix Java Gateway is running on the same machine as the Zabbix server/frontend (I'm not sure if anyone configures this differently as the Java Gateway is fairly lightweight)
* Due to the use of Host level macros to define the JMX protocol and endpoint, this means there can only be one Java server monitored per host.

Hopefully in the future a true fix will come for this which would include modify the Zabbix Server code and frontend as well as schema changes to make the protocol and endpoint configurable on the interface itself. The purpose here was to make lightest touch possible. See this Jira for that feature - https://support.zabbix.com/browse/ZBXNEXT-1274

# Jolokia integration

The main improvement here though is allowing for the Zabbix Java Gateway to use communicate with [Jolokia](http://www.jolokia.org) rather than via standard JMX methods. In addition to making all JMX data available via HTTP webservices, Jolokia provides some nice advantages over JMX such as bulk requests. Instead of making a roundtrip over the network for each JMX MBean we want to request data from we can batch all requests into one single HTTP POST using Jolokia to fulfill the request. This is especially nice if you're communicating over a network with high latency. Zabbix Java pollers inherently already batch together JMX requests on the Zabbix Server and send them to the Zabbix Java Gateway process to fulfill the request. So for instance if you have 32 JMX attributes you want to read from a host every 30 seconds, Zabbix will batch these together every 30 seconds and send them to the Zabbix Java Gateway where Jolokia can fulfill all 32 attribute requests in a single HTTP POST request.

## JMX Operations using Jolokia

Another feature added is the support for JMX Operations if you're using Jolokia to fulfill your Zabbix Java Gateway requests. Zabbix JMX operations can be defined using the following Zabbix JMX item key:

```
jmx.operation[<object_name>,<method_name_with_argument_values>,<argument_types>]
```

The argument types only need to be specified if the operations method is overloaded (see later example). So for instance if you wanted to check for deadlocked threads you could define the following key:

```
jmx.operation["java.lang:type=Threading","findDeadlockedThreads()"]
```

Now if you want to call an operation that is an overloaded method you need to specify the arguments types as well. So for example if you wanted to get the CPU time of a particular thread (let's say the thread ID is 111) you could use the key:

```
jmx.operation["java.lang:type=Threading","getThreadCpuTime(111)","long"]
```

The third parameter "long" specifies that the argument 111 is a "long" so that the correct method is used instead of the other method signature for getThreadCpuTime which takes in an array of longs. Note that if your argument is not the primitive long but rather the wrapper long you would specify "java.lang.Long"

While these examples may be contrived my company has found that we use JMX Operations via Zabbix to execute Zabbix Discovery operations for dynamically registered mbeans. So we have operations that return Zabbix discovery information.


## Miscellaneous improvements

* Added support to read in Doubles that are represented in scientific notation as Zabbix cannot parse these properly if they're defined as a number in Zabbix.
* Added support for array items, if a JMX attribute is an array then all the values are concatenated with newlines in between. The item should then be defined as a string/text in Zabbix.

## Jolokia Setup

To enable Jolokia in Zabbix take the following steps:

* You need a Java webserver that already has Jolokia deployed (see Jolokia website)
* Create a host in Zabbix with a JMX interface (note the current limitation allows this to only work with one JMX interface per host). The port used should be the port that your Java web server is running on that has Jolokia deployed.
* On the host create the following macros:
    * {$JMX_PROTOCOL} set the value to "http" or "https"
    * {$JMX_ENDPOINT} set the value to the endpoint that Jolokia is deployed on i.e. "/jolokia"
* Authentication is supported - for your JMX items defined in Zabbix make sure you have setup the JMX user/password properly.

With that done the Zabbix Java Gateway should pick up that you want to communicate using Jolokia and should start fulfilling JMX requests using Jolokia/HTTP instead.

## Limitations

* One JMX interface per host as noted due to the limitations of the configurable endpoint
* Currently doesn't support attributes that are nested more than twice i.e. you can define a key that goes after the attribute "myattribute.subattribute1" but "myattribute.subattribute1.nextsubattribute" is not supported (this should be fixed shortly)

# Metrics

Support was added to publish metrics via JMX using the [Metrics](http://metrics.codahale.com/) library. Metrics that are published are related to the performance of the Zabbix Java Gateway, there should be metrics now exposed around how long requests are taking/the rate of requests and how many attributes are being requested. I will also plan to add a Zabbix template that can be applied to your Zabbix Server host to start collecting metrics via JMX for your Zabbix Java Gateway.

# New Discovery Options

Before utilizing this you should understand how Zabbix low level discovery works, this is documented [here](https://www.zabbix.com/documentation/2.0/manual/discovery/low_level_discovery)

A new JMX discovery option has been added to both the standard Zabbix JMX checker and Jolokia checker. This discovery option allows you to search for specific MBeans by using a wildcard in the ObjectName. The format is as follows:


```
jmx.discovery[<ObjectNameWildcard>]
```

This helps solve issues such as dynamically named MBeans for both the Memory Pool and Garbage Collectors as their names may vary depending on the JVM or arguments passed to the Java process. Here's an example using the Memory Pool MBeans, for my particular JVM I have the following MBeans for the Memory Pool:

````
java.lang:type=MemoryPool,name=CMS Old Gen
java.lang:type=MemoryPool,name=CMS Perm Gen
````

These are prefixed with "CMS" due to the use of Concurrent Mark Sweep on my particular JVM - this will vary depending on the garbage collector used, for instance if the Parallel Scavenge Collector is being used the MBean name would be "PS Old Gen". Now, to resolve this with the new discovery feature you create a Zabbix Discovery item with the following item definition (JMX item):

````
jmx.discovery["java.lang:type=MemoryPool,name=*"]
````

You'll notice the name isn't specified but rather a wildcard here. This means return all MBeans whose object names match the beginning section (Memory Pool) but retrieve any "name" value. The Zabbix Java Gateway will then respond with this using the Zabbix discovery format:


```
#!JSON

{
    "data": [
        {
            "{#NAME}": "CMS Old Gen",
            "{#JMXOBJ}": "java.lang:name=CMS Old Gen,type=MemoryPool",
            "{#TYPE}": "MemoryPool"
        },
        {
            "{#NAME}": "CMS Perm Gen",
            "{#JMXOBJ}": "java.lang:name=CMS Perm Gen,type=MemoryPool",
            "{#TYPE}": "MemoryPool"
        }
    ]
}
```

The macro {#JMXOBJ} is returned for each matching MBean found, this is the full name of the MBean that was found and can be used then in Zabbix prototype items via Zabbix Discovery. Additionally, for each property in the MBean's ObjectName a macro is returned. So, in this example there were two properties in the ObjectName - Type and Name, the Type being MemoryPool and the Name being whichever Memory Pool it was. These can also be used in your Zabbix prototype definitions. For instance I could make a prototype item with the item name:

````
{#NAME} Memory Used
````
The Zabbix discovery process in the previous example would then result in two items:
````
CMS Old Gen Memory Used
CMS Perm Gen Memory Used
````

# Improved Security

One issue with Zabbix is that for JMX items added via the frontend, a username and password need to be specified for the item to be able to connect to the remote client and these values are put in plain text. To remedy this an encryption model has been added to the Zabbix Java Gateway to allow passwords to be entered using ciphered text. These ciphered passwords are decrypted using a private key that should be stored on the file system and read access locked down to the specific user which the Zabbix Java Gateway service runs under. This security is optional (non-encrypted passwords will still work the same). To setup JMX client passwords with encryption do the following:

Generate the private key
````
cd <zabbix_java>
./key_generator.sh
// The first time this runs it will prompt you to generate a private key using a password
// You should see the following output
Private key doesn't exist, enter the desired key password:
````

After confirming the password this will generate the private key in your working directory with the name ".zbx_privatekey". This file needs to be moved and properly secured. **NOTE:** This step is very important as we rely on the OS level security to keep our private key private! Typically passwords are just one way hashed (non-reversible) but since we need to know the plain text password to interact with external systems and because it isn't practical to prompt the user each time the Zabbix Java Gateway service starts, we need two way encryption! Do the following to secure the private key:

````
// Move the file to <zabbix_java>/bin so it's on the Java CLASSPATH - assumes you're already in the <zabbix_java> directory
mv .zbx_privatekey bin

// Lock down the file to your service user, we'll assume the service runs as root for this example
chown root bin/.zbx_privatekey
chmod 400 bin/.zbx_privatekey
````

At this point, you should be able to start generating encrypted passwords for your JMX client passwords. Let's take the example where your remote JMX client uses the password "mypassword". To generate the encrypted key for this password do the following:

````
// Use the key_generator.sh script again to do this, if the previous steps were followed correctly it should just prompt you for the JMX client password you wish to encrypt.
// If it prompts you to generate the the private key again revisit the previous steps because most likely the private key wasn't moved to <zabbix_java>/bin

./key_generator.sh
Enter the remote client's password to encrypt:
// Enter the password, then you'll be prompted to confirm it
// This will output the encrypted password to use like so:
Use the following encrypted password: |ZBX|IxjOX3RROTgxYYrR/Z0THQ==***EjvpU2J/b9Dmf03AZ6gH2w==
````

Take this generated encrypted password (in our example above it's |ZBX|IxjOX3RROTgxYYrR/Z0THQ==***EjvpU2J/b9Dmf03AZ6gH2w==) and paste it into your JMX item's password field. When the Zabbix Java Gateway receives the encrypted password it will decrypt it using the private key. Hint: Instead of having to copy/paste this encrypted password into all your JMX items it is recommended to use a Zabbix user macro such as {$JMX_PASS} and use that macro in all your items. Then at the host level you would set {$JMX_PASS} to whatever the generated encrypted password is.

You can additionally encrypt your API_PASSWORD in the settings.sh file, the procedure is the same just make sure to put double quotes around the value like so:

````
API_PASSWORD="|ZBX|IxjOX3RROTgxYYrR/Z0THQ==***EjvpU2J/b9Dmf03AZ6gH2w=="
````

If you turn on debugging (<zabbix_java>/lib/logback.xml - change info to debug) you'll now see on your requests the encrypted password is passed along instead of in plain text:

````
2013-04-19 15:44:33.002 [pool-1-thread-2] DEBUG c.z.gateway.BinaryProtocolSpeaker - received the following data in request: {
     "request":"java gateway jmx",
     "conn":"10.47.4.236",
     "port":5300,
     "username":"admin",
     "password":"|ZBX|IxjOX3RROTgxYYrR/Z0THQ==***EjvpU2J/b9Dmf03AZ6gH2w==",
````
Note: The encryption method used makes it so that the same plain text password will not generate the same cipher twice e.g. if I call key_generator.sh twice and use the same password "mypassword" - two different encrypted keys will be generated. This is on purpose to increase the randomness of the encrypted key - both will work however.

# Downloads

You can download the latest version that can then be unzipped and used with Zabbix [here](https://bitbucket.org/ryanrupp/zabbix-java-gateway/downloads/zabbix_java.zip)

The source is available on the "features" branch.

# Deploying the Zabbix Java Gateway

* Access the download in the Downloads section (mentioned above) and unzip it (can be placed anywhere really)
* Configure the settings.sh accordingly, make sure to specify the API_USER and API_PASSWORD
* Can be started the same as the standard Zabbix Java Gateway by running the startup.sh script.

# Future improvements

I'm looking to add the following improvements in the future:

* JMX operations without having to use Jolokia
