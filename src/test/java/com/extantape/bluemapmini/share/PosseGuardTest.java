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
        assertEquals(ShareAudience.OFF, ShareAudience.resolve("mayor", true, true, "all-online"));
        assertEquals(ShareAudience.OFF, ShareAudience.resolve("mayor-world-group", null, null, null));
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
        assertEquals(ShareAudience.OFF, ShareAudience.resolve(null, null, null, null), "nothing set: off");
        assertEquals(ShareAudience.OFF, ShareAudience.resolve("everyone", null, null, null), "unknown value: off");
        assertEquals(ShareAudience.SERVER, ShareAudience.resolve(" Server ", null, null, null));
    }

    @Test
    void olderConfigsKeepWhatTheyHad() {
        assertEquals(ShareAudience.OFF, ShareAudience.resolve(null, false, true, "world-group"));
        assertEquals(ShareAudience.POSSE_WORLD_GROUP, ShareAudience.resolve(null, true, null, "world-group"), "old defaults: Posse AND world group");
        assertEquals(ShareAudience.POSSE_WORLD_GROUP, ShareAudience.resolve(null, null, null, "world-group"));
        assertEquals(ShareAudience.POSSE, ShareAudience.resolve(null, true, true, "all-online"));
        assertEquals(ShareAudience.WORLD_GROUP, ShareAudience.resolve(null, true, false, "world-group"));
        assertEquals(ShareAudience.WORLD_GROUP, ShareAudience.resolve(null, true, false, "all-online"), "all-online without a filter stayed in the world group");
    }

    @Test
    void migrationNeverOffersMoreThanTheOldSettingsDid() {
        // Old reach: world group always, all-online only through Posse. Check who's offered, kid in kids to adult in survival.
        String[][] old = {{"true", "world-group"}, {"true", "all-online"}, {"false", "world-group"}, {"false", "all-online"}};
        for (String[] o : old) {
            ShareAudience a = ShareAudience.resolve(null, true, Boolean.parseBoolean(o[0]), o[1]);
            boolean crossWorldBefore = o[0].equals("true") && o[1].equals("all-online");
            assertEquals(crossWorldBefore, ShareService.offered(a, "kids", "survival"), String.join(",", o) + " -> " + a.key);
            assertTrue(ShareService.offered(a, "kids", "kids_nether"), "the world group was always offered");
        }
    }

    @Test
    void newSettingWinsOverOldKeys() {
        assertEquals(ShareAudience.WORLD, ShareAudience.resolve("world", true, true, "all-online"));
    }
}
