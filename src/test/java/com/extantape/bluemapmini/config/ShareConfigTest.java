package com.extantape.bluemapmini.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** D254: the shipped default is off, and an older config file keeps its old sharing even with the new defaults loaded. */
class ShareConfigTest {
    private static YamlConfiguration withJarDefaults(String yaml) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        try (var in = ShareConfigTest.class.getResourceAsStream("/config.yml")) {
            c.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        return c;
    }

    @Test
    void shippedDefaultIsOff() throws Exception {
        assertEquals(HudConfig.ShareAudience.OFF, HudConfig.from(withJarDefaults("")).share().audience());
    }

    @Test
    void olderFileWithRequirePosseBecomesPosseWithinTheWorldGroup() throws Exception {
        var c = withJarDefaults("share:\n  enabled: true\n  candidates: world-group\n  require-mayor: true\n");
        assertEquals(HudConfig.ShareAudience.POSSE_WORLD_GROUP, HudConfig.from(c).share().audience());
    }

    @Test
    void explicitAudienceIsRead() throws Exception {
        assertEquals(HudConfig.ShareAudience.SERVER, HudConfig.from(withJarDefaults("share:\n  audience: server\n")).share().audience());
        var typo = HudConfig.from(withJarDefaults("share:\n  audience: everybody\n")).share();
        assertEquals(HudConfig.ShareAudience.OFF, typo.audience());
        assertTrue(typo.audienceUnknown());
    }

    @Test
    void bareOffIsOffWithoutAWarning() throws Exception {
        // YAML 1.1 reads a bare off as false; the plugin must still treat it as the off mode, not as a typo.
        for (String v : new String[] {"off", "\"off\"", "OFF", "false"}) {
            var share = HudConfig.from(withJarDefaults("share:\n  audience: " + v + "\n")).share();
            assertEquals(HudConfig.ShareAudience.OFF, share.audience(), v);
            assertFalse(share.audienceUnknown(), v);
        }
        var on = HudConfig.from(withJarDefaults("share:\n  audience: on\n")).share();
        assertEquals(HudConfig.ShareAudience.OFF, on.audience(), "on isn't a mode, so it fails closed");
        assertTrue(on.audienceUnknown());
    }

    @Test
    void serverWithOldDefaultsAndAllOnlineBecomesPosse() throws Exception {
        // A live server's 0.3.0 file: enabled, all-online, require-mayor left at its old default (true).
        var c = withJarDefaults("share:\n  enabled: true\n  candidates: all-online\n  cooldown-seconds: 30\n  log: true\n");
        assertEquals(HudConfig.ShareAudience.POSSE, HudConfig.from(c).share().audience());
    }
}
