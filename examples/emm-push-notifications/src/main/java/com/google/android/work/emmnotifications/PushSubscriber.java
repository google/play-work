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
import com.google.common.io.CharStreams;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * THIS WILL BE EXTERNAL CODE
 *
 * To run this sample code:
 * <ol>
 *   <li>Modify settings.properties or specify a different file via DEVELOPER_CONSOLE_SETTINGS
 *   environment variable</li>
 *   <li>Build a deploy jar using <code>mvn clean compile assembly:single</code></li>
 *   <li>Execute it as <code>
 *     java -cp target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *      com.google.android.work.emmnotifications.PushSubscriber</code></li>
 * </ol>
 */
public class PushSubscriber {
  private static final String PUSH_ENDPOINT = "https://e.r-k.co";

  // this can be any port
  private static final int PORT = 8093;

  private static final Logger LOG = Logger.getLogger(PushSubscriber.class.getName());
  public static final String MESSAGE_FIELD = "message";

  public static void main(String[] args) throws Exception {
    Pubsub client = ServiceAccountConfiguration.createPubsubClient(
        Settings.getSettings().getServiceAccountEmail(),
        Settings.getSettings().getServiceAccountP12KeyPath());

    // First we check if subscription actually exists for this subscription name.
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

      LOG.info("Will be re-using existing subscription: " + subscription.toPrettyString());
    } catch (HttpResponseException e) {

      // subscription not found
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
                    .setPushEndpoint(PUSH_ENDPOINT))) // FQDN with valid SSL certificate
            .execute();

        LOG.info("Created: " + subscription.toPrettyString());
      }
    }

    // Kicking off HttpServer which will listen on specified port and process all
    // incoming push pub/sub notifications
    HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
    server.createContext("/", new HttpHandler() {
      public void handle(HttpExchange httpExchange) throws IOException {
        String rawRequest = CharStreams.toString(
            new InputStreamReader(httpExchange.getRequestBody()));
        LOG.info("Raw request: " + rawRequest);

        try {
          // Note, that documentation says this is PubsubMessage, which it isn't
          JsonParser parser = JacksonFactory.getDefaultInstance().createJsonParser(rawRequest);
          parser.skipToKey(MESSAGE_FIELD);

          PubsubMessage message = parser.parseAndClose(PubsubMessage.class);
          LOG.info("Pubsub message received: " + message.toPrettyString());

          // Decoding Protocol Buffers message from array of bytes
          EmmPubsub.MdmPushNotification mdmPushNotification = EmmPubsub.MdmPushNotification
              .newBuilder()
              .mergeFrom(message.decodeData())
              .build();

          LOG.info("Message received: " + mdmPushNotification.toString());
        } catch (Throwable e) {
          LOG.log(Level.WARNING, "Error occured when decoding message", e);
        }

        // CloudPubSub will interpret 2XX as ACK, anything that isn't 2XX will trigger a retry
        httpExchange.sendResponseHeaders(204, 0);
        httpExchange.close();
      }
    });

    server.setExecutor(null);
    server.start(); // Will keep running until killed
  }
}
