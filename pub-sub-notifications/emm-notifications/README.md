# Play for Work EMM Notifications

EMM notifications allow EMMs to receive push or pull notifications, in particular
regarding changes that may require an Admin's attention. Information about
changes can be received in one of two ways: either the EMM receives a push notification to a
specified HTTPS endpoint (a *push subscription*), or an EMM server can open a
connection and wait for notifications to be sent (a *pull subscription*).

## Before you start

It is strongly recommended that you familiarize yourself with
the [Cloud Pub/Sub
documentation](https://cloud.google.com/pubsub/overview).

In this document we will only demonstrate how to set up a *push subscription* to receive push
notifications. Examples for
enabling a *pull subscription* are available in the [Cloud Pub/Sub
documentation](https://cloud.google.com/pubsub/overview "Cloud Pub/Sub Overview").

## Push endpoint configuration
### Requirements

In order to receive push notifications, you must configure an endpoint to which the notifications
will be pushed. While it is not required that your endpoint [run on Google AppEngine](https://cloud.google.com/pubsub/prereqs#register), it must, however, meet the following criteria:

* You must own the endpoint's domain, and you will have to verify your ownership in the
  [Google Developer Console](https://console.developers.google.com "Google Developer Console").
* You must be able to run a service on port 443 (SSL).
* You must have a CA-signed SSL certificate. Self-signed certificates
  are not supported.
* The web server you are running must support
  [WebHooks](http://en.wikipedia.org/wiki/Webhook "WebHooks").

In this example we will configure a Nginx server in
reverse-proxy mode to connect to the subscriber app (in
`PushSubscriber.java`) running on port 8093. This example makes the following assumptions:

* The setup process occurs on Ubuntu 14.04. This process is untested, but likely to work
  without changes, on all Debian-based distributions. Furthermore this process is likely to be similar on other distributions (such as those based on RedHat), but the location of the configuration files may be different.
* You have access to `sudo` on the server

### Generate and sign your SSL certificate

First, produce an SSL certificate, using your actual server name in place of
`push.acme-corp.com`. You can use any subdomain as
long as the `A` record of this subdomain points to your server:

    user@ubuntu3:/tmp$ sudo openssl req -x509 -nodes -days 365
    -newkey rsa:2048 -keyout cert.key -out cert.crt

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
    Organization Name (eg, company) [Internet Widgits Pty Ltd]:ACME Corp, Inc.
    Organizational Unit Name (eg, section) []:Creative Anvils
    Common Name (e.g. server FQDN or YOUR name) []:push.acme-corp.com
    Email Address []:admin@acme-corp.com

Before proceeding, verify that certificate file was written:

    user@ubuntu3:/tmp$ ls cert*
    cert.crt  cert.key

Next, you'll need to have this certificate signed. Produce
a [Certificate Signing Request](http://en.wikipedia.org/wiki/Certificate_signing_request)
(CSR) to upload to your signer.

```
user@ubuntu3:/tmp$ sudo openssl  x509 -x509toreq -in cert.crt \
-out cert.csr -signkey cert.key
Getting request Private Key
Generating certificate request
user@ubuntu3:/tmp$ ls cert.*
cert.crt  cert.csr  cert.key
```

Ensure the content of the CSR file resembles the following:

    Certificate Request:
    Data:
        Version: 0 (0x0)
        Subject: C=GB, ST=England, L=London, O=ACME Corp, Inc.,
        OU=Creative Anvils,
        CN=push.acme-corp.com/emailAddress=admin@acme-corp.com
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

Upload the part of your certificate between `BEGIN ...`
and `END ...` inclusive to your certificate authority. The process
might be different for different providers, but in general the steps are as follows:

1. Uploaded to or paste your file on the CA site
2. The CA will validate and begin processing
3. Once processing is finished, the CA provides a file with a signed
   certificate for download.

Typically the CA will output multiple files: the signed
certificate itself and the CA's certificate confirming that the CA is
eligible to sign certificates. Concatenate all
`*.crt` certificate files in the downloaded bundle into a single bundle
file, for example `bundle.push.acme-corp.com.crt':

    $ cat *.crt > bundle.push.acme-corp.com.crt

### Configure Nginx

Next you will need to configure your server to serve the endpoint and forward all
incoming requests to the subscriber server.

Start by installing Nginx on your server:

    sudo apt-get update
    sudo apt-get install nginx
    
    nginx -v
    nginx version: nginx/1.4.6 (Ubuntu)


Now edit `/etc/nginx/nginx.conf` and include the following:

    include /etc/nginx/conf.d/*.conf;
    include /etc/nginx/sites-enabled/*;

This ensures that the 'server' configuration file you create in a later step within the
`sites-enabled` directory will be processed by Nginx.

Copy your certificate files to a safe location, readable by the
`www-data` user, but preferably not readable by any other user (you may need
to adjust the user name if your web server is running as a different
user):

    sudo mkdir -p /var/openssl/push.acme-corp.com
    sudo mv /tmp/cert.key \
        /var/openssl/push.acme-corp.com/push.acme-corp.com.key
    sudo mv /tmp/bundle.push.acme-corp.com.crt \
        /var/openssl/push.acme-corp.com/bundle.push.acme-corp.com.crt

Next create a new `server` configuration. Edit `push.acme-corp.com`
in `/etc/nginx/sites-enabled` (it is recommended to use your actual
server's fully-qualified domain name as the file name) as follows:

    server {
       listen 443;
       server_name push.acme-corp.com;

       ssl on;
       ssl_certificate
         /var/openssl/push.acme-corp.com/bundle.push.acme-corp.com.crt;
       ssl_certificate_key
         /var/openssl/push.acme-corp.com/push.acme-corp.com.key;

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

Restart Nginx to put the changes in effect:

    user@ubuntu3:/etc/nginx$ sudo service nginx restart
     * Restarting nginx nginx
    ...done.

Your server is now configured! You can quickly verify the
configuration by trying to query your server using curl:

    [user@sgzmd:~]$ curl push.acme-corp.com
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
### Configure the Developer Console account

In order to run these examples you should have an active
[Developer Console](https://console.developers.google.com "Google Developer Console") project. It is
recommended that you create one specifically for testing EMM notifications,
rather than using your production project. Once your project is created,
create a service account using the steps described [here](https://developers.google.com/accounts/docs/OAuth2ServiceAccount),
make a note of the service account email address, and
put the associated `.p12` file somewhere on your server.

### Set up the source code tree

First, clone the git repository of which this README is a part. To then compile these examples you will need to install both [Maven](https://maven.apache.org/) and the [Google Protocol Buffers compiler](https://developers.google.com/protocol-buffers/):

    user@ubuntu3:~$ mvn -v
    Apache Maven 3.0.5
    Maven home: /usr/share/maven
    Java version: 1.7.0_75, vendor: Oracle Corporation
    Java home: /usr/lib/jvm/java-7-openjdk-amd64/jre
    Default locale: en_US, platform encoding: UTF-8
    OS name: "linux", version: "3.16.0-30-generic", arch: "amd64", family: "unix"
    user@ubuntu3:~$ protoc --version
    libprotoc 2.5.0

On Debian-based systems you can install them by running:

    sudo apt-get install maven protobuf-compiler

It is assumed that in the Maven configuration file `pom.xml`, the Protocol Buffers compiler is installed to `/usr/bin/protoc`:

    user@ubuntu3:~$ which protoc
    /usr/bin/protoc
    

If this is not the case, you can either modify `pom.xml` or symlink `protoc`:

    $ sudo ln -s `which protoc` /usr/bin/protoc

Verify that you can build the code by running `mvn clean compile
assembly:single`. This should produce a file named
`emm-notifications-[version-number]-jar-with-dependencies.jar`, where
`[version number]` is the current version of the example
(e.g. `1.0-SNAPSHOT`).

    ubuntu@ubuntu3:~/code/play-work/examples/emm-notifications$ ls target/*
    target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar

Verify that you can run the compiled code. It is expected that the code will fail:

    ubuntu@ubuntu3:~/code/play-work/examples/emm-notifications$ java -cp \
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

Next you'll need to override some values in the `settings.properties` file. It is recommended that
you create your own copy of this file and change these values in that copy. Modify your
properties file to match the following:

    # This should be your own service account's email address
    ServiceAccountEmail=368628613713-t4hfexampledn5lhpdcu1qqfgio01626@developer.gserviceaccount.com
    ServiceAccountP12KeyFile=/opt/secret/secret.p12

    # This will be the name of the service account
    ProjectName=enterprise-cloud-pub-sub
    SubscriptionName=projects/enterprise-cloud-pub-sub/subscriptions/default
    TopicName=projects/enterprise-cloud-pub-sub/topics/default

    # The push endpoint in your developer console project
    PushEndpoint=https://push.acme-corp.com
    

Try and run the application again, and ensure it no longer crashes (note: you may see a single error
in the log output).

### Run the test publisher code

As well as example code that will provide the basis of your subscriber, we have also included 
code that can be used to publish notifications. Run this now so that your subscriber
will have some messages to read:

    ubuntu@ubuntu3:~/code/play-work/examples/emm-notifications$ DEVELOPER_CONSOLE_SETTINGS=./my_settings.properties java -cp \
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

Here's what happened in this example:
1. The application started
2. The application didn't find the topic that was specified in `my_settings.properties`
3. Accordingly, the applicatio went on and created it the topic specified in 'my_settings_properties'
4. Once the topic was created, the application published a message to this topic.

This example thus provides a valuable testing tool that
allows you to emulate messages sent by the Play for Work API.

### Run the subscriber

Next, run your subscriber to ensure you can receive messages
published by `TestPublisher`. Ensure your code is up to date and
compiled, and then run:

    ubuntu@ubuntu3:~/code/play-work/examples/emm-notifications$ DEVELOPER_CONSOLE_SETTINGS=./my_settings.properties \
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
messages. Run the publisher one more time, and new messages will be
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
