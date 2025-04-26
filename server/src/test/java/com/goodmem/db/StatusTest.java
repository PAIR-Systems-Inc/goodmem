package com.goodmem.db;

import com.goodmem.db.util.Status;
import com.goodmem.db.util.StatusOr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Status and StatusOr classes.
 */
public class StatusTest {

    @Test
    void testStatusCreation() {
        Status ok = Status.ok();
        assertTrue(ok.isOk());
        assertFalse(ok.isError());
        assertEquals(Status.Code.OK, ok.getCode());
        
        Status notFound = Status.notFound("Item not found");
        assertFalse(notFound.isOk());
        assertTrue(notFound.isError());
        assertEquals(Status.Code.NOT_FOUND, notFound.getCode());
        assertEquals("Item not found", notFound.getMessage());
        
        Exception exception = new RuntimeException("Test exception");
        Status internal = Status.internal("Internal error", exception);
        assertFalse(internal.isOk());
        assertTrue(internal.isError());
        assertEquals(Status.Code.INTERNAL, internal.getCode());
        assertEquals("Internal error", internal.getMessage());
        assertEquals(exception, internal.getCause());
    }
    
    @Test
    void testStatusOrWithValue() {
        StatusOr<String> statusOr = StatusOr.ofValue("test");
        assertTrue(statusOr.isOk());
        assertFalse(statusOr.isNotOk());
        assertEquals("test", statusOr.getValue());
        assertTrue(statusOr.getStatus().isOk());
    }
    
    @Test
    void testStatusOrWithError() {
        Status error = Status.invalidArgument("Invalid argument");
        StatusOr<String> statusOr = StatusOr.ofStatus(error);
        assertFalse(statusOr.isOk());
        assertTrue(statusOr.isNotOk());
        assertEquals(error, statusOr.getStatus());
        assertThrows(IllegalStateException.class, statusOr::getValue);
    }
    
    @Test
    void testStatusOrFromException() {
        Exception exception = new RuntimeException("Test exception");
        StatusOr<String> statusOr = StatusOr.ofException(exception);
        assertFalse(statusOr.isOk());
        assertTrue(statusOr.isNotOk());
        assertEquals(Status.Code.INTERNAL, statusOr.getStatus().getCode());
        assertTrue(statusOr.getStatus().getMessage().contains("Test exception"));
        assertEquals(exception, statusOr.getStatus().getCause());
    }
    
    @Test
    void testStatusOrMap() {
        StatusOr<Integer> intStatusOr = StatusOr.ofValue(42);
        StatusOr<String> stringStatusOr = intStatusOr.map(i -> i.toString());
        assertTrue(stringStatusOr.isOk());
        assertEquals("42", stringStatusOr.getValue());
        
        Status error = Status.invalidArgument("Invalid argument");
        StatusOr<Integer> errorStatusOr = StatusOr.ofStatus(error);
        StatusOr<String> mappedErrorStatusOr = errorStatusOr.map(i -> i.toString());
        assertFalse(mappedErrorStatusOr.isOk());
        assertEquals(error, mappedErrorStatusOr.getStatus());
    }
}