# Externally-Hosted APK Definition File Generator

## Overview
To publish a self-hosted private app, a customer must first build an externally-hosted APK definition 
file (plain-text JSON file). This definition file replaces the APK within Google Play so the app may be hosted externally,
containing only a minimal set of information rather than the entire APK itself.

The JSON file format of this definition file looks like this:

    {
      "package_name" : "com.example.package.name",
      "version_name" : "0.8",
      "version_code" : 12,
      "minimum_sdk" : 1,
      “maximum_sdk” : 19,
      "application_label" : "My Package Name",
      "file_sha1_base64" : "7qiBi1Z\m8wFmghQUp3H5FwiGRg0=",
      "file_sha256_base64" : "qIiv0CDHW6esQtsAN4fZzc822szPCXsg6bwgx0ZIhP0=",
      "icon_base64" : "base64encodedicon",
      "file_size" : 14638, //bytes
      "certificate_base64" : ["BASE64ENCODEDCERTIFICATE"],
      "externally_hosted_url" : "https://www.example.com/filename.apk",
    }

Please note that all fields save for “maximum_sdk” are mandatory, and will be evaluated on-device
against the APK that is installed to ensure the correct APK has been provided. 
The JSON object may also include the following additional fields:

    {
      "package_name" : "com.example.package.name",
      ...
      "externally_hosted_url" : "https://www.example.com/filename.apk",
      “uses-permission” : [{“name”: “android.permission.WRITE_CONTACTS”},
          {“name”:”android.permission.WRITE_EXTERNAL_STORAGE”,
           “maxSdkVersion”:18} ],
      “uses-feature” : [“android.hardware.bluetooth", "android.hardware.camera”],
    }

Please ensure that the permissions required by the app are present in the JSON file and correct, to
avoid your app being unable to access the correct features when installed on the device.

##Android for Work Self-Hosting Tool
This is a Google-developed tool for generating definition files from APKs. You will need aapt
installed on your machine, and available on your system’s PATH.

Execute using the following command, replacing where appropriate:

    python externallyhosted.py --apk=<path/to/apk.apk> \
      --externallyHostedUrl="<https://www.example.com/test.apk>”

This will print the required contents of the definition file to your console.

##Authenticating the download on the Enterprise Server

When the Google Play client makes a request to download the self-hosted APK from an enterprise server,
the request will include a cookie which contains a JSON Web Token.

We strongly recommend that you use a standard library (which are available in many languages) to
decode the JWT, in order to ensure all verification is correctly performed before accepting the
authentication token (this includes ensuring the token has not expired).

The public key needed to verify the JWT is unique to the application, and available in the Google Play
Developer Console in the application’s “Services and API” section, listed under “your license key for
this application.” The private key is owned by Google, so the signature confirms the authenticity of
the request.

Once verified and decoded, the JWT will provide the following information about the download request:

    {
      “aud” : “https://www.example.com/test.apk”,
      “uri-query” : “url_param_1=5&url_param_2=test”, // URL query parameters
      “iss” : “https://play.google.com”,
      “exp” : “<expiry-timestamp>”
      “cid” : “user_id_token”
    }

Optionally, the user ID token can be matched with a user via the
[EMM API](https://developers.google.com/play/enterprise/v1/users/get).
