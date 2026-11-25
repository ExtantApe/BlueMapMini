package com.extantape.bluemapmini;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Posse already loads after BlueMapMini, so it can see the share event class. If BlueMapMini also
 * asked to load after Posse, Paper finds a loop and refuses the order (caught in a server rehearsal,
 * 2026-11-25). BlueMapMini finds Posse when someone shares, so it needs no load order of its own.
 */
class LoadOrderTest {
    @Test
    void bluemapminiDoesNotAskToLoadAfterPosse() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/paper-plugin.yml"));
        assertFalse(yml.matches("(?s).*\\n\\s+Posse:\\s*\\n.*"), "a Posse entry in dependencies makes a load-order loop with Posse");
    }
}
