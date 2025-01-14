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
package com.hivemq.cli.commands.hivemq.export.clients;

import com.hivemq.cli.commands.hivemq.export.clients.ClientIdsRetrieverTask;
import com.hivemq.cli.openapi.ApiException;
import com.hivemq.cli.rest.HiveMQRestService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.hivemq.cli.rest.ClientsApiResponses.INVALID_CURSOR_VALUE;
import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_INVALID_CURSOR;
import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_SINGLE_RESULT;
import static com.hivemq.cli.rest.hivemq.TestResponseBodies.CLIENT_IDS_WITH_CURSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class ClientIdsRetrieverTaskTest {

    HiveMQRestService hiveMQRestService;
    MockWebServer server;
    BlockingQueue<String> clientIdsQueue;
    ClientIdsRetrieverTask clientIdsRetrieverTask;


    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        clientIdsQueue = new LinkedBlockingQueue<>();
        hiveMQRestService = new HiveMQRestService(server.url("/").toString(), 500);

        clientIdsRetrieverTask = new ClientIdsRetrieverTask(hiveMQRestService, clientIdsQueue);
    }

    @Test
    void single_page_success() throws ApiException, InterruptedException {
        final MockResponse response = new MockResponse()
                .setResponseCode(200)
                .setBody(CLIENT_IDS_SINGLE_RESULT);

        server.enqueue(response);

        clientIdsRetrieverTask.run();

        assertEquals(1, clientIdsQueue.size());
        assertEquals("client-ݰ", clientIdsQueue.poll());
    }

    @Test
    void more_pages_success() throws ApiException, InterruptedException {
        final MockResponse response = new MockResponse()
                .setResponseCode(200)
                .setBody(CLIENT_IDS_WITH_CURSOR);

        server.enqueue(response);
        server.enqueue(response);
        server.enqueue(response);
        server.enqueue(response);
        server.enqueue(response);

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(CLIENT_IDS_SINGLE_RESULT));

        clientIdsRetrieverTask.run();

        assertEquals(51, clientIdsQueue.size());
    }

    @Test
    void unrecoverable_exception_success() {
        final MockResponse response = new MockResponse()
                .setResponseCode(INVALID_CURSOR_VALUE)
                .setBody(CLIENT_IDS_INVALID_CURSOR);

        server.enqueue(response);

        assertThrows(CompletionException.class, () -> clientIdsRetrieverTask.run());
        assertEquals(0, clientIdsQueue.size());
    }

    @Test
    void blocking_success() {
        clientIdsQueue = new LinkedBlockingQueue<>(1);
        clientIdsRetrieverTask = new ClientIdsRetrieverTask(hiveMQRestService, clientIdsQueue);

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(CLIENT_IDS_WITH_CURSOR));

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(CLIENT_IDS_SINGLE_RESULT));


        final CompletableFuture<Void> clientIdsRetrieverFuture = CompletableFuture.runAsync(clientIdsRetrieverTask);

        final AtomicLong polledClientIds = new AtomicLong();
        final CompletableFuture<Void> clientIdsConsumerFuture = CompletableFuture.runAsync(() -> {
            try {

                while (!clientIdsRetrieverFuture.isDone() || !clientIdsQueue.isEmpty()) {
                    final String clientId = clientIdsQueue.poll(10, TimeUnit.MILLISECONDS);
                    if (clientId != null) {
                        polledClientIds.incrementAndGet();
                    }
                }
            } catch (final Exception ex) {
                throw new CompletionException(ex);
            }
        });

        clientIdsRetrieverFuture.join();
        clientIdsConsumerFuture.join();

        assertEquals(0, clientIdsQueue.size());
        assertEquals(11, polledClientIds.get());
    }

}