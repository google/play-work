package com.google.android.work.emmnotifications.notpublic;

import com.google.android.work.emmnotifications.Common;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.*;
import com.google.common.collect.ImmutableList;
import org.apache.commons.cli.CommandLine;

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
    CommandLine commandLine = Common.getCommandLine(args);
    Pubsub client = Common.makePubsubClient();
    String topicName = commandLine.getOptionValue(Common.TOPIC_NAME, Common.getDefaultTopicName());
    String subName = commandLine.getOptionValue(Common.SUBSCRIPTION_NAME, Common.getDefaultSubscriptionName());
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
