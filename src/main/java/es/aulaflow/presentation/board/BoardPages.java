package es.aulaflow.presentation.board;

import es.aulaflow.application.auth.CsrfToken;
import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.ChecklistItem;
import es.aulaflow.domain.board.ChecklistProgress;
import es.aulaflow.domain.board.Label;
import es.aulaflow.domain.board.LabelColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class BoardPages {

    private BoardPages() {
    }

    static String list(
            List<Board> boards,
            CsrfToken csrfToken
    ) {
        StringBuilder items = new StringBuilder();

        if (boards.isEmpty()) {
            items.append("""
                    <p class="empty-state" data-i18n="boards.empty">
                        Encara no hi ha cap tauler.
                    </p>
                    """);
        } else {
            items.append("<ul class=\"board-list\">");
            for (Board board : boards) {
                items.append("""
                        <li>
                          <a class="board-link" href="/boards/%d">%s</a>
                        </li>
                        """.formatted(
                        board.id().value(),
                        escape(board.name().value())
                ));
            }
            items.append("</ul>");
        }

        String content = """
                <section class="boards-panel" aria-labelledby="boards-title">
                  <h1 id="boards-title" data-i18n="boards.title">Els meus taulers</h1>
                  <p data-i18n="boards.description">
                    Crea un tauler personal i organitza les seues columnes.
                  </p>
                  <p>
                    <a href="/boards/import" data-i18n="csv.import.nav">Importar CSV</a>
                  </p>
                  %s
                </section>
                <section class="boards-panel" aria-labelledby="create-board-title">
                  <h2 id="create-board-title" data-i18n="boards.create.title">
                    Crear un tauler
                  </h2>
                  <form method="post" action="/boards" class="board-form">
                    %s
                    <label for="board-name" data-i18n="boards.name.label">
                      Nom del tauler
                    </label>
                    <input id="board-name" name="name" required maxlength="100">
                    <button type="submit" data-i18n="boards.create.submit">
                      Crear tauler
                    </button>
                  </form>
                </section>
                """.formatted(
                items,
                csrfField(csrfToken)
        );

        return layout(
                "Els meus taulers · AulaFlow",
                content,
                csrfToken
        );
    }

    static String details(
            BoardDetails details,
            List<Card> cards,
            List<Label> boardLabels,
            Map<Long, List<Label>> labelsByCard,
            Map<Long, List<ChecklistItem>> itemsByCard,
            CsrfToken csrfToken
    ) {
        Board board = details.board();

        StringBuilder columns = new StringBuilder(
                "<ol class=\"kanban-board\">"
        );

        for (int index = 0;
             index < details.columns().size();
             index++) {
            BoardColumn column =
                    details.columns().get(index);

            List<Card> colCards = cards.stream()
                    .filter(c ->
                            c.columnId().value()
                                    == column.id().value()
                    )
                    .toList();

            Long prevColId = index > 0
                    ? details.columns()
                    .get(index - 1).id().value()
                    : null;
            Long nextColId =
                    index < details.columns().size() - 1
                            ? details.columns()
                            .get(index + 1).id().value()
                            : null;
            int prevColSize = prevColId == null ? 0
                    : (int) cards.stream()
                    .filter(c ->
                            c.columnId().value()
                                    == prevColId
                    )
                    .count();
            int nextColSize = nextColId == null ? 0
                    : (int) cards.stream()
                    .filter(c ->
                            c.columnId().value()
                                    == nextColId
                    )
                    .count();

            columns.append(
                    columnSection(
                            board,
                            column,
                            index,
                            details,
                            colCards,
                            prevColId,
                            prevColSize,
                            nextColId,
                            nextColSize,
                            boardLabels,
                            labelsByCard,
                            itemsByCard,
                            csrfToken
                    )
            );
        }
        columns.append("</ol>");

        String content = """
                <nav aria-label="Navegació de taulers">
                  <a href="/boards" data-i18n="boards.back">Tornar als taulers</a>
                </nav>
                <section class="boards-panel" aria-labelledby="board-title">
                  <h1 id="board-title">%s</h1>
                  <p>
                    <a href="/boards/%d/export.csv" data-i18n="csv.export.nav">Exportar CSV</a>
                  </p>
                  <form method="post" action="/boards/%d/rename"
                        class="board-form">
                    %s
                    <label for="rename-board" data-i18n="boards.rename.label">
                      Nou nom del tauler
                    </label>
                    <input id="rename-board" name="name" value="%s"
                           required maxlength="100">
                    <button type="submit" data-i18n="boards.rename.submit">
                      Canviar nom
                    </button>
                  </form>
                </section>
                %s
                %s
                <section class="boards-panel" aria-labelledby="create-column-title">
                  <h2 id="create-column-title" data-i18n="columns.create.title">
                    Afegir una columna
                  </h2>
                  <form method="post" action="/boards/%d/columns"
                        class="board-form">
                    %s
                    <label for="column-name" data-i18n="columns.name.label">
                      Nom de la columna
                    </label>
                    <input id="column-name" name="name" required maxlength="80">
                    <button type="submit" data-i18n="columns.create.submit">
                      Afegir columna
                    </button>
                  </form>
                </section>
                <div role="status"
                     aria-live="polite"
                     class="dnd-status"
                     data-i18n="cards.dnd.available">
                  Arrossega i deixa anar per moure targetes.
                </div>
                """.formatted(
                escape(board.name().value()),
                board.id().value(),
                board.id().value(),
                csrfField(csrfToken),
                escapeAttribute(board.name().value()),
                columns,
                labelsManagementSection(board, boardLabels, csrfToken),
                board.id().value(),
                csrfField(csrfToken)
        );

        return layout(
                board.name().value() + " · AulaFlow",
                content,
                csrfToken
        );
    }

    private static String labelsManagementSection(
            Board board,
            List<Label> boardLabels,
            CsrfToken csrfToken
    ) {
        StringBuilder items = new StringBuilder();

        if (boardLabels.isEmpty()) {
            items.append("""
                    <p class="empty-state" data-i18n="labels.empty">
                      Encara no hi ha cap etiqueta.
                    </p>
                    """);
        } else {
            items.append("<ul class=\"label-list\">");
            for (Label label : boardLabels) {
                items.append(labelManagementItem(
                        board, label, csrfToken
                ));
            }
            items.append("</ul>");
        }

        return """
                <section class="boards-panel" aria-labelledby="labels-title">
                  <h2 id="labels-title" data-i18n="labels.title">
                    Etiquetes del tauler
                  </h2>
                  %s
                  <form method="post" action="/boards/%d/labels"
                        class="board-form">
                    %s
                    <label for="label-name" data-i18n="labels.name.label">
                      Nom de l'etiqueta
                    </label>
                    <input id="label-name" name="name" required maxlength="40">
                    <label for="label-color" data-i18n="labels.color.label">
                      Color
                    </label>
                    %s
                    <button type="submit" data-i18n="labels.create.submit">
                      Crear etiqueta
                    </button>
                  </form>
                </section>
                """.formatted(
                items,
                board.id().value(),
                csrfField(csrfToken),
                colorSelect("label-color")
        );
    }

    private static String labelManagementItem(
            Board board,
            Label label,
            CsrfToken csrfToken
    ) {
        return """
                <li class="label-list-item">
                  %s
                  <details class="label-edit">
                    <summary data-i18n="labels.edit">Editar</summary>
                    <form method="post"
                          action="/boards/%d/labels/%d/edit"
                          class="label-edit-form">
                      %s
                      <label for="label-name-%d"
                             data-i18n="labels.name.label">
                        Nom de l'etiqueta
                      </label>
                      <input id="label-name-%d" name="name"
                             value="%s" required maxlength="40">
                      <label for="label-color-%d"
                             data-i18n="labels.color.label">
                        Color
                      </label>
                      %s
                      <button type="submit"
                              data-i18n="labels.edit.submit">
                        Guardar
                      </button>
                    </form>
                  </details>
                  <form method="post"
                        action="/boards/%d/labels/%d/delete"
                        class="label-delete-form">
                    %s
                    <button type="submit" class="danger-button"
                            data-i18n="labels.delete.submit">
                      Eliminar
                    </button>
                  </form>
                </li>
                """.formatted(
                labelChip(label),
                board.id().value(),
                label.id().value(),
                csrfField(csrfToken),
                label.id().value(),
                label.id().value(),
                escapeAttribute(label.name().value()),
                label.id().value(),
                colorSelect(
                        "label-color-" + label.id().value(),
                        label.color()
                ),
                board.id().value(),
                label.id().value(),
                csrfField(csrfToken)
        );
    }

    private static String colorSelect(String id) {
        return colorSelect(id, null);
    }

    private static String colorSelect(
            String id,
            LabelColor selected
    ) {
        StringBuilder options = new StringBuilder();

        for (LabelColor color : LabelColor.values()) {
            String key = color.key();

            options.append("""
                    <option value="%s"%s data-i18n="labels.color.%s">%s</option>
                    """.formatted(
                    key,
                    color == selected ? " selected" : "",
                    key,
                    key
            ));
        }

        return """
                <select id="%s" name="color" required>
                  %s
                </select>
                """.formatted(id, options);
    }

    private static String labelChip(Label label) {
        return """
                <span class="label-chip label-chip--%s">%s</span>
                """.formatted(
                label.color().key(),
                escape(label.name().value())
        ).strip();
    }

    private static String columnSection(
            Board board,
            BoardColumn column,
            int index,
            BoardDetails details,
            List<Card> colCards,
            Long prevColId,
            int prevColSize,
            Long nextColId,
            int nextColSize,
            List<Label> boardLabels,
            Map<Long, List<Label>> labelsByCard,
            Map<Long, List<ChecklistItem>> itemsByCard,
            CsrfToken csrfToken
    ) {
        String cardListHtml;
        if (colCards.isEmpty()) {
            cardListHtml = """
                    <p class="card-empty" data-i18n="cards.column.empty">
                      Cap targeta en aquesta columna.
                    </p>
                    """;
        } else {
            StringBuilder cardItems = new StringBuilder(
                    "<ul class=\"card-list\" data-column-id=\"%d\">"
                            .formatted(column.id().value())
            );
            for (int i = 0; i < colCards.size(); i++) {
                Card card = colCards.get(i);
                cardItems.append(
                        cardItem(
                                board,
                                card,
                                csrfToken,
                                i > 0,
                                i < colCards.size() - 1,
                                prevColId,
                                prevColSize,
                                nextColId,
                                nextColSize,
                                boardLabels,
                                labelsByCard.getOrDefault(
                                        card.id().value(),
                                        List.of()
                                ),
                                itemsByCard.getOrDefault(
                                        card.id().value(),
                                        List.of()
                                )
                        )
                );
            }
            cardItems.append("</ul>");
            cardListHtml = cardItems.toString();
        }

        String createCardForm = """
                <form method="post"
                      action="/boards/%d/columns/%d/cards"
                      class="card-create-form">
                  %s
                  <label for="card-title-col-%d" data-i18n="cards.title.label">
                    Títol de la targeta
                  </label>
                  <input id="card-title-col-%d" name="title"
                         required maxlength="160">
                  <label for="card-desc-col-%d"
                         data-i18n="cards.description.label">
                    Descripció
                  </label>
                  <textarea id="card-desc-col-%d" name="description"
                            maxlength="4000"></textarea>
                  <button type="submit" data-i18n="cards.create.submit">
                    Afegir targeta
                  </button>
                </form>
                """.formatted(
                board.id().value(),
                column.id().value(),
                csrfField(csrfToken),
                column.id().value(),
                column.id().value(),
                column.id().value(),
                column.id().value()
        );

        return """
                <li class="kanban-column" data-column-id="%d">
                  <h2>%s</h2>
                  <form method="post"
                        action="/boards/%d/columns/%d/rename"
                        class="column-form">
                    %s
                    <label for="column-name-%d" data-i18n="columns.rename.label">
                      Nou nom
                    </label>
                    <input id="column-name-%d" name="name" value="%s"
                           required maxlength="80">
                    <button type="submit" data-i18n="columns.rename.submit">
                      Canviar nom
                    </button>
                  </form>
                  %s
                  %s
                  %s
                </li>
                """.formatted(
                column.id().value(),
                escape(column.name().value()),
                board.id().value(),
                column.id().value(),
                csrfField(csrfToken),
                column.id().value(),
                column.id().value(),
                escapeAttribute(column.name().value()),
                cardListHtml,
                createCardForm,
                moveForms(details, index, csrfToken)
        );
    }

    private static String cardItem(
            Board board,
            Card card,
            CsrfToken csrfToken,
            boolean canMoveUp,
            boolean canMoveDown,
            Long prevColId,
            int prevColSize,
            Long nextColId,
            int nextColSize,
            List<Label> boardLabels,
            List<Label> assignedLabels,
            List<ChecklistItem> checklistItems
    ) {
        StringBuilder moveActions = new StringBuilder();

        if (canMoveUp) {
            moveActions.append(
                    moveCardForm(
                            board.id().value(),
                            card.id().value(),
                            card.columnId().value(),
                            card.position() - 1,
                            "cards.move.up",
                            "Pujar",
                            csrfToken
                    )
            );
        }
        if (canMoveDown) {
            moveActions.append(
                    moveCardForm(
                            board.id().value(),
                            card.id().value(),
                            card.columnId().value(),
                            card.position() + 1,
                            "cards.move.down",
                            "Baixar",
                            csrfToken
                    )
            );
        }
        if (prevColId != null) {
            moveActions.append(
                    moveCardForm(
                            board.id().value(),
                            card.id().value(),
                            prevColId,
                            prevColSize,
                            "cards.move.prev.column",
                            "Columna anterior",
                            csrfToken
                    )
            );
        }
        if (nextColId != null) {
            moveActions.append(
                    moveCardForm(
                            board.id().value(),
                            card.id().value(),
                            nextColId,
                            nextColSize,
                            "cards.move.next.column",
                            "Columna següent",
                            csrfToken
                    )
            );
        }

        String descHtml = card.description().value().isBlank()
                ? ""
                : "<p class=\"card-description\">%s</p>"
                .formatted(escape(card.description().value()));

        return """
                <li class="card-item"
                    data-card-id="%d"
                    data-column-id="%d"
                    data-position="%d">
                  <span class="card-title">%s</span>
                  %s
                  <div class="card-move-actions">%s</div>
                  %s
                  %s
                  <details class="card-edit">
                    <summary data-i18n="cards.edit">Editar</summary>
                    <form method="post"
                          action="/boards/%d/cards/%d/edit"
                          class="card-edit-form">
                      %s
                      <label for="card-title-%d" data-i18n="cards.title.label">
                        Títol
                      </label>
                      <input id="card-title-%d" name="title"
                             value="%s" required maxlength="160">
                      <label for="card-desc-%d"
                             data-i18n="cards.description.label">
                        Descripció
                      </label>
                      <textarea id="card-desc-%d"
                                name="description"
                                maxlength="4000">%s</textarea>
                      <button type="submit" data-i18n="cards.save">
                        Guardar
                      </button>
                    </form>
                  </details>
                  <form method="post"
                        action="/boards/%d/cards/%d/delete"
                        class="card-delete-form">
                    %s
                    <button type="submit" class="danger-button"
                            data-i18n="cards.delete.submit">
                      Eliminar
                    </button>
                  </form>
                </li>
                """.formatted(
                card.id().value(),
                card.columnId().value(),
                card.position(),
                escape(card.title().value()),
                descHtml,
                moveActions,
                cardLabelsSection(
                        board, card, boardLabels,
                        assignedLabels, csrfToken
                ),
                checklistSection(
                        board, card, checklistItems, csrfToken
                ),
                board.id().value(),
                card.id().value(),
                csrfField(csrfToken),
                card.id().value(),
                card.id().value(),
                escapeAttribute(card.title().value()),
                card.id().value(),
                card.id().value(),
                escape(card.description().value()),
                board.id().value(),
                card.id().value(),
                csrfField(csrfToken)
        );
    }

    private static String cardLabelsSection(
            Board board,
            Card card,
            List<Label> boardLabels,
            List<Label> assignedLabels,
            CsrfToken csrfToken
    ) {
        StringBuilder chips = new StringBuilder();

        if (assignedLabels.isEmpty()) {
            chips.append("""
                    <p class="card-labels-empty"
                       data-i18n="labels.card.empty">
                      Sense etiquetes.
                    </p>
                    """);
        } else {
            chips.append("<div class=\"card-label-chips\">");
            for (Label label : assignedLabels) {
                chips.append(labelChip(label));
            }
            chips.append("</div>");
        }

        java.util.Set<Long> assignedIds = assignedLabels
                .stream()
                .map(label -> label.id().value())
                .collect(java.util.stream.Collectors.toSet());

        StringBuilder toggleForms = new StringBuilder();

        for (Label label : boardLabels) {
            boolean assigned = assignedIds.contains(
                    label.id().value()
            );

            String actionText = assigned
                    ? "Retirar"
                    : "Assignar";

            String labelName =
                    escape(label.name().value());

            toggleForms.append("""
                    <form method="post"
                          action="/boards/%d/cards/%d/labels/%d/%s"
                          class="card-label-toggle-form">
                      %s
                      <button type="submit"
                              class="secondary-button"
                              data-i18n="%s"
                              data-label-name="%s">
                        %s: %s
                      </button>
                    </form>
                    """.formatted(
                    board.id().value(),
                    card.id().value(),
                    label.id().value(),
                    assigned ? "unassign" : "assign",
                    csrfField(csrfToken),
                    assigned
                            ? "labels.unassign"
                            : "labels.assign",
                    labelName,
                    actionText,
                    labelName
            ));
        }

        return """
                <div class="card-labels">
                  %s
                  <details class="card-label-manager">
                    <summary data-i18n="labels.manage">
                      Gestionar etiquetes
                    </summary>
                    <div class="card-label-toggle-list">%s</div>
                  </details>
                </div>
                """.formatted(chips, toggleForms);
    }

    private static String checklistSection(
            Board board,
            Card card,
            List<ChecklistItem> items,
            CsrfToken csrfToken
    ) {
        ChecklistProgress progress =
                ChecklistProgress.of(items);

        StringBuilder rows = new StringBuilder();

        if (items.isEmpty()) {
            rows.append("""
                    <p class="checklist-empty"
                       data-i18n="checklist.empty">
                      Cap element de checklist.
                    </p>
                    """);
        } else {
            rows.append("<ul class=\"checklist-list\">");
            for (int i = 0; i < items.size(); i++) {
                rows.append(
                        checklistItemRow(
                                board,
                                card,
                                items.get(i),
                                i > 0,
                                i < items.size() - 1,
                                csrfToken
                        )
                );
            }
            rows.append("</ul>");
        }

        return """
                <div class="checklist-section">
                  <details class="checklist-details">
                    <summary>
                      <span data-i18n="checklist.title">Checklist</span>
                      <span class="checklist-progress">%d/%d (%d%%)</span>
                    </summary>
                    %s
                    <form method="post"
                          action="/boards/%d/cards/%d/checklist-items"
                          class="checklist-add-form">
                      %s
                      <label for="checklist-text-%d"
                             data-i18n="checklist.add.label">
                        Nou element
                      </label>
                      <input id="checklist-text-%d" name="text"
                             required maxlength="280">
                      <button type="submit"
                              data-i18n="checklist.add.submit">
                        Afegir element
                      </button>
                    </form>
                  </details>
                </div>
                """.formatted(
                progress.completedItems(),
                progress.totalItems(),
                progress.percentage(),
                rows,
                board.id().value(),
                card.id().value(),
                csrfField(csrfToken),
                card.id().value(),
                card.id().value()
        );
    }

    private static String checklistItemRow(
            Board board,
            Card card,
            ChecklistItem item,
            boolean canMoveUp,
            boolean canMoveDown,
            CsrfToken csrfToken
    ) {
        String basePath = "/boards/%d/cards/%d/checklist-items/%d"
                .formatted(
                        board.id().value(),
                        card.id().value(),
                        item.id().value()
                );

        String toggleLabelKey = item.completed()
                ? "checklist.item.mark.incomplete"
                : "checklist.item.mark.complete";

        String toggleLabelText = item.completed()
                ? "Marcar com a pendent"
                : "Marcar com a fet";

        StringBuilder moveButtons = new StringBuilder();

        if (canMoveUp) {
            moveButtons.append(
                    checklistMoveForm(
                            basePath,
                            item.position() - 1,
                            "checklist.move.up",
                            "Pujar",
                            csrfToken
                    )
            );
        }

        if (canMoveDown) {
            moveButtons.append(
                    checklistMoveForm(
                            basePath,
                            item.position() + 1,
                            "checklist.move.down",
                            "Baixar",
                            csrfToken
                    )
            );
        }

        return """
                <li class="checklist-item%s"
                    data-item-id="%d"
                    data-position="%d">
                  <form method="post" action="%s/toggle"
                        class="checklist-toggle-form">
                    %s
                    <input type="hidden" name="completed" value="%s">
                    <button type="submit"
                            class="checklist-toggle-button"
                            aria-pressed="%s"
                            data-i18n="%s">
                      %s
                    </button>
                  </form>
                  <span class="checklist-item-text">%s</span>
                  <div class="checklist-move-actions">%s</div>
                  <details class="checklist-item-edit">
                    <summary data-i18n="checklist.item.edit">Editar</summary>
                    <form method="post" action="%s/edit"
                          class="checklist-item-edit-form">
                      %s
                      <label for="checklist-item-text-%d"
                             data-i18n="checklist.item.text.label">
                        Text
                      </label>
                      <input id="checklist-item-text-%d" name="text"
                             value="%s" required maxlength="280">
                      <button type="submit"
                              data-i18n="checklist.item.save">
                        Guardar
                      </button>
                    </form>
                  </details>
                  <form method="post" action="%s/delete"
                        class="checklist-item-delete-form">
                    %s
                    <button type="submit" class="danger-button"
                            data-i18n="checklist.item.delete">
                      Eliminar
                    </button>
                  </form>
                </li>
                """.formatted(
                item.completed() ? " checklist-item--completed" : "",
                item.id().value(),
                item.position(),
                basePath,
                csrfField(csrfToken),
                item.completed() ? "false" : "true",
                item.completed() ? "true" : "false",
                toggleLabelKey,
                toggleLabelText,
                escape(item.text().value()),
                moveButtons,
                basePath,
                csrfField(csrfToken),
                item.id().value(),
                item.id().value(),
                escapeAttribute(item.text().value()),
                basePath,
                csrfField(csrfToken)
        );
    }

    private static String checklistMoveForm(
            String basePath,
            int targetPosition,
            String i18nKey,
            String label,
            CsrfToken csrfToken
    ) {
        return """
                <form method="post" action="%s/move">
                  %s
                  <input type="hidden" name="targetPosition"
                         value="%d">
                  <button type="submit" class="secondary-button"
                          data-i18n="%s">%s</button>
                </form>
                """.formatted(
                basePath,
                csrfField(csrfToken),
                targetPosition,
                i18nKey,
                label
        );
    }

    private static String moveCardForm(
            long boardId,
            long cardId,
            long targetColumnId,
            int targetPosition,
            String i18nKey,
            String label,
            CsrfToken csrfToken
    ) {
        return """
                <form method="post"
                      action="/boards/%d/cards/%d/move">
                  %s
                  <input type="hidden" name="targetColumnId"
                         value="%d">
                  <input type="hidden" name="targetPosition"
                         value="%d">
                  <button type="submit" class="secondary-button"
                          data-i18n="%s">%s</button>
                </form>
                """.formatted(
                boardId,
                cardId,
                csrfField(csrfToken),
                targetColumnId,
                targetPosition,
                i18nKey,
                label
        );
    }

    private static String moveForms(
            BoardDetails details,
            int index,
            CsrfToken csrfToken
    ) {
        StringBuilder forms =
                new StringBuilder(
                        "<div class=\"column-order-actions\">"
                );

        if (index > 0) {
            forms.append(
                    moveForm(
                            details,
                            index,
                            index - 1,
                            csrfToken,
                            "columns.move.left",
                            "Moure a l'esquerra"
                    )
            );
        }

        if (index < details.columns().size() - 1) {
            forms.append(
                    moveForm(
                            details,
                            index,
                            index + 1,
                            csrfToken,
                            "columns.move.right",
                            "Moure a la dreta"
                    )
            );
        }

        forms.append("</div>");
        return forms.toString();
    }

    private static String moveForm(
            BoardDetails details,
            int from,
            int to,
            CsrfToken csrfToken,
            String translationKey,
            String label
    ) {
        List<Long> order = new ArrayList<>(
                details.columns()
                        .stream()
                        .map(column ->
                                column.id().value()
                        )
                        .toList()
        );

        Long moved = order.remove(from);
        order.add(to, moved);

        String encodedOrder = String.join(
                ",",
                order.stream()
                        .map(String::valueOf)
                        .toList()
        );

        return """
                <form method="post" action="/boards/%d/columns/reorder">
                  %s
                  <input type="hidden" name="order" value="%s">
                  <button type="submit" class="secondary-button"
                          data-i18n="%s">%s</button>
                </form>
                """.formatted(
                details.board().id().value(),
                csrfField(csrfToken),
                encodedOrder,
                translationKey,
                label
        );
    }

    private static String layout(
            String title,
            String content,
            CsrfToken csrfToken
    ) {
        return """
                <!DOCTYPE html>
                <html lang="ca">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <link rel="stylesheet" href="/assets/css/app.css">
                </head>
                <body>
                  <header class="site-header site-header--compact">
                    <a class="brand-link" href="/" data-i18n="app.name">AulaFlow</a>
                    <nav aria-label="Navegació principal">
                      <a href="/boards" data-i18n="nav.boards">Taulers</a>
                      <a href="/account" data-i18n="nav.account">Compte</a>
                    </nav>
                    <div class="language-control">
                      <label for="language-selector" data-i18n="language.selector.label">
                        Idioma
                      </label>
                      <select id="language-selector" name="language" disabled>
                        <option value="ca" data-i18n="language.ca" selected>Valencià</option>
                        <option value="es" data-i18n="language.es">Castellà</option>
                      </select>
                    </div>
                    <form method="post" action="/logout">
                      %s
                      <button type="submit" class="secondary-button"
                              data-i18n="logout.submit">Tancar sessió</button>
                    </form>
                  </header>
                  <main class="boards-layout">%s</main>
                  <script src="/assets/js/app.js" defer></script>
                </body>
                </html>
                """.formatted(
                escape(title),
                csrfField(csrfToken),
                content
        );
    }

    private static String csrfField(CsrfToken token) {
        return """
                <input type="hidden" name="_csrf" value="%s">
                """.formatted(token.value());
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeAttribute(
            String value
    ) {
        return escape(value)
                .replace("\n", "&#10;")
                .replace("\r", "&#13;");
    }
}
