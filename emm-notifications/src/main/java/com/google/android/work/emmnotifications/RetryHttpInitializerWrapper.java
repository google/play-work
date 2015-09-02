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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.Sleeper;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * RetryHttpInitializerWrapper will automatically retry upon RPC
 * failures, preserving the auto-refresh behavior of the Google
 * Credentials.
 */
public class RetryHttpInitializerWrapper implements HttpRequestInitializer {

  private static final Logger LOG =
      Logger.getLogger(RetryHttpInitializerWrapper.class.getName());

  // Intercepts the request for filling in the "Authorization"
  // header field, as well as recovering from certain unsuccessful
  // error codes wherein the Credential must refresh its token for a
  // retry.
  private final Credential wrappedCredential;

  // A sleeper; you can replace it with a mock in your test.
  private final Sleeper sleeper;

  public RetryHttpInitializerWrapper(Credential wrappedCredential) {
    this(wrappedCredential, Sleeper.DEFAULT);
  }

  // Use only for testing.
  RetryHttpInitializerWrapper(
      Credential wrappedCredential, Sleeper sleeper) {
    this.wrappedCredential = Preconditions.checkNotNull(wrappedCredential);
    this.sleeper = sleeper;
  }

  public void initialize(HttpRequest request) {
    final HttpUnsuccessfulResponseHandler backoffHandler =
        new HttpBackOffUnsuccessfulResponseHandler(
            new ExponentialBackOff())
            .setSleeper(sleeper);
    request.setInterceptor(wrappedCredential);

    request.setConnectTimeout(3 * 60000);  // 3 minutes connect timeout
    request.setReadTimeout(3 * 60000);  // 3 minutes read timeout

    request.setUnsuccessfulResponseHandler(
        new HttpUnsuccessfulResponseHandler() {
          public boolean handleResponse(
              HttpRequest request,
              HttpResponse response,
              boolean supportsRetry) throws IOException {

            LOG.info("RetryHandler: " + CharStreams.toString(
                new InputStreamReader(response.getContent())));

            if (wrappedCredential.handleResponse(
                request, response, supportsRetry)) {
              // If credential decides it can handle it,
              // the return code or message indicated
              // something specific to authentication,
              // and no backoff is desired.

              LOG.info("Requested: " + request.getUrl().toString());
              return true;
            } else if (backoffHandler.handleResponse(
                request, response, supportsRetry)) {

              // Otherwise, we defer to the judgement of
              // our internal backoff handler.
              LOG.info("Retrying " + request.getUrl());
              return true;
            } else {
              return false;
            }
          }
        });
    request.setIOExceptionHandler(
        new HttpBackOffIOExceptionHandler(new ExponentialBackOff())
            .setSleeper(sleeper));
  }
}