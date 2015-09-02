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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.logging.Logger;

import static com.google.api.client.util.Preconditions.checkArgument;
import static com.google.api.client.util.Preconditions.checkNotNull;

/**
 * Loads settings from settings.properties or the file specified in DEVELOPER_CONSOLE_SETTINGS
 * env variable.
 */
public class Settings {
  private static final String EMAIL = "ServiceAccountEmail";
  private static final String KEY = "ServiceAccountP12KeyFile";
  private static final String TOPIC_NAME = "TopicName";
  private static final String SUBSCRIPTION_NAME = "SubscriptionName";
  private static final String PROJECT_NAME = "ProjectName";
  private static final String PUSH_ENDPOINT = "PushEndpoint";
  
  private static final String PORT = "Port";

  private static final String EMAIL_DEFAULT = "changeme@gserviceaccount.com";
  private static final String KEY_DEFAULT = "/path/to/key.p12";
  private static final String TOPIC_NAME_DEFAULT =
      "projects/sample-project-name/topics/default";
  private static final String SUBSCRIPTION_NAME_DEFAULT =
      "projects/sample-project-name/subscriptions/default";
  private static final String PROJECT_NAME_DEFAULT = "sample-project-name";
  private static final String PUSH_ENDPOINT_DEFAULT = "sample.push.endpoint";

  private static final Logger LOG = Logger.getLogger(Settings.class.getName());
  public static final String SETTINGS_ENV_VAR_NAME = "DEVELOPER_CONSOLE_SETTINGS";

  private static Settings settings = null;

  // See https://cloud.google.com/pubsub/subscriber for more details
  private final String topicName;
  private final String subscriptionName;

  // Email address associated with your service account
  // Obtain this from the "APIs & auth" -> "Credentials"
  // section in the Google Developers Console:
  // https://console.developers.google.com/
  // (and put the e-mail address into your system property obviously)
  private final String serviceAccountEmail;

  // p12 file of your service account
  // Download this file from "APIs & auth" -> "Credentials"
  // section in the Google Developers Console:
  // https://console.developers.google.com/
  private final String serviceAccountP12KeyPath;

  // Name of your developer console project
  private final String projectName;

  // SSL WebHook enabled endpoint, see https://cloud.google.com/pubsub/prereqs#push_endpoints
  private final String pushEndpoint;
  
  // Port on which subscriber will be listening for notifications.
  private final int port;

  private Settings(
      String serviceAccountEmail,
      String serviceAccountP12KeyPath,
      String topicName,
      String subscriptionName,
      String projectName,
      String pushEndpoint,
      int port) {
    this.serviceAccountEmail = serviceAccountEmail;
    this.serviceAccountP12KeyPath = serviceAccountP12KeyPath;
    this.topicName = topicName;
    this.subscriptionName = subscriptionName;
    this.projectName = projectName;
    this.pushEndpoint = pushEndpoint;
    this.port = port;
  }

  /**
   * Returns a singleton instance of Settings
   * @throws IOException
   */
  public static Settings getSettings() throws IOException {
    if (settings != null) {
      return settings;
    }

    String settingsFilePath = "settings.properties";

    String settingsEnvVariable = null;
    if ((settingsEnvVariable = System.getenv(SETTINGS_ENV_VAR_NAME)) != null) {
      settingsFilePath = settingsEnvVariable;
    }

    Properties properties = new Properties();
    properties.load(new InputStreamReader(new FileInputStream(new File(settingsFilePath))));

    String email = properties.getProperty(EMAIL);
    String key = properties.getProperty(KEY);
    String topicName = properties.getProperty(TOPIC_NAME);
    String subscriptionName = properties.getProperty(SUBSCRIPTION_NAME);
    String projectName = properties.getProperty(PROJECT_NAME);
    String pushEndpoint = properties.getProperty(PUSH_ENDPOINT);

    verifyVariable(email, EMAIL, EMAIL_DEFAULT, settingsFilePath);
    verifyVariable(key, KEY, KEY_DEFAULT, settingsFilePath);
    verifyVariable(topicName, TOPIC_NAME, TOPIC_NAME_DEFAULT, settingsFilePath);
    verifyVariable(
        subscriptionName,
        SUBSCRIPTION_NAME,
        SUBSCRIPTION_NAME_DEFAULT,
        settingsFilePath);
    verifyVariable(projectName, PROJECT_NAME, PROJECT_NAME_DEFAULT, settingsFilePath);
    verifyVariable(pushEndpoint, PUSH_ENDPOINT, PUSH_ENDPOINT_DEFAULT, settingsFilePath);
    
    String portString = properties.getProperty(PORT);
    checkNotNull(portString, "Port must be set in %s", settingsFilePath);
    
    int port = 0;
    try {
      port = Integer.parseInt(portString);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Port must be an integer, but was: " + portString, e);
    }
    
    return (settings = new Settings(
        email,
        key,
        topicName,
        subscriptionName,
        projectName,
        pushEndpoint,
        port));
  }

  private static void verifyVariable(
      String variable,
      String key,
      String defaultValue,
      String filePath) {
    checkNotNull(variable, "%s must be set in %s", key, filePath);
    checkArgument(
        !variable.equals(defaultValue),
        "You must specify non-default %s in %s", key, filePath);
  }

  public String getServiceAccountEmail() {
    return serviceAccountEmail;
  }

  public String getServiceAccountP12KeyPath() {
    return serviceAccountP12KeyPath;
  }

  public String getSubscriptionName() {
    return subscriptionName;
  }

  public String getTopicName() {
    return topicName;
  }

  public String getProjectName() {
    return projectName;
  }

  public String getPushEndpoint() {
    return pushEndpoint;
  }

  public int getPort() {
    return port;
  }
}
