package com.google.android.work.emmnotifications;

import com.google.api.services.pubsub.Pubsub;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

public class Common {
  private static final Logger LOG = Logger.getLogger(Common.class.getName());

  public static final String TOPIC_NAME = "topic_name";
  public static final String SUBSCRIPTION_NAME = "subscription_name";
  public static final String PROJECT_NAME = "project_name";

  private static final String DEFAULT_PROJECT_NAME = "enterprise-cloud-pub-sub";

  public static CommandLine getCommandLine(String[] args) throws ParseException {
    Options options = new Options();

    options.addOption("t", TOPIC_NAME, true, "Topic name");
    options.addOption("s", SUBSCRIPTION_NAME, true, "Subscription name");
    options.addOption("p", PROJECT_NAME, true, "Developer Console project name, e.g. cloud-pub-sub");

    CommandLineParser parser = new GnuParser();
    CommandLine commandLine = parser.parse(options, args);

    // If project name is specified we will use default topic and subscription name
    // If non-default topic and/or subscription name are specified, then project name makes no sense.
    if (commandLine.hasOption(PROJECT_NAME) &&
        (commandLine.hasOption(TOPIC_NAME) || commandLine.hasOption(SUBSCRIPTION_NAME))) {
      LOG.warning("Either --" + PROJECT_NAME
          + " or combination of --" + TOPIC_NAME
          + " and --" + SUBSCRIPTION_NAME
          + " can be set");

      System.exit(2);
    }

    return commandLine;
  }

  public static Pubsub makePubsubClient() throws IOException, GeneralSecurityException {
    return ServiceAccountConfiguration.createPubsubClient(
        Settings.getSettings().getServiceAccountEmail(),
        Settings.getSettings().getServiceAccountP12KeyPath());
  }

  public static String getDefaultProjectName() {
    return DEFAULT_PROJECT_NAME;
  }

  public static String getDefaultTopicName() {
    return "projects/" + getDefaultProjectName() + "/topics/hello-world-topic";
  }

  public static String getDefaultSubscriptionName() {
    return "projects/" + getDefaultProjectName() + "/subscriptions/hello-world-sub";
  }
}
