# Set Up Google Play for Work EMM Notifications

Google Play for Work Enterprise Mobility Management (EMM) notifications allow
EMMs to receive push or pull notifications that inform administrators of
important events. This document describes how to set up push notifications only.
Additional information and examples on how to set up push and pull notifications
are available in the official [Cloud Pub/Sub
documentation](https://cloud.google.com/pubsub/overview).

You can configure your system so that push notifications are sent to either a
specified HTTPS endpoint, or to a server that waits for notifications to be sent.

## Information about push endpoint configuration

To configure a push endpoint you need a server with a valid SSL certificate. In
this example, you will create and upload an SSL certificate to a certificate authority
(CA), then configure the Nginx server. Finally you will compile and run test code to
confirm your set up is correct.

The sample scenario in this document shows how to configure an
[Nginx server](https://www.nginx.com/resources/admin-guide/reverse-proxy/) in
reverse­ proxy mode, to connect to the subscriber app (in `PushSubscriber.java`)
running on port 8093, using Ubuntu 14.04. Your enterprise might use a different
server, but the sample set up should work, without changes, on all Debian­ based
distributions. Other distributions (such as those based on RedHat) are similar
but the location of the configuration files may be different.

Note: To perform the tasks in this document you must have access to `sudo` on
the server.

Before you can receive notifications, you must first configure an endpoint, that
meets the following criteria:

* You must own the domain, and you will have to verify your ownership in the
[Google Developers Console](https://console.developers.google.com/).

* You must be able to run a service on port 443 (SSL)

* You must have a CA ­signed SSL certificate. Self­ signed certificates do not work.

* The web server you are running must support [Webhooks](http://en.wikipedia.org/wiki/Webhook "Webhooks").

Your endpoint does not need to [run on Google App Engine](https://cloud.google.com/pubsub/prereqs#register) (although it can).

## Create and upload an SSL certificate

1\. Produce a Secure Sockets Layer (SSL) certificate.

    myusername@myhost:/tmp$ sudo openssl req -x509 -nodes -days 365
      -newkey rsa:2048 -keyout cert.key -out cert.crt

This generates the following response. Replace the sample values
(such as `push.solarmora.com` and `myusername@myhost`) with your actual server
name, company, address and so on, in the following code. You can use any
subdomain as long as the `A` record of this subdomain points to your server.

    Generating a 2048 bit RSA private key
    ...........................................................................
    .....+++
    writing new private key to 'cert.key'
    -----
    You are about to be asked to enter information that will be incorporated
    into your certificate request.
    What you are about to enter is what is called a Distinguished Name or a DN.
    There are quite a few fields but you can leave some blank
    For some fields there will be a default value,
    If you enter '.', the field will be left blank.
    -----
    Country Name (2 letter code) [AU]:GB
    State or Province Name (full name) [Some-State]:England
    Locality Name (eg, city) []:London
    Organization Name (eg, company) [Internet Widgits Pty Ltd]:Solarmora, Inc.
    Organizational Unit Name (eg, section) []:Creative Publications
    Common Name (e.g. server FQDN or YOUR name) []:push.solarmora.com
    Email Address []:admin@solarmora.com


2\. Verify that a certificate file was created:

    $ myusername@myhost:/tmp$ ls cert*
    cert.crt  cert.key

3\. To get this certificate signed, produce a
[Certificate Signing Request](http://en.wikipedia.org/wiki/Certificate_signing_request) (CSR) to upload to your signer.

```
myusername@myhost:/tmp$ sudo openssl  x509 -x509toreq -in cert.crt \
-out cert.csr -signkey cert.key
Getting request Private Key
Generating certificate request
myusername@myhost:/tmp$ ls cert.*
cert.crt  cert.csr  cert.key
```

4\. Ensure the content of the CSR file looks like this:

    Certificate Request:
    Data:
        Version: 0 (0x0)
        Subject: C=GB, ST=England, L=London, O=Solarmora, Inc.,
        OU=Creative Publications,
        CN=push.solarmora.com/emailAddress=admin@solarmora.com
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                Public-Key: (2048 bit)
                Modulus:
                    00:cc:0f:54:26:3d:d9:17:eb:8f:6c:f7:27:5e:77:
                    64:65:00:db:fe:2a:1f:fa:ea:de:21:7a:c5:5d:87:
                    ...
                    ...
                Exponent: 65537 (0x10001)
        Attributes:
            a0:00
    Signature Algorithm: sha256WithRSAEncryption
         1d:ea:12:b8:c2:6a:d6:f4:6e:92:2f:b9:12:5e:e3:91:15:a0:
         06:b5:81:ce:c5:cf:b7:d2:a7:dd:f2:78:ca:28:8e:21:cd:6d:
         ...
         ...
    -----BEGIN CERTIFICATE REQUEST-----
    MIIC6zCCAdMCAQAwgaUxCzAJBgNVBAYTAkdCMRAwDgYDVQQIDAdFbmdsYW5kMQ8w
    DQYDVQQHDAZMb25kb24xGDAWBgNVBAoMD0FDTUUgQ29ycCwgSW5jLjEYMBYGA1UE
    CwwPQ3JlYXRpdmUgQW52aWxzMRswGQYDVQQDDBJwdXNoLmFjbWUtY29ycC5jb20x
    IjAgBgkqhkiG9w0BCQEWE2FkbWluQGFjbWUtY29ycC5jb20wggEiMA0GCSqGSIb3
    ...
    ...
    -----END CERTIFICATE REQUEST-----

5\. Upload the part of your certificate between the `BEGIN`
and `END` lines inclusive to your CA. The exact process will
depend on your CA, but will include the following steps:

1. Upload your CSR file to your CA site, or paste the content of your file onto your CA site. Your CA then validates and processes this data.
2. Download the signed certificate generated by your CA.

6\. The output from the CA should contains multiple files: the signed
certificate itself and the CA's certificate confirming they are eligible to
sign certificates. Concatenate all `*.crt` certificate files in the downloaded
bundle into a single bundle file, for example bundle.push.solarmora.com.crt:

    $ cat *.crt > bundle.push.solarmora.com.crt

### Configure Your Proxy Server

In this section you configure the Nginx open ­source web server and reverse
proxy server to serve the endpoint and forward all incoming requests to the
subscriber server. Note that Nginx is used here as an example, but any other
http proxy can be used instead.

1\. Install Nginx on your server:

    $ sudo apt-get update
    $ sudo apt-get install nginx

    $ nginx -v
    $ nginx version: nginx/1.4.6 (Ubuntu)


2\. To ensure that the extra server conifguration files you create  in the
`sites-enabled` directory are processed by Nginx, edit `/etc/nginx/nginx.conf`
and include the following:

    $ include /etc/nginx/conf.d/*.conf;
    $ include /etc/nginx/sites-enabled/*;


3\. Copy your certificate files to a safe location, readable by the
`www-data` user, but preferably not readable by any other user (you may need
to adjust the user name if your web server is running as a different
user):

    $ sudo mkdir -p /var/openssl/push.solarmora.com
    $ sudo mv /tmp/cert.key \
        /var/openssl/push.solarmora.com/push.solarmora.com.key
    $ sudo mv /tmp/bundle.push.solarmora.com.crt \
        /var/openssl/push.solarmora.com/bundle.push.solarmora.com.crt

4\. Create a new `server` configuration. Edit `push.solarmora.com`
in `/etc/nginx/sites-enabled`. You should use your actual
server's fully-qualified domain name as the file name, as follows:

    server {
       listen 443;
       server_name push.solarmora.com;

       ssl on;
       ssl_certificate
         /var/openssl/push.solarmora.com/bundle.push.solarmora.com.crt;
       ssl_certificate_key
         /var/openssl/push.solarmora.com/push.solarmora.com.key;

       # it is usually very convenient to have separate files for your
       # access and error log to analyze for possible problems
       access_log /var/log/nginx/nginx.push.acme-corp.com.log;
       error_log /var/log/nginx/nginx.push.acme-corp.com.log;

       location / {
                # assuming the subscriber will run on the same machine
                # on port 8093
                proxy_pass http://localhost:8093;
       }
    }

5\. Restart Nginx to implement the changes:

    myusername@myhost:/etc/nginx$ sudo service nginx restart
     * Restarting nginx nginx
    ...done.

6\. Your server is now configured. To verify the
configuration, try to query your server using curl:

    [myusername@myhost:~]$ curl push.solarmora.com
    <html>
    <head><title>502 Bad Gateway</title></head>
    <body bgcolor="white">
    <center><h1>502 Bad Gateway</h1></center>
    <hr><center>nginx/1.4.6 (Ubuntu)</center>
    </body>
    </html>

This is the expected response given that no downstream server has been configured
(`localhost:8093` in our config file).

## Compile and run examples
To run the examples in this section you need an active [Google Developers Console](https://console.developers.google.com/)
project. We recommend you create one specifically for testing purposes and
keep it separate from your production project. After you create a test project,
you need to create a [service account](https://developers.google.com/accounts/docs/OAuth2ServiceAccount).
Make a note of the service account email address, and put the associated .p12 file somewhere on your server.

### Set up the source code tree

1\. Clone the `play-work.git` repository.

    myusername@myhost:~/code$ git clone
    https://github.com/google/play-work.git
    Cloning into 'play-work'...
    Username for 'https://github.com': username
    Password for 'https://username@github.com':
    remote: Counting objects: 110, done.
    remote: Compressing objects: 100% (60/60), done.
    remote: Total 110 (delta 24), reused 95 (delta 9), pack-reused 0
    Receiving objects: 100% (110/110), 23.88 KiB | 0 bytes/s, done.
    Resolving deltas: 100% (24/24), done.
    Checking connectivity... done.


2\. Install both [Maven](https://maven.apache.org/) and the [Google Protocol Buffers compiler](https://developers.google.com/protocol-buffers/):

    myusername@myhost:~$ mvn -v
    Apache Maven 3.0.5
    Maven home: /usr/share/maven
    Java version: 1.7.0_75, vendor: Oracle Corporation
    Java home: /usr/lib/jvm/java-7-openjdk-amd64/jre
    Default locale: en_US, platform encoding: UTF-8
    OS name: "linux", version: "3.16.0-30-generic", arch: "amd64", family: "unix"
    myusername@myhost:~$ protoc --version
    libprotoc 2.5.0

3\. On Debian-based systems install both Maven and the Google Protocol Buffers compiler as follows:

    $ sudo apt-get install maven protobuf-compiler

4\. The Maven configuration file `pom.xml` assumes that the Protocol Buffers compiler is installed to the `/usr/bin/protoc` directory:

    myusername@myhost:~$ which protoc
    /usr/bin/protoc

   If this is not the case, you can either modify `pom.xml` or symlink `protoc`:

    $ sudo ln -s `which protoc` /usr/bin/protoc

5\. Compile the examples. Verify that you can build the code by running `mvn clean compile assembly:single`. This should produce a file named
`emm-notifications-[version-number]-jar-with-dependencies.jar`, where
`[version number]` is the current version of the example, for example `1.0-SNAPSHOT`.

    myusername@myhost:~/code/play-work/examples/emm-notifications$ ls target/*
    target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar

6\. Verify that you can run the compiled code. It is expected that the code will fail:

    myusername@myhost:~/code/play-work/examples/emm-notifications$ java -cp \
      target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar \
      com.google.android.work.emmnotifications.TestPublisher
      Exception in thread "main" java.lang.IllegalArgumentException:
      You must specify non-default ServiceAccountEmail in
      settings.properties
        at
        com.google.api.client.repackaged.com.google.common.base.Preconditions.checkArgument(Preconditions.java:119)

        at com.google.api.client.util.Preconditions.checkArgument(Preconditions.java:69)
        at com.google.android.work.emmnotifications.Settings.verifyVariable(Settings.java:129)
        at com.google.android.work.emmnotifications.Settings.getSettings(Settings.java:103)
        at com.google.android.work.emmnotifications.TestPublisher.main(TestPublisher.java:39)

7\. You must override some values in the `settings.properties` file. Create a copy of the file and modify the properties in the copy as follows:

    # This should be your own service account's email address
    ServiceAccountEmail=368628613713-t4hfexampledn5lhpdcu1qqfgio01626@developer.gserviceaccount.com
    ServiceAccountP12KeyFile=/opt/secret/secret.p12

    # This will be the name of the service account
    ProjectName=enterprise-cloud-pub-sub
    SubscriptionName=projects/enterprise-cloud-pub-sub/subscriptions/default
    TopicName=projects/enterprise-cloud-pub-sub/topics/default

    # The push endpoint in your [Google Developers Console](https://console.developers.google.com/) project
    PushEndpoint=https://push.solarmora.com


8\. Run the application again, and ensure it no longer crashes (note: you may see a single error
in the log output).

### Run the publisher test code

As well as sample subscriber code, we have also provided sample
code to publish notifications. You need to run this code so that your subscriber will have some messages to read.

In the following example, the application starts, looks for but doesn’t find the
topic specified in `my_settings.properties`, and therefore creates the topic.
It then publishes a message to the topic. This example provides a valuable
testing tool that allows you to emulate messages sent by the Google Play for Work API.


    myusername@myhost:~/code/play-work/examples/emm-notifications$ DEVELOPER_CONSOLE_SETTINGS=./my_settings.properties java -cp \
      target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar com.google.android.work.emmnotifications.TestPublisher
    Feb 27, 2015 1:39:59 PM com.google.android.work.emmnotifications.RetryHttpInitializerWrapper$1 handleResponse
    INFO: RetryHandler: {
      "error": {
        "code": 404,
        "message": "Resource not found (resource=default).",
        "errors": [
          {
            "message": "Resource not found (resource=default).",
            "domain": "global",
            "reason": "notFound"
          }
        ],
        "status": "NOT_FOUND"
      }
    }

    Feb 27, 2015 1:39:59 PM com.google.android.work.emmnotifications.TestPublisher main
    INFO: Topic projects/enterprise-cloud-pub-sub/topics/default doesn't exists, creating it
    Feb 27, 2015 1:40:02 PM com.google.android.work.emmnotifications.TestPublisher main
    INFO: Topic projects/enterprise-cloud-pub-sub/topics/default created
    Feb 27, 2015 1:40:02 PM com.google.android.work.emmnotifications.TestPublisher main
    INFO: Publishing a request: {messages=[{data=CjEKFQoIMTIzMjEzMjESCXJpZ2h0IG5vdxIWY29tLmdvb2dsZS5hbmRyb2lkLmdtcxgA}]}


### Run the subscriber test code

The subscriber test code confirms that you can receive the messages published by the test publisher `TestPublisher`. To run the subscriber test code:

1\. Ensure your code is up to date and compiled, and then run:

    myusername@myhost:~/code/play-work/examples/emm-notifications$ DEVELOPER_CONSOLE_SETTINGS=./my_settings.properties \
      java -cp target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar \
      com.google.android.work.emmnotifications.PushSubscriber
    Feb 27, 2015 1:46:37 PM com.google.android.work.emmnotifications.PushSubscriber main
    INFO: Will be using topic name: projects/enterprise-cloud-pub-sub/topics/default, subscription name: \
      projects/enterprise-cloud-pub-sub/subscriptions/default
    Feb 27, 2015 1:46:38 PM com.google.android.work.emmnotifications.PushSubscriber main
    INFO: Trying to get subscription named projects/enterprise-cloud-pub-sub/subscriptions/default
    Feb 27, 2015 1:46:38 PM com.google.android.work.emmnotifications.RetryHttpInitializerWrapper$1 handleResponse
    INFO: RetryHandler: {
      "error": {
        "code": 404,
        "message": "Resource not found (resource=default).",
        "errors": [
          {
            "message": "Resource not found (resource=default).",
            "domain": "global",
            "reason": "notFound"
          }
        ],
        "status": "NOT_FOUND"
      }
    }

    Feb 27, 2015 1:46:38 PM com.google.android.work.emmnotifications.PushSubscriber main
    INFO: Subscription doesn't exist, will try to create projects/enterprise-cloud-pub-sub/subscriptions/default
    Feb 27, 2015 1:46:43 PM com.google.android.work.emmnotifications.PushSubscriber main
    INFO: Created: {
      "ackDeadlineSeconds" : 600,
      "name" : "projects/enterprise-cloud-pub-sub/subscriptions/default",
      "pushConfig" : {
        "pushEndpoint" : "https://push.acme-corp.com"
      },
      "topic" : "projects/enterprise-cloud-pub-sub/topics/default"
    }

   The subscriber is now running and ready to accept incoming
messages.

2\. Run the publisher again, and new messages will be
added to the log:

    Feb 27, 2015 1:47:24 PM com.google.android.work.emmnotifications.PushSubscriber$1 handle
    INFO: Raw request: {"message":{"data":"CjEKFQoIMTIzMjEzMjESCXJpZ2h0IG5vdxIWY29tLmdvb2dsZS5hbmRyb2lkLmdtcxgA",\
      "attributes":{},"message_id":"71571141246"},"subscription":"/subscriptions/enterprise-cloud-pub-sub/default"}
    Feb 27, 2015 1:47:24 PM com.google.android.work.emmnotifications.PushSubscriber$1 handle
    INFO: Pubsub message received: {
      "attributes" : { },
      "data" : "CjEKFQoIMTIzMjEzMjESCXJpZ2h0IG5vdxIWY29tLmdvb2dsZS5hbmRyb2lkLmdtcxgA",
      "message_id" : "71571141246"
    }
    Feb 27, 2015 1:47:24 PM com.google.android.work.emmnotifications.PushSubscriber$1 handle
    INFO: Message received: product_approval_event {
      common_event_information {
        enterprise_id: "12321321"
        event_notification_sent_timestamp: "right now"
      }
      product_id: "com.google.android.gms"
      approved: false
    }

Congratulations, a message has been properly received and processed!
