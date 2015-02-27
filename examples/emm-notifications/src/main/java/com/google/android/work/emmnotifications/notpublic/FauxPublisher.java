package com.google.android.work.emmnotifications.notpublic;

import com.google.android.work.emmnotifications.Common;
import com.google.android.work.pubsub.EmmPubsub;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.Topic;
import com.google.common.collect.ImmutableList;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

/**
 * ---- NOT PUBLIC CODE ----
 *
 * This is a faux publisher which can be used to simulate messages that would be normally sent by
 * Enterprise Server
 */
public class FauxPublisher {

  private static final Logger LOG =
      Logger.getLogger(FauxPublisher.class.getName());

  public static void main(String[] args) throws ParseException, IOException, GeneralSecurityException {

    CommandLine commandLine = Common.getCommandLine(args);
    Pubsub pubsubClient = Common.makePubsubClient();
    String topicName = commandLine.getOptionValue(Common.TOPIC_NAME, Common.getDefaultTopicName());

    try {
      Topic topic = pubsubClient
          .projects()
          .topics()
          .get(topicName)
          .execute();

      LOG.info("Topic " + topicName + " exists: " + topic.toPrettyString());
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == 404) {
        // Topic doesn't exist?
        LOG.info("Topic " + topicName + " doesn't exists, creating it");
        pubsubClient
            .projects()
            .topics()
            .create(topicName, new Topic())
            .execute();
        LOG.info("Topic " + topicName + " created");
      }
    }

    ImmutableList.Builder<PubsubMessage> listBuilder = ImmutableList.builder();

    EmmPubsub.MdmPushNotification mdmPushNotification = EmmPubsub.MdmPushNotification.newBuilder()
        .addProductApprovalEvent(EmmPubsub.ProductApprovalEvent.newBuilder()
            .setApproved(false)
            .setProductId("com.google.android.gms")
            .setCommonEventInformation(EmmPubsub.MdmNotificationEnterpriseEventCommon.newBuilder()
                .setEnterpriseId("12321321")
                .setEventNotificationSentTimestamp("right now")))
        .build();

    pubsubClient
        .projects()
        .topics()
        .publish(
            topicName,
            new PublishRequest()
                .setMessages(ImmutableList.of(
                    new PubsubMessage()
                        .encodeData(mdmPushNotification.toByteArray()))))
        .execute();
  }

}
