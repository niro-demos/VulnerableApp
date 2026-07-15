package org.sasanlabs.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class PublicDeploymentSecurityTest {

    @Test
    void publicApplicationConfigurationRemainsAvailable() throws IOException {
        Properties application = loadProperties("application.properties");
        Properties publicProfile = loadProperties("application-public.properties");

        assertEquals("9090", application.getProperty("server.port"));
        assertEquals("/VulnerableApp", application.getProperty("server.servlet.context-path"));
        assertEquals("false", publicProfile.getProperty("spring.h2.console.enabled"));
    }

    @Test
    void publicDeploymentDoesNotExposeH2ConsoleOrReadOnlyCredentials() throws IOException {
        Properties application = loadProperties("application.properties");
        String messages = loadResource("i18n/messages.properties");
        String authenticationSchema = loadResource("scripts/Authentication/db/schema.sql");

        assertEquals("public", application.getProperty("spring.profiles.active"));
        assertFalse(messages.contains("/h2/"));
        assertFalse(messages.contains("jdbc:h2:mem:testdb"));
        assertFalse(messages.contains("readonly_user"));
        assertFalse(messages.contains("readonly_password"));
        assertFalse(authenticationSchema.contains("CREATE USER IF NOT EXISTS readonly_user"));
    }

    private static Properties loadProperties(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = resource(resource)) {
            properties.load(input);
        }
        return properties;
    }

    private static String loadResource(String resource) throws IOException {
        try (InputStream input = resource(resource)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream resource(String name) {
        InputStream input =
                PublicDeploymentSecurityTest.class.getClassLoader().getResourceAsStream(name);
        if (input == null) {
            throw new IllegalArgumentException("Missing test resource: " + name);
        }
        return input;
    }
}
