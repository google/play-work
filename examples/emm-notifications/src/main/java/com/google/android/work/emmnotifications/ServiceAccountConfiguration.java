package com.google.android.work.emmnotifications;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.PubsubScopes;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Creates a Pubsub client with the service account.
 */
public class ServiceAccountConfiguration {

  private static final JsonFactory JSON_FACTORY =
      JacksonFactory.getDefaultInstance();

  public static Pubsub createPubsubClient(String serviceAccountEmail, String privateKeyFilePath)
      throws IOException, GeneralSecurityException {
    HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
    GoogleCredential credential = new GoogleCredential.Builder()
        .setTransport(transport)
        .setJsonFactory(JSON_FACTORY)
        .setServiceAccountScopes(PubsubScopes.all())

        // Obtain this from the "APIs & auth" -> "Credentials"
        // section in the Google Developers Console:
        // https://console.developers.google.com/
        // (and put the e-mail address into your system property obviously)
        .setServiceAccountId(serviceAccountEmail)

        // Download this file from "APIs & auth" -> "Credentials"
        // section in the Google Developers Console:
        // https://console.developers.google.com/
        .setServiceAccountPrivateKeyFromP12File(new File(privateKeyFilePath))
        .build();


    // Please use custom HttpRequestInitializer for automatic
    // retry upon failures.  We provide a simple reference
    // implementation in the "Retry Handling" section.
    HttpRequestInitializer initializer =
        new RetryHttpInitializerWrapper(credential);
    return new Pubsub.Builder(transport, JSON_FACTORY, initializer)
        .setApplicationName("PubSub Example")
        .build();
  }
}