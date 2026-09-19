package peershare.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PairAndResultTest {

    // --- Pair<A, B> -----------------------------------------------------

    @Test
    void pairStoresAndReturnsBothValuesWithDifferentTypes() {
        Pair<String, Integer> pair = Pair.of("size", 54);

        assertEquals("size", pair.first());
        assertEquals(54, pair.second());
    }

    @Test
    void pairsWithEqualContentsAreEqual() {
        Pair<String, Integer> a = Pair.of("x", 1);
        Pair<String, Integer> b = Pair.of("x", 1);
        Pair<String, Integer> c = Pair.of("x", 2);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void pairToStringIsHumanReadable() {
        Pair<String, Integer> pair = Pair.of("a", 1);
        assertEquals("(a, 1)", pair.toString());
    }

    @Test
    void pairAllowsNullValues() {
        Pair<String, String> pair = Pair.of(null, "b");
        assertNull(pair.first());
        assertEquals("b", pair.second());
    }

    // --- Result<T> --------------------------------------------------------

    @Test
    void okResultIsOkAndCarriesValue() {
        Result<String> result = Result.ok("done");

        assertTrue(result.isOk());
        assertEquals("done", result.getValue());
        assertNull(result.getErrorMessage());
    }

    @Test
    void failResultIsNotOkAndCarriesMessage() {
        Result<String> result = Result.fail("network error");

        assertFalse(result.isOk());
        assertNull(result.getValue());
        assertEquals("network error", result.getErrorMessage());
    }

    @Test
    void handleInvokesOnSuccessForOkResult() {
        Result<Integer> result = Result.ok(42);
        AtomicReference<Integer> received = new AtomicReference<>();
        AtomicBoolean failureCalled = new AtomicBoolean(false);

        result.handle(received::set, err -> failureCalled.set(true));

        assertEquals(42, received.get());
        assertFalse(failureCalled.get());
    }

    @Test
    void handleInvokesOnFailureForFailResult() {
        Result<Integer> result = Result.fail("boom");
        AtomicReference<String> receivedError = new AtomicReference<>();
        AtomicBoolean successCalled = new AtomicBoolean(false);

        result.handle(v -> successCalled.set(true), receivedError::set);

        assertEquals("boom", receivedError.get());
        assertFalse(successCalled.get());
    }
}
