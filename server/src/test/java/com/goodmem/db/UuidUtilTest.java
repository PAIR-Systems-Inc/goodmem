package com.goodmem.db;

import com.goodmem.db.util.StatusOr;
import com.goodmem.db.util.UuidUtil;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UuidUtil.
 */
public class UuidUtilTest {

    @Test
    void testUuidConversion() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = UuidUtil.toBytes(uuid);
        
        // Check conversion from UUID to bytes and back
        StatusOr<UUID> reconvertedUuid = UuidUtil.fromBytes(bytes);
        assertTrue(reconvertedUuid.isOk());
        assertEquals(uuid, reconvertedUuid.getValue());
        
        // Check conversion from UUID to string and back
        String uuidString = UuidUtil.toString(uuid);
        StatusOr<UUID> fromStringUuid = UuidUtil.fromString(uuidString);
        assertTrue(fromStringUuid.isOk());
        assertEquals(uuid, fromStringUuid.getValue());
        
        // Check conversion from UUID to protobuf ByteString and back
        ByteString byteString = UuidUtil.toProtoBytes(uuid);
        StatusOr<UUID> fromProtoUuid = UuidUtil.fromProtoBytes(byteString);
        assertTrue(fromProtoUuid.isOk());
        assertEquals(uuid, fromProtoUuid.getValue());
    }
    
    @Test
    void testInvalidUuidStringConversion() {
        StatusOr<UUID> result = UuidUtil.fromString("not-a-uuid");
        assertFalse(result.isOk());
        assertEquals("INVALID_ARGUMENT: Invalid UUID string: not-a-uuid", result.getStatus().toString());
    }
    
    @Test
    void testInvalidUuidBytesConversion() {
        // Test with null
        StatusOr<UUID> nullResult = UuidUtil.fromBytes(null);
        assertFalse(nullResult.isOk());
        assertEquals("INVALID_ARGUMENT: Bytes cannot be null", nullResult.getStatus().toString());
        
        // Test with wrong length
        StatusOr<UUID> shortResult = UuidUtil.fromBytes(new byte[10]);
        assertFalse(shortResult.isOk());
        assertEquals("INVALID_ARGUMENT: UUID bytes must be 16 bytes long", shortResult.getStatus().toString());
    }
}