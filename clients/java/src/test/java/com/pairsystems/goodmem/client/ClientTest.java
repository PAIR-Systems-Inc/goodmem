package com.pairsystems.goodmem.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class ClientTest {
    @Test
    void testClientCreation() {
        Client client = new Client("http://localhost:8080");
        assertNotNull(client);
    }
}
