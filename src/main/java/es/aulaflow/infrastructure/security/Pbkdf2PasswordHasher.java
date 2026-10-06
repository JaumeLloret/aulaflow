package es.aulaflow.infrastructure.security;

import es.aulaflow.application.auth.PasswordHasher;
import es.aulaflow.application.auth.PasswordVerifier;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public final class Pbkdf2PasswordHasher
        implements PasswordHasher {

    static final String ALGORITHM =
            "PBKDF2WithHmacSHA256";

    static final String FORMAT_NAME =
            "pbkdf2-sha256";

    static final String FORMAT_VERSION =
            "v1";

    static final int ITERATIONS =
            600_000;

    static final int SALT_BYTES =
            16;

    static final int DERIVED_KEY_BYTES =
            32;

    private static final String FIELD_SEPARATOR =
            "\\$";

    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODER =
            Base64.getUrlDecoder();

    private final SecureRandom secureRandom;

    public Pbkdf2PasswordHasher() {
        this(new SecureRandom());
    }

    Pbkdf2PasswordHasher(
            SecureRandom secureRandom
    ) {
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "El generador aleatorio no puede ser null."
        );
    }

    @Override
    public PasswordVerifier hash(char[] password) {
        Objects.requireNonNull(
                password,
                "La contraseña no puede ser null."
        );

        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);

        byte[] derivedKey = derive(
                password,
                salt,
                ITERATIONS,
                DERIVED_KEY_BYTES
        );

        try {
            return encode(
                    ITERATIONS,
                    salt,
                    derivedKey
            );
        } finally {
            Arrays.fill(derivedKey, (byte) 0);
            Arrays.fill(salt, (byte) 0);
        }
    }

    @Override
    public boolean verify(
            char[] password,
            PasswordVerifier passwordVerifier
    ) {
        Objects.requireNonNull(
                password,
                "La contraseña no puede ser null."
        );

        Objects.requireNonNull(
                passwordVerifier,
                "El verificador no puede ser null."
        );

        ParsedVerifier parsedVerifier =
                parse(passwordVerifier);

        byte[] candidate = derive(
                password,
                parsedVerifier.salt(),
                parsedVerifier.iterations(),
                parsedVerifier.derivedKey().length
        );

        try {
            return MessageDigest.isEqual(
                    candidate,
                    parsedVerifier.derivedKey()
            );
        } finally {
            Arrays.fill(candidate, (byte) 0);
            parsedVerifier.clear();
        }
    }

    @Override
    public PasswordVerifier createDummyVerifier() {
        byte[] salt = new byte[SALT_BYTES];
        byte[] derivedKey =
                new byte[DERIVED_KEY_BYTES];

        secureRandom.nextBytes(salt);
        secureRandom.nextBytes(derivedKey);

        try {
            return encode(
                    ITERATIONS,
                    salt,
                    derivedKey
            );
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(derivedKey, (byte) 0);
        }
    }

    private static PasswordVerifier encode(
            int iterations,
            byte[] salt,
            byte[] derivedKey
    ) {
        return new PasswordVerifier(
                FORMAT_NAME
                        + "$"
                        + FORMAT_VERSION
                        + "$"
                        + iterations
                        + "$"
                        + ENCODER.encodeToString(salt)
                        + "$"
                        + ENCODER.encodeToString(
                                derivedKey
                        )
        );
    }

    private static ParsedVerifier parse(
            PasswordVerifier passwordVerifier
    ) {
        String[] fields =
                passwordVerifier
                        .encodedValue()
                        .split(
                                FIELD_SEPARATOR,
                                -1
                        );

        if (
                fields.length != 5
                        || !FORMAT_NAME.equals(fields[0])
                        || !FORMAT_VERSION.equals(fields[1])
        ) {
            throw invalidVerifier();
        }

        try {
            int iterations =
                    Integer.parseInt(fields[2]);

            byte[] salt =
                    DECODER.decode(fields[3]);

            byte[] derivedKey =
                    DECODER.decode(fields[4]);

            if (
                    iterations < 1
                            || salt.length != SALT_BYTES
                            || derivedKey.length
                            != DERIVED_KEY_BYTES
            ) {
                Arrays.fill(salt, (byte) 0);
                Arrays.fill(derivedKey, (byte) 0);
                throw invalidVerifier();
            }

            return new ParsedVerifier(
                    iterations,
                    salt,
                    derivedKey
            );
        } catch (
                IllegalArgumentException exception
        ) {
            throw invalidVerifier(exception);
        }
    }

    private static byte[] derive(
            char[] password,
            byte[] salt,
            int iterations,
            int derivedKeyBytes
    ) {
        PBEKeySpec keySpec = new PBEKeySpec(
                password,
                salt,
                iterations,
                derivedKeyBytes * Byte.SIZE
        );

        try {
            SecretKeyFactory keyFactory =
                    SecretKeyFactory.getInstance(
                            ALGORITHM
                    );

            return keyFactory
                    .generateSecret(keySpec)
                    .getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "No se ha podido derivar "
                            + "la contraseña.",
                    exception
            );
        } finally {
            keySpec.clearPassword();
        }
    }

    private static IllegalArgumentException
    invalidVerifier() {
        return new IllegalArgumentException(
                "El formato del verificador "
                        + "de contraseña no es válido."
        );
    }

    private static IllegalArgumentException
    invalidVerifier(Exception cause) {
        return new IllegalArgumentException(
                "El formato del verificador "
                        + "de contraseña no es válido.",
                cause
        );
    }

    private record ParsedVerifier(
            int iterations,
            byte[] salt,
            byte[] derivedKey
    ) {

        private void clear() {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(derivedKey, (byte) 0);
        }
    }
}
