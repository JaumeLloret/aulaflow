package es.aulaflow.application.csv;

import es.aulaflow.domain.board.BoardId;

/**
 * Puerto de escritura atómica de un {@link CsvImportPlan} completo:
 * tablero, columnas y tarjetas en una única transacción. No debe
 * llamar en bucle a repositorios que abren conexiones independientes.
 */
public interface CsvImportRepository {

    BoardId importPlan(long ownerId, CsvImportPlan plan);
}
