package io.github.didacll.madre.algebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class AlgebraTest {
    @Test void carriersComposeIntrinsically() {
        assertEquals(Sensitivity.S5, Sensitivity.S2.combine(Sensitivity.S5));
        assertEquals(Privacy.P3, Privacy.P5.combine(Privacy.P3));
        assertEquals(Integrity.I2, Integrity.I2.combine(Integrity.I5));
        assertEquals(2, Privacy.UNKNOWN.rank());
    }

    @Test void exactReachabilityExamplesHold() {
        assertTrue(Sensitivity.S2.canReach(Privacy.UNKNOWN));
        assertFalse(Sensitivity.S5.canReach(Privacy.UNKNOWN));
    }
}
