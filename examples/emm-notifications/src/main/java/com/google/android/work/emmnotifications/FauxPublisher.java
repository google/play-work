package com.google.android.work.emmnotifications;

import com.google.android.work.pubsub.EmmPubsub;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.Topic;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

/**
 * This is a faux publisher which can be used to simulate messages that would be normally sent by
 * Play for Work API
 *
 * To run this sample code:
 * <ol>
 *   <li>Modify settings.properties or specify a different file via DEVELOPER_CONSOLE_SETTINGS
 *   environment variable</li>
 *   <li>Build a deploy jar using <code>mvn clean compile assembly:single</code></li>
 *   <li>Execute it as <code>
 *     java -cp target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *      com.google.android.work.emmnotifications.FauxPublisher</code></li>
 * </ol>

 */
public class FauxPublisher {

  private static final Logger LOG =
      Logger.getLogger(FauxPublisher.class.getName());

  public static void main(String[] args)
      throws IOException, GeneralSecurityException {

    Pubsub pubsubClient = ServiceAccountConfiguration.createPubsubClient(
        Settings.getSettings().getServiceAccountEmail(),
        Settings.getSettings().getServiceAccountP12KeyPath());
    String topicName = Settings.getSettings().getTopicName();

    try {
      Topic topic = pubsubClient
          .projects()
          .topics()
          .get(topicName)
          .execute();

      LOG.info("Topic " + topicName + " exists: " + topic.toPrettyString());
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
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

    PublishRequest publishRequest = new PublishRequest()
        .setMessages(ImmutableList.of(new PubsubMessage()
            .encodeData(mdmPushNotification.toByteArray())));

    LOG.info("Publishing a request: " + publishRequest.toPrettyString());

    pubsubClient
        .projects()
        .topics()
        .publish(topicName, publishRequest)
        .execute();
  }

}
