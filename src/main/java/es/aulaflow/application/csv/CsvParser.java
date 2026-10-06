package es.aulaflow.application.csv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Trocedador CSV mediante una máquina de estados carácter a carácter,
 * según el contrato AulaFlow Kanban CSV v1. No utiliza
 * {@code String.split}: una coma dentro de un campo entrecomillado no
 * es un delimitador.
 */
public final class CsvParser {

    private static final byte[] UTF8_BOM =
            {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvParser() {
    }

    public static CsvDocument parse(
            InputStream input
    ) throws IOException {
        byte[] rawBytes = readWithLimit(input);
        String text = decodeStrictUtf8(stripBom(rawBytes));
        return tokenize(text);
    }

    private static byte[] readWithLimit(
            InputStream input
    ) throws IOException {
        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;

        while ((read = input.read(chunk)) != -1) {
            total += read;

            if (total > CsvLimits.MAX_UPLOAD_BYTES) {
                throw new CsvParseException(
                        -1,
                        "file_too_large",
                        "El archivo CSV supera el tamaño "
                                + "máximo permitido."
                );
            }

            buffer.write(chunk, 0, read);
        }

        return buffer.toByteArray();
    }

    private static byte[] stripBom(byte[] rawBytes) {
        if (
                rawBytes.length >= 3
                        && rawBytes[0] == UTF8_BOM[0]
                        && rawBytes[1] == UTF8_BOM[1]
                        && rawBytes[2] == UTF8_BOM[2]
        ) {
            byte[] withoutBom =
                    new byte[rawBytes.length - 3];

            System.arraycopy(
                    rawBytes,
                    3,
                    withoutBom,
                    0,
                    withoutBom.length
            );

            return withoutBom;
        }

        return rawBytes;
    }

    private static String decodeStrictUtf8(byte[] bytes) {
        CharsetDecoder decoder =
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(
                                CodingErrorAction.REPORT
                        )
                        .onUnmappableCharacter(
                                CodingErrorAction.REPORT
                        );

        try {
            return decoder
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new CsvParseException(
                    -1,
                    "invalid_utf8",
                    "El archivo CSV no está codificado "
                            + "en UTF-8 válido."
            );
        }
    }

    private static CsvDocument tokenize(String text) {
        List<CsvRecordRow> rows = new ArrayList<>();
        List<String> currentFields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int recordNumber = 1;
        boolean inQuotes = false;
        boolean afterClosingQuote = false;
        int length = text.length();
        int index = 0;

        while (index < length) {
            char current = text.charAt(index);

            if (current == '\u0000') {
                throw new CsvParseException(
                        recordNumber,
                        "nul_character",
                        "El registro " + recordNumber
                                + " contiene un carácter "
                                + "NUL no permitido."
                );
            }

            if (inQuotes) {
                if (current == '"') {
                    if (
                            index + 1 < length
                                    && text.charAt(index + 1)
                                    == '"'
                    ) {
                        field.append('"');
                        index += 2;
                    } else {
                        inQuotes = false;
                        afterClosingQuote = true;
                        index++;
                    }
                } else {
                    field.append(current);
                    index++;
                }
                continue;
            }

            if (
                    afterClosingQuote
                            && current != ','
                            && current != '\r'
                            && current != '\n'
            ) {
                throw new CsvParseException(
                        recordNumber,
                        "malformed_quote",
                        "El registro " + recordNumber
                                + " contiene texto inválido "
                                + "tras cerrar unas comillas."
                );
            }

            if (current == '"') {
                if (field.length() > 0) {
                    throw new CsvParseException(
                            recordNumber,
                            "malformed_quote",
                            "El registro " + recordNumber
                                    + " contiene una comilla "
                                    + "mal colocada."
                    );
                }

                inQuotes = true;
                index++;
                continue;
            }

            if (current == ',') {
                currentFields.add(field.toString());
                field.setLength(0);
                afterClosingQuote = false;
                index++;
                continue;
            }

            if (current == '\r' || current == '\n') {
                currentFields.add(field.toString());
                field.setLength(0);
                afterClosingQuote = false;

                if (
                        current == '\r'
                                && index + 1 < length
                                && text.charAt(index + 1)
                                == '\n'
                ) {
                    index += 2;
                } else {
                    index++;
                }

                finalizeRow(
                        currentFields,
                        recordNumber,
                        rows
                );
                currentFields = new ArrayList<>();
                recordNumber++;
                continue;
            }

            field.append(current);
            index++;
        }

        if (inQuotes) {
            throw new CsvParseException(
                    recordNumber,
                    "unterminated_quote",
                    "El registro " + recordNumber
                            + " tiene una comilla sin cerrar."
            );
        }

        if (
                field.length() > 0
                        || !currentFields.isEmpty()
                        || afterClosingQuote
        ) {
            currentFields.add(field.toString());
            finalizeRow(currentFields, recordNumber, rows);
        }

        if (rows.isEmpty()) {
            throw new CsvParseException(
                    0,
                    "invalid_header",
                    "El archivo CSV está vacío."
            );
        }

        return new CsvDocument(rows);
    }

    private static void finalizeRow(
            List<String> fields,
            int recordNumber,
            List<CsvRecordRow> rows
    ) {
        if (
                fields.size()
                        != CsvLimits.EXPECTED_FIELD_COUNT
        ) {
            throw new CsvParseException(
                    recordNumber,
                    "invalid_field_count",
                    "El registro " + recordNumber
                            + " no tiene el número de "
                            + "campos esperado."
            );
        }

        rows.add(
                new CsvRecordRow(
                        recordNumber,
                        List.copyOf(fields)
                )
        );

        if (rows.size() > CsvLimits.MAX_LOGICAL_RECORDS) {
            throw new CsvParseException(
                    recordNumber,
                    "too_many_records",
                    "El archivo CSV supera el número "
                            + "máximo de registros lógicos "
                            + "permitidos."
            );
        }
    }
}
