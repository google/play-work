package com.google.android.work.emmnotifications;

import com.google.android.work.pubsub.EmmPubsub;
import com.google.api.client.http.HttpResponseException;
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
import org.apache.commons.cli.CommandLine;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * THIS WILL BE EXTERNAL CODE
 */
public class PushSubscriber {
  private static final String PUSH_ENDPOINT = "https://e.r-k.co";
  private static final int PORT = 8093;

  private static final Logger LOG = Logger.getLogger(PushSubscriber.class.getName());
  public static final String MESSAGE_FIELD = "message";

  public static void main(String[] args) throws Exception {
    CommandLine commandLine = Common.getCommandLine(args);
    Pubsub client = Common.makePubsubClient();

    // First we check if subscription actually exists for this subscription name.
    Subscription subscription = null;

    String topicName = commandLine.getOptionValue(Common.TOPIC_NAME, Common.getDefaultTopicName());
    String subName = commandLine.getOptionValue(Common.SUBSCRIPTION_NAME, Common.getDefaultSubscriptionName());

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
      if (e.getStatusCode() == 404) {
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
        String rawRequest = CharStreams.toString(new InputStreamReader(httpExchange.getRequestBody()));
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
