Play for Work EMM Notifications
===============================

EMM notifications allow EMMs to receive push or pull notifications when some
changes that require admin attention occur. Information about the changes is
delivered in form of a push notification to the specified HTTPS endpoint;
alternatively, server can open a connection and wait for notification to be
sent.

Push endpoint configuration
---------------------------

Before notifications can be received, the endpoint must be configured. Endpoint must meet following criteria:

* You must own the domain since you will have to verify it in [Developer Console](https://console.developers.google.com)
* You should be able to run service on port 443 (SSL)
* You must have a signed SSL certificate. Self-signed certificate will not work.
* Webserver you are running should support [WebHook](http://en.wikipedia.org/wiki/Webhook)

In this example we will configure popular Nginx server in reverse-proxy mode to connect to the subscriber app running on port 8093. Following assumptions are made:

* Ubuntu 14.04 or later server is used, this is likely to work without any changes on any Debian based server; for RedHat based servers config paths likely to be different.
* You have access to `sudo` on the server

You should start with producing an SSL certificate. Please note, that you must specify your actual server name instead of `push.acme-corp.com` in your certificate. You can use any subdomain as long as the A record of this subdomain points to your server.

    kirillov@ubuntu3:/tmp$ sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout cert.key -out cert.crtGenerating a 2048 bit RSA private key
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

Next step is to sign this certificate. You have to produce so-called [Certificate Signing Request](http://en.wikipedia.org/wiki/Certificate_signing_request) (CSR) which you will upload to your signer.

    kirillov@ubuntu3:/tmp$ sudo openssl  x509 -x509toreq -in cert.crt \
      -out cert.csr -signkey cert.key 
    Getting request Private Key
    Generating certificate request
    kirillov@ubuntu3:/tmp$ ls cert.*
    cert.crt  cert.csr  cert.key

Check the content of the CSR file, it should look like that:


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

Now you will upload part of your certificate between `BEGIN ...` and `END ...` including those lines to your certificate authority. The process might be different for different providers, but the general structure is:

1. File is uploaded / pasted to the authority website
2. They validate it and start processing
3. Once processing was finished they provide a file with a signed certificate to download. 

Usually produced files contains multiple files -- signed certificate itself and certificates authority's certificate confirming they are eligible to sign certificates. You will have to concatenate all `*.crt` certificate files in the downloaded bundle to a single bundle file and configure Nginx to use it as your certificate (let's assume you will name it bundle.push.acme-corp.com.crt)

Configuring Nginx
-----------------

In this section we will configure popular open-source web server and reverse proxy server to serve the endpoint and forward all incoming requests to the subscriber endpoint.

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

    [kirillov@sgzmd:~]$ curl e.r-k.co
    <html>
    <head><title>502 Bad Gateway</title></head>
    <body bgcolor="white">
    <center><h1>502 Bad Gateway</h1></center>
    <hr><center>nginx/1.4.6 (Ubuntu)</center>
    </body>
    </html>

This is expected response given no downstream server was configured (`localhost:8093` in our config file).