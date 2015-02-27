package com.google.android.work.emmnotifications.notpublic;

import com.google.android.work.emmnotifications.ServiceAccountConfiguration;
import com.google.android.work.emmnotifications.Settings;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.AcknowledgeRequest;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.PullRequest;
import com.google.api.services.pubsub.model.PullResponse;
import com.google.api.services.pubsub.model.ReceivedMessage;
import com.google.api.services.pubsub.model.Subscription;
import com.google.common.collect.ImmutableList;

import java.net.SocketTimeoutException;
import java.util.logging.Logger;

/**
 * ---- NOT PUBLIC CODE ----
 *
 * Cloud PubSub backends currently 500's when trying to ACK the message therefore this code is NOT PUBLIC.
 */
public class PullSubscriber {
  private static final Logger LOG = Logger.getLogger(PullSubscriber.class.getName());

  public static void main(String[] args) throws Exception {
    Pubsub client = ServiceAccountConfiguration.createPubsubClient(
        Settings.getSettings().getServiceAccountEmail(),
        Settings.getSettings().getServiceAccountP12KeyPath());
    String topicName = Settings.getSettings().getTopicName();
    String subName = Settings.getSettings().getSubscriptionName();
    Subscription subscription = null;

    try {
      LOG.info("Trying to get subscription " + subName);

      subscription = client
          .projects()
          .subscriptions()
          .get(subName)
          .execute();

      LOG.info("Got subscription back: " + subscription.toPrettyString());
    } catch (HttpResponseException e) {
      throw e;
    }

    PullRequest pullRequest = new PullRequest().setReturnImmediately(false).setMaxMessages(10);

    LOG.info("Will be polling: " + pullRequest.toPrettyString());

    while (true) {
      PullResponse response = null;
      try {
        response = client
            .projects()
            .subscriptions()
            .pull(subName, pullRequest).execute();
      } catch (SocketTimeoutException e) {
        // Something went wrong, try again in a bit
        LOG.info("Timed out waiting for data, repeating in 1 second...");
        Thread.sleep(1000);
        continue;
      }

      ImmutableList.Builder<String> ackIdsBuilder = ImmutableList.builder();
      for (ReceivedMessage msg : response.getReceivedMessages()) {
        PubsubMessage message = msg.getMessage();
        String ackId = message.getMessageId();

        LOG.info("Will be ack'ing " + ackId);
        ackIdsBuilder.add(ackId);
      }

      AcknowledgeRequest ack = new AcknowledgeRequest().setAckIds(ackIdsBuilder.build());
      client
          .projects()
          .subscriptions()
          .acknowledge(subName, ack)
          .execute();

      Thread.sleep(500);
    }
  }
}
