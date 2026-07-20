package org.dempsay.aether.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.Test;

class TestAetherFailure {
    @Test
    void httpStatusMetadata() {
        assertEquals(400, AetherFailure.Validation.httpStatus());
        assertEquals(404, AetherFailure.NotFound.httpStatus());
        assertEquals(409, AetherFailure.Conflict.httpStatus());
        assertEquals(400, AetherFailure.Identity.httpStatus());
        assertEquals(403, AetherFailure.Forbidden.httpStatus());
        assertEquals(500, AetherFailure.Internal.httpStatus());
    }

    @Test
    void failNotifiesListenerAndReturnsFailure() {
        final AtomicReference<Exception> captured = new AtomicReference<>();
        final ExceptionalResponse<String> response = AetherResponses.fail(
                captured::set,
                AetherFailure.NotFound,
                "missing");

        assertTrue(response.wasError());
        assertInstanceOf(AetherException.class, captured.get());
        final AetherException ex = (AetherException) captured.get();
        assertEquals(AetherFailure.NotFound, ex.failure());
        assertEquals("missing", ex.getMessage());
        assertEquals(404, ex.failure().httpStatus());
    }
}
