package es.aulaflow.shared.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationConfigTest {

    @Test
    void usesDefaultValuesWhenVariablesAreMissing() {
        ApplicationConfig config =
                ApplicationConfig.from(Map.of());

        assertEquals(
                "127.0.0.1",
                config.getHttpHost()
        );

        assertEquals(
                8080,
                config.getHttpPort()
        );

        assertEquals(
                "development",
                config.getEnvironment()
        );
    }

    @Test
    void readsConfiguredEnvironmentVariables() {
        Map<String, String> environmentVariables = Map.of(
                "AULAFLOW_HTTP_HOST", "0.0.0.0",
                "AULAFLOW_HTTP_PORT", "9090",
                "AULAFLOW_ENV", "test"
        );

        ApplicationConfig config =
                ApplicationConfig.from(environmentVariables);

        assertEquals(
                "0.0.0.0",
                config.getHttpHost()
        );

        assertEquals(
                9090,
                config.getHttpPort()
        );

        assertEquals(
                "test",
                config.getEnvironment()
        );
    }

    @Test
    void rejectsPortThatIsNotANumber() {
        Map<String, String> environmentVariables = Map.of(
                "AULAFLOW_HTTP_PORT",
                "hola"
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ApplicationConfig.from(
                                environmentVariables
                        )
                );

        assertTrue(
                exception.getMessage().contains(
                        "AULAFLOW_HTTP_PORT"
                )
        );
    }

    @Test
    void rejectsPortBelowAllowedRange() {
        Map<String, String> environmentVariables = Map.of(
                "AULAFLOW_HTTP_PORT",
                "0"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationConfig.from(
                        environmentVariables
                )
        );
    }

    @Test
    void rejectsPortAboveAllowedRange() {
        Map<String, String> environmentVariables = Map.of(
                "AULAFLOW_HTTP_PORT",
                "65536"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationConfig.from(
                        environmentVariables
                )
        );
    }
}
