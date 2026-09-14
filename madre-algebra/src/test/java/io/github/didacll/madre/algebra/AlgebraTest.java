package io.github.didacll.madre.algebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class AlgebraTest {
    @Test void everyCarrierHasSystemReservedRankZero() {
        assertEquals(0, Sensitivity.SYSTEM_RESERVED.rank());
        assertEquals(0, Privacy.SYSTEM_RESERVED.rank());
        assertEquals(0, Integrity.SYSTEM_RESERVED.rank());
        assertEquals(0, Risk.SYSTEM_RESERVED.rank());
        assertEquals(0, Autonomy.SYSTEM_RESERVED.rank());
    }

    @Test void privacyRanksHaveCanonicalMeaning() {
        assertEquals(1, Privacy.PUBLIC.rank());
        assertEquals(2, Privacy.UNKNOWN.rank());
        assertEquals(3, Privacy.LOCAL.rank());
        assertEquals(4, Privacy.MODULE.rank());
        assertEquals(5, Privacy.SECRET.rank());
    }

    @Test void carriersComposeIntrinsically() {
        assertEquals(Sensitivity.S5, Sensitivity.S2.combine(Sensitivity.S5));
        assertEquals(Privacy.LOCAL, Privacy.SECRET.combine(Privacy.LOCAL));
        assertEquals(Integrity.I2, Integrity.I2.combine(Integrity.I5));
    }

    @Test void exactReachabilityExamplesHold() {
        assertTrue(Sensitivity.S2.canReach(Privacy.UNKNOWN));
        assertTrue(Sensitivity.S3.canReach(Privacy.LOCAL));
        assertTrue(Sensitivity.S4.canReach(Privacy.MODULE));
        assertTrue(Sensitivity.S5.canReach(Privacy.SECRET));
        assertFalse(Sensitivity.S5.canReach(Privacy.UNKNOWN));
    }
}
