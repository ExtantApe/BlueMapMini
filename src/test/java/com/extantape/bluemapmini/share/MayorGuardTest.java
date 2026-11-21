package com.extantape.bluemapmini.share;

import com.extantape.bluemapmini.config.HudConfig.ShareAudience;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** D254: who coordinates can be shared with, per share.audience, and Mayor's mode failing closed. */
class MayorGuardTest {
    @Test
    void mayorModeIsOffWithoutMayorOrWithOneOlderThan05() {
        assertNotNull(ShareService.mayorGuard(ShareAudience.MAYOR, null), "no Mayor: fail closed");
        assertNotNull(ShareService.mayorGuard(ShareAudience.MAYOR, "0.4.0"), "0.4.x hooks a share event BlueMapMini no longer sends");
        assertNotNull(ShareService.mayorGuard(ShareAudience.MAYOR, "something odd"), "an unreadable version fails closed");
    }

    @Test
    void mayorModeIsOnWithMayor05OrNewer() {
        assertNull(ShareService.mayorGuard(ShareAudience.MAYOR, "0.5.0"));
        assertNull(ShareService.mayorGuard(ShareAudience.MAYOR, "0.7.0-SNAPSHOT"));
        assertNull(ShareService.mayorGuard(ShareAudience.MAYOR, "1.0.0"));
    }

    @Test
    void mayorWorldGroupFailsClosedLikeMayor() {
        assertNotNull(ShareService.mayorGuard(ShareAudience.MAYOR_WORLD_GROUP, null));
        assertNull(ShareService.mayorGuard(ShareAudience.MAYOR_WORLD_GROUP, "0.7.0"));
        assertTrue(ShareService.offered(ShareAudience.MAYOR_WORLD_GROUP, "survival", "survival_nether"));
        assertFalse(ShareService.offered(ShareAudience.MAYOR_WORLD_GROUP, "kids", "survival"));
    }

    @Test
    void anOldMayorTurnsEveryModeOff() {
        for (ShareAudience a : new ShareAudience[] {ShareAudience.WORLD, ShareAudience.WORLD_GROUP, ShareAudience.SERVER}) {
            assertNotNull(ShareService.mayorGuard(a, "0.4.0"), a.key);
        }
    }

    @Test
    void otherModesDoNotNeedMayor() {
        for (ShareAudience a : new ShareAudience[] {ShareAudience.WORLD, ShareAudience.WORLD_GROUP, ShareAudience.SERVER}) {
            assertNull(ShareService.mayorGuard(a, null), a.key);
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
    void serverAndMayorOfferEveryoneAndMayorNarrowsLater() {
        assertTrue(ShareService.offered(ShareAudience.SERVER, "kids", "survival"));
        assertTrue(ShareService.offered(ShareAudience.MAYOR, "kids", "survival"));
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
        assertEquals(ShareAudience.MAYOR_WORLD_GROUP, ShareAudience.resolve(null, true, null, "world-group"), "old defaults: Mayor AND world group");
        assertEquals(ShareAudience.MAYOR_WORLD_GROUP, ShareAudience.resolve(null, null, null, "world-group"));
        assertEquals(ShareAudience.MAYOR, ShareAudience.resolve(null, true, true, "all-online"));
        assertEquals(ShareAudience.WORLD_GROUP, ShareAudience.resolve(null, true, false, "world-group"));
        assertEquals(ShareAudience.WORLD_GROUP, ShareAudience.resolve(null, true, false, "all-online"), "all-online without a filter stayed in the world group");
    }

    @Test
    void migrationNeverOffersMoreThanTheOldSettingsDid() {
        // Old reach: world group always, all-online only through Mayor. Check who's offered, kid in kids to adult in survival.
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
