package es.aulaflow.presentation.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Trocedador {@code multipart/form-data} mínimo: solo lo necesario
 * para un campo de archivo y un campo CSRF. No usa
 * {@code String.split(boundary)} sobre el cuerpo binario, no escribe
 * en disco y no confía en el nombre de archivo declarado por el
 * cliente.
 */
public final class MultipartFormDataParser {

    private static final int MAX_BOUNDARY_LENGTH = 70;

    private MultipartFormDataParser() {
    }

    public static MultipartFormData parse(
            HttpExchange exchange,
            Set<String> expectedFieldNames,
            int maxBodyBytes
    ) throws IOException {
        String contentType = exchange
                .getRequestHeaders()
                .getFirst("Content-Type");

        if (
                contentType == null
                        || !contentType
                        .toLowerCase(Locale.ROOT)
                        .startsWith("multipart/form-data")
        ) {
            throw new InvalidMultipartException();
        }

        String boundary = extractBoundary(contentType);

        if (
                boundary == null
                        || boundary.isEmpty()
                        || boundary.length()
                        > MAX_BOUNDARY_LENGTH
        ) {
            throw new InvalidMultipartException();
        }

        byte[] body;

        try (
                InputStream input =
                        exchange.getRequestBody()
        ) {
            body = readWithLimit(input, maxBodyBytes);
        }

        return parseBody(
                body, boundary, expectedFieldNames
        );
    }

    private static byte[] readWithLimit(
            InputStream input,
            int maxBodyBytes
    ) throws IOException {
        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;

        while ((read = input.read(chunk)) != -1) {
            total += read;

            if (total > maxBodyBytes) {
                throw new MultipartTooLargeException();
            }

            buffer.write(chunk, 0, read);
        }

        return buffer.toByteArray();
    }

    private static String extractBoundary(
            String contentType
    ) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();

            if (
                    trimmed.regionMatches(
                            true, 0, "boundary=", 0, 9
                    )
            ) {
                String value = trimmed.substring(9).trim();

                if (
                        value.length() >= 2
                                && value.startsWith("\"")
                                && value.endsWith("\"")
                ) {
                    value = value.substring(
                            1, value.length() - 1
                    );
                }

                return value;
            }
        }

        return null;
    }

    static MultipartFormData parseBody(
            byte[] body,
            String boundary,
            Set<String> expectedFieldNames
    ) {
        byte[] delimiter =
                ("--" + boundary)
                        .getBytes(StandardCharsets.US_ASCII);

        List<Integer> positions =
                findBoundaryPositions(body, delimiter);

        if (positions.size() < 2) {
            throw new InvalidMultipartException();
        }

        int lastStart =
                positions.get(positions.size() - 1);
        int lastEnd = lastStart + delimiter.length;

        if (
                lastEnd + 1 >= body.length
                        || body[lastEnd] != '-'
                        || body[lastEnd + 1] != '-'
        ) {
            throw new InvalidMultipartException();
        }

        Map<String, String> textFields =
                new LinkedHashMap<>();
        byte[] fileBytes = null;
        Set<String> seenNames = new HashSet<>();

        for (int i = 0;
             i < positions.size() - 1;
             i++) {
            int partStart =
                    positions.get(i) + delimiter.length;
            int partEnd = positions.get(i + 1);

            if (
                    partStart + 1 >= body.length
                            || body[partStart] != '\r'
                            || body[partStart + 1] != '\n'
            ) {
                throw new InvalidMultipartException();
            }

            int contentStart = partStart + 2;
            int contentEnd = partEnd;

            if (
                    contentEnd - contentStart < 2
                            || body[contentEnd - 2] != '\r'
                            || body[contentEnd - 1] != '\n'
            ) {
                throw new InvalidMultipartException();
            }

            contentEnd -= 2;

            byte[] headerSeparator =
                    "\r\n\r\n".getBytes(
                            StandardCharsets.US_ASCII
                    );

            int headerEnd = indexOf(
                    body,
                    headerSeparator,
                    contentStart,
                    contentEnd
            );

            if (headerEnd < 0) {
                throw new InvalidMultipartException();
            }

            String headerText = new String(
                    body,
                    contentStart,
                    headerEnd - contentStart,
                    StandardCharsets.US_ASCII
            );

            int fieldContentStart =
                    headerEnd + headerSeparator.length;

            String contentDisposition = null;

            for (
                    String line
                    : headerText.split("\r\n")
            ) {
                if (
                        line.toLowerCase(Locale.ROOT)
                                .startsWith(
                                        "content-disposition:"
                                )
                ) {
                    contentDisposition = line;
                }
            }

            if (contentDisposition == null) {
                throw new InvalidMultipartException();
            }

            String name = extractDispositionParameter(
                    contentDisposition, "name"
            );

            if (name == null) {
                throw new InvalidMultipartException();
            }

            boolean isFile =
                    extractDispositionParameter(
                            contentDisposition, "filename"
                    ) != null;

            if (!expectedFieldNames.contains(name)) {
                throw new InvalidMultipartException();
            }

            if (!seenNames.add(name)) {
                throw new InvalidMultipartException();
            }

            byte[] fieldBytes = Arrays.copyOfRange(
                    body, fieldContentStart, contentEnd
            );

            if (isFile) {
                fileBytes = fieldBytes;
            } else {
                textFields.put(
                        name,
                        new String(
                                fieldBytes,
                                StandardCharsets.UTF_8
                        )
                );
            }
        }

        return new MultipartFormData(
                textFields,
                Optional.ofNullable(fileBytes)
        );
    }

    private static String extractDispositionParameter(
            String headerLine,
            String parameterName
    ) {
        for (String segment : headerLine.split(";")) {
            String trimmed = segment.trim();
            String prefix = parameterName + "=";

            if (
                    trimmed.regionMatches(
                            true,
                            0,
                            prefix,
                            0,
                            prefix.length()
                    )
            ) {
                String value = trimmed.substring(
                        prefix.length()
                ).trim();

                if (
                        value.length() >= 2
                                && value.startsWith("\"")
                                && value.endsWith("\"")
                ) {
                    value = value.substring(
                            1, value.length() - 1
                    );
                }

                return value;
            }
        }

        return null;
    }

    private static List<Integer> findBoundaryPositions(
            byte[] body,
            byte[] delimiter
    ) {
        List<Integer> result = new ArrayList<>();
        int from = 0;

        while (true) {
            int index = indexOf(
                    body,
                    delimiter,
                    from,
                    body.length
            );

            if (index < 0) {
                break;
            }

            if (isBoundaryMarker(body, delimiter, index)) {
                result.add(index);
            }

            from = index + delimiter.length;
        }

        return result;
    }

    private static boolean isBoundaryMarker(
            byte[] body,
            byte[] delimiter,
            int index
    ) {
        boolean validPrefix = index == 0
                || (
                index >= 2
                        && body[index - 2] == '\r'
                        && body[index - 1] == '\n'
        );

        if (!validPrefix) {
            return false;
        }

        int suffix = index + delimiter.length;

        if (suffix + 1 >= body.length) {
            return false;
        }

        boolean regularBoundary =
                body[suffix] == '\r'
                        && body[suffix + 1] == '\n';

        boolean closingBoundary =
                body[suffix] == '-'
                        && body[suffix + 1] == '-';

        return regularBoundary || closingBoundary;
    }

    private static int indexOf(
            byte[] haystack,
            byte[] needle,
            int from,
            int to
    ) {
        outer:
        for (
                int i = from;
                i <= to - needle.length;
                i++
        ) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }

            return i;
        }

        return -1;
    }

    public static final class InvalidMultipartException
            extends RuntimeException {
    }

    public static final class MultipartTooLargeException
            extends RuntimeException {
    }
}
