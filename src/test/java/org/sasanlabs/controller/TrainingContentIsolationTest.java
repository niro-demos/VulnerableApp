package org.sasanlabs.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TrainingContentIsolationTest {

    @Test
    void executableTrainingContent_ShouldRunInOpaqueSandbox() throws Exception {
        String script;
        try (var stream = getClass().getResourceAsStream("/static/vulnerableApp.js")) {
            assertTrue(stream != null);
            script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(
                script.contains("frame.setAttribute(\"sandbox\", \"allow-scripts allow-forms\")"));
        assertTrue(script.contains("_requiresOpaqueSandbox(vulnerabilitySelected)"));
        assertTrue(script.contains("detailTitle.replaceChildren(frame)"));
    }
}
