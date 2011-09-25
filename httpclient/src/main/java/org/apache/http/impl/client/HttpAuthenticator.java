/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.client;

import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthChallengeState;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.client.AuthenticationHandler;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.protocol.HttpContext;

public class HttpAuthenticator {

    private final Log log;

    public HttpAuthenticator(final Log log) {
        super();
        this.log = log != null ? log : LogFactory.getLog(getClass());
    }

    public HttpAuthenticator() {
        this(null);
    }

    public boolean isAuthenticationRequested(
            final HttpResponse response,
            final AuthenticationHandler authHandler,
            final AuthState authState,
            final HttpContext context) {
        if (authHandler.isAuthenticationRequested(response, context)) {
            return true;
        } else {
            if (authState.getChallengeState() == AuthChallengeState.CHALLENGED) {
                authState.setChallengeState(AuthChallengeState.SUCCESS);
            } else {
                authState.setChallengeState(AuthChallengeState.UNCHALLENGED);
            }
            return false;
        }
    }

    public boolean authenticate(
            final HttpHost host,
            final HttpResponse response,
            final AuthenticationHandler authHandler,
            final AuthState authState,
            final CredentialsProvider credsProvider,
            final HttpContext context) {
        try {
            if (this.log.isDebugEnabled()) {
                this.log.debug(host.toHostString() + " requested authentication");
            }
            Map<String, Header> challenges = authHandler.getChallenges(response, context);
            if (challenges.isEmpty()) {
                this.log.debug("Response contains no authentication challenges");
                return false;
            }
            AuthScheme authScheme = authState.getAuthScheme();
            if (authScheme == null) {
                // Authentication not attempted before
                authScheme = authHandler.selectScheme(challenges, response, context);
                authState.setAuthScheme(authScheme);
            }
            String id = authScheme.getSchemeName();
            Header challenge = challenges.get(id.toLowerCase(Locale.US));
            if (challenge == null) {
                // Retry authentication with a different scheme
                authState.invalidate();
                authScheme = authHandler.selectScheme(challenges, response, context);
                authState.setAuthScheme(authScheme);
                id = authScheme.getSchemeName();
                challenge = challenges.get(id.toLowerCase(Locale.US));
            }
            authState.setChallengeState(AuthChallengeState.CHALLENGED);
            authScheme.processChallenge(challenge);
            this.log.debug("Authorization challenge processed");

            AuthScope authScope = new AuthScope(
                    host.getHostName(),
                    host.getPort(),
                    authScheme.getRealm(),
                    authScheme.getSchemeName());

            if (this.log.isDebugEnabled()) {
                this.log.debug("Authentication scope: " + authScope);
            }
            Credentials creds = authState.getCredentials();
            if (creds == null) {
                creds = credsProvider.getCredentials(authScope);
                if (this.log.isDebugEnabled()) {
                    if (creds != null) {
                        this.log.debug("Found credentials");
                    } else {
                        this.log.debug("Credentials not found");
                    }
                }
            } else {
                if (authScheme.isComplete()) {
                    this.log.debug("Authentication failed");
                    authState.setChallengeState(AuthChallengeState.FAILURE);
                    creds = null;
                }
            }
            authState.setCredentials(creds);
            return creds != null;
        } catch (MalformedChallengeException ex) {
            if (this.log.isWarnEnabled()) {
                this.log.warn("Malformed challenge: " +  ex.getMessage());
            }
            authState.invalidate();
            return false;
        } catch (AuthenticationException ex) {
            if (this.log.isWarnEnabled()) {
                this.log.warn("Authentication error: " +  ex.getMessage());
            }
            authState.invalidate();
            return false;
        }
    }

}
