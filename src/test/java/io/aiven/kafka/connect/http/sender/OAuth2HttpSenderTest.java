/*
 * Copyright 2021 Aiven Oy and http-connector-for-apache-kafka project contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.aiven.kafka.connect.http.sender;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.connect.errors.ConnectException;

import io.aiven.kafka.connect.http.config.HttpSinkConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OAuth2HttpSenderTest {

    private static final String CONTENT_TYPE_VALUE = "application/json";

    @Mock
    HttpClient mockedHttpClient;

    final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildAccessTokenAuthHeaderForDefaultSettings(@Mock final HttpResponse<String> accessTokenResponse)
            throws IOException, InterruptedException {
        final var config = defaultConfig();

        final var httpSend =
                new OAuth2HttpSender(
                        new HttpSinkConfig(config),
                        mockedHttpClient
                );

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        final var accessTokenJson = Map.of(
                "access_token", "bla-bla-bla"
        );

        when(accessTokenResponse.statusCode()).thenReturn(200);
        when(accessTokenResponse.body()).thenReturn(objectMapper.writeValueAsString(accessTokenJson));
        when(mockedHttpClient.<String>send(requestCaptor.capture(), any())).thenReturn(accessTokenResponse);

        httpSend.send("SOME_BODY");

        final var r = requestCaptor.getAllValues().get(1);
        assertEquals(
                Optional.of("Bearer bla-bla-bla"),
                r.headers().firstValue(HttpRequestBuilder.HEADER_AUTHORIZATION)
        );
    }

    @Test
    void buildAccessTokenAuthHeaderFromCustomSettings(@Mock final HttpResponse<String> accessTokenResponse)
            throws IOException, InterruptedException {
        final var config = new HashMap<>(defaultConfig());
        config.put("oauth2.client.authorization.mode", "url");
        config.put("oauth2.client.scope", "a,b,c");
        config.put("oauth2.response.token.property", "some_token");

        final var httpSend =
                new OAuth2HttpSender(
                        new HttpSinkConfig(config),
                        mockedHttpClient
                );

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        final var accessTokenJson = Map.of(
                "some_token", "bla-bla-bla-bla",
                "token_type", "Basic"
        );

        when(accessTokenResponse.statusCode()).thenReturn(200);
        when(accessTokenResponse.body()).thenReturn(objectMapper.writeValueAsString(accessTokenJson));
        when(mockedHttpClient.<String>send(requestCaptor.capture(), any())).thenReturn(accessTokenResponse);

        httpSend.send("SOME_BODY");

        final var r = requestCaptor.getAllValues().get(1);
        assertEquals(
                Optional.of("Basic bla-bla-bla-bla"),
                r.headers().firstValue(HttpRequestBuilder.HEADER_AUTHORIZATION)
        );
    }

    @Test
    void buildSpecifiedContentType(@Mock final HttpResponse<String> accessTokenResponse)
            throws IOException, InterruptedException {
        final var config = new HashMap<>(defaultConfig());
        config.put("oauth2.client.authorization.mode", "url");
        config.put("oauth2.client.scope", "a,b,c");
        config.put("oauth2.response.token.property", "some_token");
        config.put("http.headers.content.type", CONTENT_TYPE_VALUE);

        final var httpSend =
                new OAuth2HttpSender(
                        new HttpSinkConfig(config),
                        mockedHttpClient
                );

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        final var accessTokenJson = Map.of(
                "some_token", "bla-bla-bla-bla",
                "token_type", "Basic"
        );

        when(accessTokenResponse.statusCode()).thenReturn(200);
        when(accessTokenResponse.body()).thenReturn(objectMapper.writeValueAsString(accessTokenJson));
        when(mockedHttpClient.<String>send(requestCaptor.capture(), any())).thenReturn(accessTokenResponse);

        httpSend.send("SOME_BODY");

        final var r = requestCaptor.getAllValues().get(1);
        assertTrue(r.headers().firstValue(HttpRequestBuilder.HEADER_CONTENT_TYPE).isPresent());
        assertEquals(CONTENT_TYPE_VALUE, r.headers().firstValue(HttpRequestBuilder.HEADER_CONTENT_TYPE).get());
    }

    @Test
    void reuseAccessToken(@Mock final HttpResponse<String> response) throws Exception {
        final var config = defaultConfig();

        final var httpSend =
                new OAuth2HttpSender(
                        new HttpSinkConfig(config),
                        mockedHttpClient
                );

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        final var accessTokenJson = Map.of(
                "access_token", "bla-bla-bla-bla"
        );

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(objectMapper.writeValueAsString(accessTokenJson));
        when(mockedHttpClient.<String>send(requestCaptor.capture(), any())).thenReturn(response);

        httpSend.send("SOME_BODY");
        verify(mockedHttpClient, times(2)).send(any(HttpRequest.class), any());
        httpSend.send("SOME_BODY");
        verify(mockedHttpClient, times(3)).send(any(HttpRequest.class), any());

    }

    @Test
    void refreshAccessToken(@Mock final HttpResponse<String> response) throws Exception {
        final var config = defaultConfig();

        final var httpSend =
                new OAuth2HttpSender(
                        new HttpSinkConfig(config),
                        mockedHttpClient
                );

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockedHttpClient.<String>send(requestCaptor.capture(), any()))
                .thenAnswer(new Answer<HttpResponse<String>>() {

                    final Map<String, String> accessTokenJson = Map.of(
                            "access_token", "bla-bla-bla-bla"
                    );
                    final Map<String, String> newAccessTokenJson = Map.of(
                            "access_token", "bla-bla-bla-bla-bla"
                    );

                    int accessTokenRequestCounter = 0;

                    int messageRequestCounter = 0;

                    @Override
                    public HttpResponse<String> answer(final InvocationOnMock invocation) throws Throwable {
                        final var request = invocation.<HttpRequest>getArgument(0);
                        if (request.uri().equals(new URI("http://localhost:42/token"))) {
                            if (accessTokenRequestCounter == 1) {
                                when(response.statusCode()).thenReturn(200);
                                when(response.body())
                                        .thenReturn(objectMapper.writeValueAsString(newAccessTokenJson));
                            } else {
                                when(response.statusCode()).thenReturn(200);
                                when(response.body())
                                        .thenReturn(objectMapper.writeValueAsString(accessTokenJson));
                            }
                            accessTokenRequestCounter++;
                        } else {
                            if (messageRequestCounter == 1) {
                                when(response.statusCode()).thenReturn(401);
                                when(response.body()).thenReturn("NOK");
                            } else {
                                when(response.statusCode()).thenReturn(200);
                                when(response.body()).thenReturn("OK");
                            }
                            messageRequestCounter++;
                        }
                        return response;
                    }
                });

        httpSend.send("SOME_BODY_1");
        httpSend.send("SOME_BODY_2");
        httpSend.send("SOME_BODY_3");

        assertEquals(2,
                requestCaptor.getAllValues()
                        .stream()
                        .filter(r -> {
                            try {
                                return r.uri().equals(new URI("http://localhost:42/token"));
                            } catch (final URISyntaxException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .count());
    }

    @Test
    void throwsConnectExceptionForNokToken(@Mock final HttpResponse<String> response)
            throws IOException, InterruptedException {
        final var config = defaultConfig();

        final var httpSend =
                new OAuth2HttpSender(
                        new HttpSinkConfig(config),
                        mockedHttpClient
                );
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("NOK");
        when(mockedHttpClient.<String>send(any(HttpRequest.class), any()))
                .thenReturn(response);

        final var e = assertThrows(ConnectException.class, () -> httpSend.send("SOME_BODY"));

        assertEquals(
                "Sending failed and no retries remain, stopping",
                e.getMessage());

    }

    @Test
    void throwsConnectExceptionOnRefreshToken(@Mock final HttpResponse<String> response)
            throws IOException, InterruptedException {

        final var httpSend =
                new OAuth2HttpSender(
                        new HttpSinkConfig(defaultConfig()),
                        mockedHttpClient
                );


        when(mockedHttpClient.<String>send(any(HttpRequest.class), any()))
                .thenAnswer(new Answer<HttpResponse<String>>() {

                    final Map<String, String> accessTokenJson = Map.of(
                            "access_token", "bla-bla-bla-bla"
                    );

                    int accessTokenRequestCounter = 0;

                    int messageRequestCounter = 0;

                    @Override
                    public HttpResponse<String> answer(final InvocationOnMock invocation) throws Throwable {
                        final var request = invocation.<HttpRequest>getArgument(0);
                        if (request.uri().equals(new URI("http://localhost:42/token"))) {
                            if (accessTokenRequestCounter >= 1) {
                                when(response.statusCode()).thenReturn(400);
                                when(response.body()).thenReturn("NOK");
                            } else {
                                when(response.statusCode()).thenReturn(200);
                                when(response.body())
                                        .thenReturn(objectMapper.writeValueAsString(accessTokenJson));
                            }
                            accessTokenRequestCounter++;
                        } else {
                            if (messageRequestCounter == 1) {
                                when(response.statusCode()).thenReturn(401);
                                when(response.body()).thenReturn("NOK");
                            } else {
                                when(response.statusCode()).thenReturn(200);
                                when(response.body()).thenReturn("OK");
                            }
                            messageRequestCounter++;
                        }
                        return response;
                    }
                });

        final var e = assertThrows(ConnectException.class, () -> {
            httpSend.send("SOME_BODY_1");
            httpSend.send("SOME_BODY_2");
        });

        assertEquals(
                "Sending failed and no retries remain, stopping",
                e.getMessage());

    }


    private Map<String, String> defaultConfig() {
        return Map.of(
                "http.url", "http://localhost:42",
                "http.authorization.type", "oauth2",
                "oauth2.access.token.url", "http://localhost:42/token",
                "oauth2.client.id", "some_client_id",
                "oauth2.client.secret", "some_client_secret"
        );
    }

}
