Play for Work EMM Notifications
===============================

EMM notifications allow EMMs to receive push or pull notifications when
changes that require admin attention occur. Information about the changes is
delivered in the form of a push notification to the specified HTTPS endpoint;
alternatively, an EMM server can open a connection and wait for notification to be
sent.

*Before you start:* it is strongly recommended to familiarise yourself with
the official [Cloud Pub/Sub
documentation](https://cloud.google.com/pubsub/overview).

Push endpoint configuration
---------------------------

Before notifications can be received, the endpoint must be configured. The endpoint must meet the following criteria:

* You must own the domain since you will have to verify it in the [Google Developer Console](https://console.developers.google.com)
* You should be able to run service on port 443 (SSL)
* You must have a CA-signed SSL certificate. Self-signed certificate will not work.
* The web server you are running should support [WebHook](http://en.wikipedia.org/wiki/Webhook)

In this example we will configure the popular Nginx server in reverse-proxy mode to connect to the subscriber app (in `PushSubscriber.java`) running on port 8093. Following assumptions are made:

* Ubuntu 14.04 or later server is used, this is likely to work without any changes on any Debian based server; for RedHat based servers config paths likely to be different.
* You have access to `sudo` on the server

You should start with producing an SSL certificate. Please note, that you must specify your actual server name instead of `push.acme-corp.com` in your certificate. You can use any subdomain as long as the A record of this subdomain points to your server.

    kirillov@ubuntu3:/tmp$ sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout cert.key -out cert.crt

    Generating a 2048 bit RSA private key
    ...............................................................................................+++
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

Verify that certificate file was written:

    kirillov@ubuntu3:/tmp$ ls cert*
    cert.crt  cert.key

Next step is to get this certificate signed. You have to produce so-called [Certificate Signing Request](http://en.wikipedia.org/wiki/Certificate_signing_request) (CSR) which you will upload to your signer.

    kirillov@ubuntu3:/tmp$ sudo openssl  x509 -x509toreq -in cert.crt \
      -out cert.csr -signkey cert.key 
    Getting request Private Key
    Generating certificate request
    kirillov@ubuntu3:/tmp$ ls cert.*
    cert.crt  cert.csr  cert.key

Check the content of the CSR file, it should look like this:


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

Now you will upload the part of your certificate between `BEGIN ...` and `END ...` inclusive to your certificate authority. The process might be different for different providers, but the general structure is:

1. File is uploaded / pasted to the CA website
2. They validate it and start processing
3. Once processing was finished they provide a file with a signed certificate to download. 

Usually the produced files contains multiple files -- the signed certificate itself and the CA's certificate confirming they are eligible to sign certificates. You will have to concatenate all `*.crt` certificate files in the downloaded bundle to a single bundle file (let's assume you will name it bundle.push.acme-corp.com.crt)

Configuring Nginx
-----------------

In this section we will configure the popular open-source web server and reverse proxy server to serve the endpoint and forward all incoming requests to the subscriber endpoint.

You should start with installing Nginx on your server:

    sudo apt-get update
    sudo apt-get install nginx

It's not crucial, but you may want to check Nginx's version in case you need to do any troubleshooting down the road. Newer versions of Nginx provide a few more features as well.

    kirillov@ubuntu3:/tmp$ nginx -v
    nginx version: nginx/1.4.6 (Ubuntu)


Now edit `/etc/nginx/nginx.conf` and ensure that following lines are present and uncommented:

    include /etc/nginx/conf.d/*.conf;
    include /etc/nginx/sites-enabled/*;

This will ensure that extra files we create in sites-enabled directory will be processed by Nginx. 

Copy your certificate files to a safe location, readable by `www-data` user and preferably not readable by any other user (you may need to adjust user name if your web server is running as a different user).

    sudo mkdir -p /var/openssl/push.acme-corp.com
    sudo mv /tmp/cert.key \
        /var/openssl/push.acme-corp.com/push.acme-corp.com.key
    sudo mv /tmp/bundle.push.acme-corp.com.crt \
        /var/openssl/push.acme-corp.com/bundle.push.acme-corp.com.crt

Now we create a new server configuration. Edit `push.acme-corp.com` in `/etc/nginx/sites-enabled` (it is recommended to use your actual server fully-qualified domain name as the file name). You should have something similar to this:

    server {
       listen 443;
       server_name push.acme-corp.com;

       ssl on;
       ssl_certificate /var/openssl/push.acme-corp.com/bundle.push.acme-corp.com.crt;
       ssl_certificate_key /var/openssl/push.acme-corp.com/push.acme-corp.com.key;

       # it is usually very convenient to have separate files for your
       # access and error log to analyse for possible problems
       access_log /var/log/nginx/nginx.push.acme-corp.com.log;
       error_log /var/log/nginx/nginx.push.acme-corp.com.log;

       location / {
                # assuming subscriber will run on the same machine
                # on port 8093
                proxy_pass http://localhost:8093;
       }
    }

Restart Nginx to pick up the changes:

    kirillov@ubuntu3:/etc/nginx$ sudo service nginx restart
     * Restarting nginx nginx
    ...done.

Now you should have your server configured. You can quickly verify the configuration by trying to query it using curl:

    [kirillov@sgzmd:~]$ curl push.acme-corp.com
    <html>
    <head><title>502 Bad Gateway</title></head>
    <body bgcolor="white">
    <center><h1>502 Bad Gateway</h1></center>
    <hr><center>nginx/1.4.6 (Ubuntu)</center>
    </body>
    </html>

This is expected response given no downstream server was configured (`localhost:8093` in our config file).

Compiling and running examples
------------------------------

### Configuring Developer Console account

In order to run these examples you should have an active [Developer Console](https://console.developers.google.com) project. We recommend you to create one specifically for experimentation purposes and not re-use your production project. Once you've created one, you should create a service account using the steps described [here](https://developers.google.com/accounts/docs/OAuth2ServiceAccount#creatinganaccount). You will need to make a note of the service account email address and download the `p12` file to somewhere on your server.

### Setting up the source code tree

Clone the git repository:

    ubuntu@ubuntu3:~/code$ git clone https://github.com/google/play-work.git
    Cloning into 'play-work'...
    Username for 'https://github.com': sigizmund
    Password for 'https://sigizmund@github.com': 
    remote: Counting objects: 110, done.
    remote: Compressing objects: 100% (60/60), done.
    remote: Total 110 (delta 24), reused 95 (delta 9), pack-reused 0
    Receiving objects: 100% (110/110), 23.88 KiB | 0 bytes/s, done.
    Resolving deltas: 100% (24/24), done.
    Checking connectivity... done.

To compile these examples you will have to have Maven installed. Check your maven version by running `mvn -v`. If you do not have maven, you can usually install it by running `sudo apt-get install maven` on the most Debian-based distributions. You will also need to have Google Protocol Buffers compiler installed. You can install it by running `sudo apt-get install protobuf-compiler`. Ensure that you have `protoc` in your `/usr/bin`; if it is installed elsewhere you can either symlink it to `/use/bin` or modify Maven `pom.xml` to point it to a different location.

Verify that you can build the code by running `mvn clean compile assembly:single`. This should produce a file named `emm-notifications-[version-number]-jar-with-dependencies.jar`, where `[version number]` is the current version of the example (e.g. `1.0-SNAPSHOT`).

    ubuntu@ubuntu3:~/code/play-work/examples/emm-notifications$ ls target/*
    target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar

Verify that you can run the compiled code:

    ubuntu@ubuntu3:~/code/play-work/examples/emm-notifications$ java -cp \
      target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar \
      com.google.android.work.emmnotifications.FauxPublisher
      Exception in thread "main" java.lang.IllegalArgumentException: You must specify non-default ServiceAccountEmail in settings.properties
        at com.google.api.client.repackaged.com.google.common.base.Preconditions.checkArgument(Preconditions.java:119)
        at com.google.api.client.util.Preconditions.checkArgument(Preconditions.java:69)
        at com.google.android.work.emmnotifications.Settings.verifyVariable(Settings.java:129)
        at com.google.android.work.emmnotifications.Settings.getSettings(Settings.java:103)
        at com.google.android.work.emmnotifications.FauxPublisher.main(FauxPublisher.java:39)

It is expected for this code to fail with this error. You will need to override values in `settings.properties` file for it to work. We recommend to copy this file into your own file, e.g. `my_settings.properties` and change values there. Modify your properties file to match:

    # This should be your own service account email address
    ServiceAccountEmail=368628613713-t4hfexampledn5lhpdcu1qqfgio01626@developer.gserviceaccount.com
    ServiceAccountP12KeyFile=/opt/secret/secret.p12

    # This will be the name of the service account you will create                                                                     
    ProjectName=enterprise-cloud-pub-sub
    SubscriptionName=projects/enterprise-cloud-pub-sub/subscriptions/default
    TopicName=projects/enterprise-cloud-pub-sub/topics/default

    # Define new push endpoint in developer console project                                                                            
    PushEndpoint=https://push.acme-corp.com

### Running the test publisher code

Now you can try to launch your application:

    ubuntu@ubuntu3:~/code/play-work/examples/emm-notifications$ DEVELOPER_CONSOLE_SETTINGS=./my_settings.properties java -cp \
      target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar com.google.android.work.emmnotifications.FauxPublisher
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

    Feb 27, 2015 1:39:59 PM com.google.android.work.emmnotifications.FauxPublisher main
    INFO: Topic projects/enterprise-cloud-pub-sub/topics/default doesn't exists, creating it
    Feb 27, 2015 1:40:02 PM com.google.android.work.emmnotifications.FauxPublisher main
    INFO: Topic projects/enterprise-cloud-pub-sub/topics/default created
    Feb 27, 2015 1:40:02 PM com.google.android.work.emmnotifications.FauxPublisher main
    INFO: Publishing a request: {messages=[{data=CjEKFQoIMTIzMjEzMjESCXJpZ2h0IG5vdxIWY29tLmdvb2dsZS5hbmRyb2lkLmdtcxgA}]}

What happened is: application started, it didn't find the topic you've specified in `my_settings.properties` so it went on and created it. Once this was done, it has published a message to this topic. This example should provide a valuable testing tool which would allow to emulate messages sent by the Play for Work API.

### Running the subscriber

Now we will run a subscriber to ensure we can receive the messages publisher by `FauxPublisher`. Ensure your code is up to date and compiled, and run:

```
ubuntu@ubuntu3:~/code/play-work/examples/emm-notifications$ DEVELOPER_CONSOLE_SETTINGS=./my_settings.properties java -cp target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar com.google.android.work.emmnotifications.PushSubscriber
Feb 27, 2015 1:46:37 PM com.google.android.work.emmnotifications.PushSubscriber main
INFO: Will be using topic name: projects/enterprise-cloud-pub-sub/topics/default, subscription name: projects/enterprise-cloud-pub-sub/subscriptions/default
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
```

Now subscriber is running and ready to accept incoming messages. Run publisher one more time; you will see new messages added to the log:


```
Feb 27, 2015 1:47:24 PM com.google.android.work.emmnotifications.PushSubscriber$1 handle
INFO: Raw request: {"message":{"data":"CjEKFQoIMTIzMjEzMjESCXJpZ2h0IG5vdxIWY29tLmdvb2dsZS5hbmRyb2lkLmdtcxgA","attributes":{},"message_id":"71571141246"},"subscription":"/subscriptions/enterprise-cloud-pub-sub/default"}
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
```

This means the message has been received and processed correctly.