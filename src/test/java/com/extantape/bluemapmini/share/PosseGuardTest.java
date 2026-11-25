package com.extantape.bluemapmini.share;

import com.extantape.bluemapmini.config.HudConfig.ShareAudience;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** D254: who coordinates can be shared with, per share.audience, and Posse's mode failing closed. */
class PosseGuardTest {
    @Test
    void posseModeIsOffWithoutPosseOrWithOneOlderThan011() {
        assertNotNull(ShareService.posseGuard(ShareAudience.POSSE, null), "no Posse: fail closed");
        assertNotNull(ShareService.posseGuard(ShareAudience.POSSE, "0.4.0"), "0.4.x hooks a share event BlueMapMini no longer sends");
        assertNotNull(ShareService.posseGuard(ShareAudience.POSSE, "something odd"), "an unreadable version fails closed");
    }

    @Test
    void theOldMayorNameIsNotAnAudience() {
        // D196: the old values are unknown, and an unknown value is off, so sharing stays off until the config is edited.
        assertNull(ShareAudience.of("mayor"));
        assertNull(ShareAudience.of("mayor-world-group"));
        assertEquals(ShareAudience.OFF, ShareAudience.resolve("mayor"));
        assertEquals(ShareAudience.OFF, ShareAudience.resolve("mayor-world-group"));
        assertEquals(ShareAudience.POSSE, ShareAudience.of("posse"));
        assertEquals(ShareAudience.POSSE_WORLD_GROUP, ShareAudience.of("posse-world-group"));
    }

    @Test
    void aPosseOlderThanItsFirstReleaseUnderThatNameStaysOff() {
        assertNotNull(ShareService.posseGuard(ShareAudience.POSSE, "0.10.0"));
    }

    @Test
    void posseModeIsOnWithPosse011OrNewer() {
        assertNull(ShareService.posseGuard(ShareAudience.POSSE, "0.11.0"));
        assertNull(ShareService.posseGuard(ShareAudience.POSSE, "0.12.0-SNAPSHOT"));
        assertNull(ShareService.posseGuard(ShareAudience.POSSE, "1.0.0"));
    }

    @Test
    void posseWorldGroupFailsClosedLikePosse() {
        assertNotNull(ShareService.posseGuard(ShareAudience.POSSE_WORLD_GROUP, null));
        assertNull(ShareService.posseGuard(ShareAudience.POSSE_WORLD_GROUP, "0.11.0"));
        assertTrue(ShareService.offered(ShareAudience.POSSE_WORLD_GROUP, "survival", "survival_nether"));
        assertFalse(ShareService.offered(ShareAudience.POSSE_WORLD_GROUP, "kids", "survival"));
    }

    @Test
    void anOldPosseTurnsEveryModeOff() {
        for (ShareAudience a : new ShareAudience[] {ShareAudience.WORLD, ShareAudience.WORLD_GROUP, ShareAudience.SERVER}) {
            assertNotNull(ShareService.posseGuard(a, "0.4.0"), a.key);
        }
    }

    @Test
    void otherModesDoNotNeedPosse() {
        for (ShareAudience a : new ShareAudience[] {ShareAudience.WORLD, ShareAudience.WORLD_GROUP, ShareAudience.SERVER}) {
            assertNull(ShareService.posseGuard(a, null), a.key);
        }
    }

    @Test
    void offOffersNobody() {
        assertFalse(ShareService.offered(ShareAudience.OFF, "survival", "survival"));
    }

    @Test
    void worldOffersOnlyTheSameWorld() {
        assertTrue(ShareService.offered(ShareAudience.WORLD, "survival", "survival"));
        assertFalse(ShareService.offered(ShareAudience.WORLD, "survival", "survival_nether"));
        assertFalse(ShareService.offered(ShareAudience.WORLD, "kids", "survival"));
    }

    @Test
    void worldGroupCountsNetherAndEnd() {
        assertTrue(ShareService.offered(ShareAudience.WORLD_GROUP, "survival", "survival_nether"));
        assertTrue(ShareService.offered(ShareAudience.WORLD_GROUP, "survival_the_end", "survival"));
        assertFalse(ShareService.offered(ShareAudience.WORLD_GROUP, "kids", "survival"));
    }

    @Test
    void serverAndPosseOfferEveryoneAndPosseNarrowsLater() {
        assertTrue(ShareService.offered(ShareAudience.SERVER, "kids", "survival"));
        assertTrue(ShareService.offered(ShareAudience.POSSE, "kids", "survival"));
    }

    @Test
    void defaultsAndTyposAreOff() {
        assertEquals(ShareAudience.OFF, ShareAudience.resolve(null), "nothing set: off");
        assertEquals(ShareAudience.OFF, ShareAudience.resolve("everyone"), "unknown value: off");
        assertEquals(ShareAudience.SERVER, ShareAudience.resolve(" Server "));
    }



}
