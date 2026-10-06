package es.aulaflow.infrastructure.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitialAdministratorConfigTest {

    @Test
    void readsInitialCredentialsWithoutExposingPassword() {
        try (
                InitialAdministratorConfig config =
                        InitialAdministratorConfig.from(
                                Map.of(
                                        "AULAFLOW_ADMIN_USERNAME",
                                        "teacher",
                                        "AULAFLOW_ADMIN_PASSWORD",
                                        "fictional test password"
                                )
                        )
        ) {
            assertEquals(
                    "teacher",
                    config.username()
            );

            assertArrayEquals(
                    "fictional test password"
                            .toCharArray(),
                    config.copyPassword()
            );
        }
    }

    @Test
    void requiresBothVariablesForEmptyInstallation() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> InitialAdministratorConfig
                                .from(
                                        Map.of(
                                                "AULAFLOW_ADMIN_USERNAME",
                                                "teacher"
                                        )
                                )
                );

        assertEquals(
                "Una base sin administrador requiere "
                        + "AULAFLOW_ADMIN_USERNAME y "
                        + "AULAFLOW_ADMIN_PASSWORD.",
                exception.getMessage()
        );
    }

    @Test
    void errorDoesNotContainConfiguredUsername() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> InitialAdministratorConfig
                                .from(
                                        Map.of(
                                                "AULAFLOW_ADMIN_USERNAME",
                                                "private-teacher"
                                        )
                                )
                );

        org.junit.jupiter.api.Assertions.assertFalse(
                exception
                        .getMessage()
                        .contains("private-teacher")
        );
    }
}
