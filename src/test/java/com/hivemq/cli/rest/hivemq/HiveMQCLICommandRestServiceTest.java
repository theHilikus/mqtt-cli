/*
 * Copyright 2019-present HiveMQ and the HiveMQ Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hivemq.cli.rest.hivemq;

import com.hivemq.cli.openapi.ApiException;
import com.hivemq.cli.openapi.hivemq.Client;
import com.hivemq.cli.openapi.hivemq.ClientList;
import com.hivemq.cli.rest.HiveMQRestService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_CURSOR_NOT_VALID_ANYMORE;
import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_EMPTY;
import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_INVALID_CURSOR;
import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_REPLICATION;
import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_SINGLE_RESULT;
import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_WITH_CURSOR;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_GONE;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveMQCLICommandRestServiceTest {

    private HiveMQRestService hivemqRestService;
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        hivemqRestService = new HiveMQRestService(server.url("/").toString(), 500);

    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void get_client_ids_success() throws ApiException {
        final MockResponse response = new MockResponse()
                .setResponseCode(HTTP_OK)
                .setBody(CLIENT_IDS_WITH_CURSOR);

        server.enqueue(response);

        final ClientList clientList = hivemqRestService.getClientIds(null);
        final List<Client> clients = clientList.getItems();

        assertNotNull(clients);
        for (int i = 1; i < 11; i++) {
            String expected = "client-" + i ;
            assertEquals(expected, clients.get(i - 1).getId());
        }

        assertNotNull(clientList.getLinks());
        assertNotNull(clientList.getLinks().getNext());
    }

    @Test
    void get_client_ids_single_success() throws ApiException {
        final MockResponse response = new MockResponse()
                .setResponseCode(HTTP_OK)
                .setBody(CLIENT_IDS_SINGLE_RESULT);

        server.enqueue(response);

        final ClientList clientList = hivemqRestService.getClientIds(null);
        final List<Client> clients = clientList.getItems();

        assertNotNull(clients);
        assertEquals(1, clients.size());
    }

    @Test
    void get_client_ids_empty_success() throws ApiException {
        final MockResponse response = new MockResponse()
                .setResponseCode(HTTP_OK)
                .setBody(CLIENT_IDS_EMPTY);

        server.enqueue(response);

        final ClientList clientList = hivemqRestService.getClientIds(null);
        final List<Client> clients = clientList.getItems();

        assertNotNull(clients);
        assertEquals(0, clients.size());
    }

    @Test
    void get_client_ids_invalid_cursor_failed() {
        final MockResponse response = new MockResponse()
                .setResponseCode(HTTP_BAD_REQUEST)
                .setBody(CLIENT_IDS_INVALID_CURSOR);

        server.enqueue(response);


        assertThrows(ApiException.class, () -> hivemqRestService.getClientIds(null));
    }


    @Test
    void get_client_ids_cursor_not_Valid_anymore_failed() {
        final MockResponse response = new MockResponse()
                .setResponseCode(HTTP_GONE)
                .setBody(CLIENT_IDS_CURSOR_NOT_VALID_ANYMORE);

        server.enqueue(response);


        assertThrows(ApiException.class, () -> hivemqRestService.getClientIds(null));
    }

    @Test
    void get_client_ids_during_replication_failed() {
        final MockResponse response = new MockResponse()
                .setResponseCode(HTTP_UNAVAILABLE)
                .setBody(CLIENT_IDS_REPLICATION);

        server.enqueue(response);


        assertThrows(ApiException.class, () -> hivemqRestService.getClientIds(null));
    }

    @Test
    void get_client_ids_rate_limit_5_success() throws ApiException {
        hivemqRestService = new HiveMQRestService(server.url("/").toString(), 1);

        final MockResponse response = new MockResponse()
                .setResponseCode(HTTP_OK)
                .setBody(CLIENT_IDS_WITH_CURSOR);

        for (int i = 0; i < 5; i++) {
            server.enqueue(response);
        }

        final long startTime = System.nanoTime();

        for (int i = 0; i < 5; i++) {
            hivemqRestService.getClientIds(null);

        }

        final long stopTime = System.nanoTime();

        assertTrue(stopTime > startTime + 4_000_000);

    }


}