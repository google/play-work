/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package com.google.android.work.emmnotifications;

import com.google.android.work.pubsub.EmmPubsub;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.PushConfig;
import com.google.api.services.pubsub.model.Subscription;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is a sample subscriber code. To run it you need to have an SSL endpoint configured
 * and run this code either on port 443 (update below) on the server backing this endpoint or
 * configure a reverse proxy from port 443 to 8093.
 *
 * Details: [link will be here]
 *
 * To run this sample code:
 * <ol>
 *   <li>Modify settings.properties or specify a different file via the DEVELOPER_CONSOLE_SETTINGS
 *   environment variable</li>
 *   <li>Build a deploy jar using <code>mvn clean compile assembly:single</code></li>
 *   <li>Execute it as <code>
 *     java -cp target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *      com.google.android.work.emmnotifications.PushSubscriber</code></li>
 * </ol>
 */
public class PushSubscriber {
  // This can be any port
  private static final int PORT = 8093;

  private static final Logger LOG = Logger.getLogger(PushSubscriber.class.getName());
  
  // Notification comes in form of a JSON message. The actual encoded protocol buffers
  // message is located in the field called "message".
  public static final String MESSAGE_FIELD = "message";

  public static void main(String[] args) throws Exception {
    Pubsub client = ServiceAccountConfiguration.createPubsubClient(
        Settings.getSettings().getServiceAccountEmail(),
        Settings.getSettings().getServiceAccountP12KeyPath());
    
    ensureSubscriptionExists(client);
    
    // Kicking off HttpServer which will listen on the specified port and process all
    // incoming push pub/sub notifications
    HttpServer server = HttpServer.create(new InetSocketAddress(
        Settings.getSettings().getPort()), 0);
    
    server.createContext("/", new HttpHandler() {
      public void handle(HttpExchange httpExchange) throws IOException {
        String rawRequest = CharStreams.toString(
            new InputStreamReader(httpExchange.getRequestBody()));
        LOG.info("Raw request: " + rawRequest);

        try {
          JsonParser parser = JacksonFactory.getDefaultInstance().createJsonParser(rawRequest);
          parser.skipToKey(MESSAGE_FIELD);

          PubsubMessage message = parser.parseAndClose(PubsubMessage.class);
          LOG.info("Pubsub message received: " + message.toPrettyString());

          // Decode Protocol Buffer message from base64 encoded byte array.
          EmmPubsub.MdmPushNotification mdmPushNotification = EmmPubsub.MdmPushNotification
              .newBuilder()
              .mergeFrom(message.decodeData())
              .build();

          LOG.info("Message received: " + mdmPushNotification.toString());
        } catch (InvalidProtocolBufferException e) {
          LOG.log(Level.WARNING, "Error occured when decoding message", e);
        }

        // CloudPubSub will interpret 2XX as ACK, anything that isn't 2XX will trigger a retry
        httpExchange.sendResponseHeaders(HttpStatusCodes.STATUS_CODE_NO_CONTENT, 0);
        httpExchange.close();
      }
    });

    server.setExecutor(null);
    server.start(); // Will keep running until killed
  }

  /**
   * Verifies that the subscription with the name defined in settings file actually exists and 
   * points to a correct topic defined in the same settings file. If the subscription doesn't
   * exist, it will be created.
   */
  private static void ensureSubscriptionExists(Pubsub client) throws IOException {
    // First we check if the subscription with this name actually exists.
    Subscription subscription = null;

    String topicName = Settings.getSettings().getTopicName();
    String subName = Settings.getSettings().getSubscriptionName();

    LOG.info("Will be using topic name: " + topicName + ", subscription name: " + subName);

    try {
      LOG.info("Trying to get subscription named " + subName);
      subscription = client
          .projects()
          .subscriptions()
          .get(subName)
          .execute();
      
      Preconditions.checkArgument(
          subscription.getTopic().equals(topicName),
          "Subscription %s already exists but points to a topic %s and not %s." +
              "Please specify a different subscription name or delete this subscription",
          subscription.getName(),
          subscription.getTopic(),
          topicName);

      LOG.info("Will be re-using existing subscription: " + subscription.toPrettyString());
    } catch (HttpResponseException e) {

      // Subscription not found
      if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        LOG.info("Subscription doesn't exist, will try to create " + subName);

        // Creating subscription
        subscription = client
            .projects()
            .subscriptions()
            .create(subName, new Subscription()
                .setTopic(topicName)   // Name of the topic it subscribes to
                .setAckDeadlineSeconds(600)
                .setPushConfig(new PushConfig()
                    // FQDN with valid SSL certificate
                    .setPushEndpoint(Settings.getSettings().getPushEndpoint())))
            .execute();

        LOG.info("Created: " + subscription.toPrettyString());
      }
    }
  }
}
