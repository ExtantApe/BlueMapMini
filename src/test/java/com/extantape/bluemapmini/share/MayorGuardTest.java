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
        assertEquals(ShareAudience.MAYOR, ShareAudience.resolve(null, true, null, "world-group"), "require-mayor defaulted to true");
        assertEquals(ShareAudience.MAYOR, ShareAudience.resolve(null, true, true, "all-online"));
        assertEquals(ShareAudience.WORLD_GROUP, ShareAudience.resolve(null, true, false, "world-group"));
        assertEquals(ShareAudience.SERVER, ShareAudience.resolve(null, true, false, "all-online"));
    }

    @Test
    void newSettingWinsOverOldKeys() {
        assertEquals(ShareAudience.WORLD, ShareAudience.resolve("world", true, true, "all-online"));
    }
}
