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
 * Loads settings from settings.properties or file specified in DEVELOPER_CONSOLE_SETTINGS env variable.
 */
public class Settings {
  private static final String EMAIL = "ServiceAccountEmail";
  private static final String KEY = "ServiceAccountP12KeyFile";

  private static final String EMAIL_DEFAULT = "changeme@gserviceaccount.com";
  private static final String KEY_DEFAULT = "/path/to/key.p12";

  private static final Logger LOG = Logger.getLogger(Settings.class.getName());
  public static final String SETTINGS_ENV_VAR_NAME = "DEVELOPER_CONSOLE_SETTINGS";

  private static Settings settings = null;

  private String serviceAccountEmail;
  private String serviceAccountP12KeyPath;

  private Settings(String serviceAccountEmail, String serviceAccountP12KeyPath) {
    this.serviceAccountEmail = serviceAccountEmail;
    this.serviceAccountP12KeyPath = serviceAccountP12KeyPath;
  }

  /**
   * @return a singleton instance of Settings
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

    checkNotNull(email, EMAIL + " must be set in " + settingsFilePath);
    checkArgument(!email.equals(EMAIL_DEFAULT), "You must specify non-default " + EMAIL + " in " + settingsFilePath);

    checkNotNull(key, KEY + " must be set in " + settingsFilePath);
    checkArgument(!key.equals(KEY_DEFAULT), "You must specify non-default " + KEY + " in " + settingsFilePath);

    return (settings = new Settings(email, key));
  };

  public String getServiceAccountEmail() {
    return serviceAccountEmail;
  }

  public String getServiceAccountP12KeyPath() {
    return serviceAccountP12KeyPath;
  }
}
