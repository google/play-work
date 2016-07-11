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
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.Topic;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.logging.Logger;

/**
 * This is a test publisher which can be used to simulate messages that would be normally sent by
 * Play for Work API
 *
 * To run this sample code:
 * <ol>
 *   <li>Modify settings.properties or specify a different file via the DEVELOPER_CONSOLE_SETTINGS
 *   environment variable</li>
 *   <li>Build a deploy jar using <code>mvn clean compile assembly:single</code></li>
 *   <li>Execute it as <code>
 *     java -cp target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *      com.google.android.work.emmnotifications.TestPublisher</code></li>
 * </ol>
 */
public class TestPublisher {

  private static final Logger LOG =
      Logger.getLogger(TestPublisher.class.getName());

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

      LOG.info("The topic " + topicName + " exists: " + topic.toPrettyString());
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        // The topic doesn't exist
        LOG.info("The topic " + topicName + " doesn't exist, creating it");
        
        // TODO(kirillov): add explicit error handling here
        pubsubClient
            .projects()
            .topics()
            .create(topicName, new Topic())
            .execute();
        LOG.info("The topic " + topicName + " created");
      }
    }

    ImmutableList.Builder<PubsubMessage> listBuilder = ImmutableList.builder();

    EmmPubsub.MdmPushNotification mdmPushNotification = EmmPubsub.MdmPushNotification.newBuilder()
        .setEnterpriseId("12321321")
        .setEventNotificationSentTimestampMillis(System.currentTimeMillis())
        .addProductApprovalEvent(EmmPubsub.ProductApprovalEvent.newBuilder()
            .setApproved(EmmPubsub.ProductApprovalEvent.ApprovalStatus.UNAPPROVED)
            .setProductId("app:com.android.chrome"))
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
