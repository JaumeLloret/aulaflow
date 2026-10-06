"use strict";

const DEFAULT_LANGUAGE = "ca";
const LANGUAGE_STORAGE_KEY = "aulaflow.language";
const SUPPORTED_LANGUAGES = new Set(["ca", "es"]);
const CATALOG_BASE_PATH = "/assets/i18n";
const rootElement = document.documentElement;

let currentCatalog = null;
let draggedCardId = null;
let activeCardModal = null;

rootElement.dataset.js = "enabled";

initializeVisualDesign();
initializeLanguageSelector();
initializeDragAndDrop();

function initializeVisualDesign() {
    const body = document.body;
    if (!body || body.classList.contains("login-page")) return;

    const boardsLayout = document.querySelector(".boards-layout");
    if (!boardsLayout) return;

    body.classList.add("app-page");
    enhanceApplicationHeader();

    if (window.location.pathname === "/boards") {
        enhanceBoardsIndex(boardsLayout);
        return;
    }

    if (/^\/boards\/\d+\/?$/.test(window.location.pathname)) {
        enhanceBoardDetails(boardsLayout);
    }
}

function enhanceApplicationHeader() {
    const header = document.querySelector(".site-header");
    if (!header) return;

    header.classList.add("app-topbar");

    const brand = header.querySelector(".brand-link");
    if (brand) brand.classList.add("app-brand");

    const nav = header.querySelector("nav");
    if (nav) {
        nav.classList.add("app-nav");
        const accountLink = nav.querySelector('a[href="/account"]');
        if (accountLink) accountLink.classList.add("app-account-link");
    }

    const languageControl = header.querySelector(".language-control");
    if (languageControl) languageControl.classList.add("language-control--app");

    const logoutButton = header.querySelector('form[action="/logout"] button');
    if (logoutButton) logoutButton.classList.add("app-logout-button");
}

function enhanceBoardsIndex(layout) {
    document.body.classList.add("boards-list-page");

    const panels = [...layout.querySelectorAll(":scope > .boards-panel")];
    if (panels.length < 2) return;

    const listPanel = panels[0];
    const createPanel = panels[1];
    listPanel.classList.add("boards-dashboard");

    const title = listPanel.querySelector("h1");
    const description = listPanel.querySelector("h1 + p");
    const importLink = listPanel.querySelector('a[href="/boards/import"]');
    const importParagraph = importLink ? importLink.closest("p") : null;
    const createForm = createPanel.querySelector(".board-form");

    const header = document.createElement("div");
    header.className = "boards-dashboard__header";

    const heading = document.createElement("div");
    heading.className = "boards-dashboard__heading";
    if (title) heading.append(title);
    if (description) heading.append(description);

    const controls = document.createElement("div");
    controls.className = "boards-dashboard__controls";

    if (createForm) {
        prepareBoardCreateForm(createForm);
        controls.append(createForm);
    }

    if (importLink) {
        importLink.classList.add("secondary-button", "import-button");
        controls.append(importLink);
    }

    header.append(heading, controls);
    listPanel.prepend(header);

    if (importParagraph) importParagraph.remove();
    createPanel.remove();

    enhanceBoardCards(listPanel);
}

function prepareBoardCreateForm(form) {
    form.classList.add("board-create-inline");

    const label = form.querySelector('label[for="board-name"]');
    const input = form.querySelector("#board-name");
    const button = form.querySelector('button[type="submit"]');

    if (label) label.classList.add("visually-hidden");
    if (input && label) {
        input.placeholder = label.textContent.trim();
        input.dataset.placeholderI18n = label.dataset.i18n || "";
    }
    if (button) button.classList.add("primary-button");
}

function enhanceBoardCards(panel) {
    const links = [...panel.querySelectorAll(".board-list .board-link")];
    const accentClasses = [
        "board-card--blue",
        "board-card--purple",
        "board-card--green",
        "board-card--orange",
        "board-card--red"
    ];

    links.forEach(function (link, index) {
        const title = link.textContent.trim();
        link.classList.add("board-card", accentClasses[index % accentClasses.length]);
        link.setAttribute("aria-label", title);
        link.textContent = "";

        const icon = document.createElement("span");
        icon.className = "board-card__icon";
        icon.setAttribute("aria-hidden", "true");
        icon.textContent = firstLetter(title);

        const titleElement = document.createElement("span");
        titleElement.className = "board-card__title";
        titleElement.textContent = title;

        const arrow = document.createElement("span");
        arrow.className = "board-card__arrow";
        arrow.setAttribute("aria-hidden", "true");
        arrow.textContent = "→";

        link.append(icon, titleElement, arrow);
    });
}

function enhanceBoardDetails(layout) {
    document.body.classList.add("board-detail-page");

    const nav = layout.querySelector(":scope > nav");
    const boardPanel = layout.querySelector(":scope > .boards-panel");
    const boardTitle = boardPanel ? boardPanel.querySelector("h1") : null;

    if (nav && boardPanel && boardTitle) {
        const context = document.createElement("div");
        context.className = "board-context";

        const backLink = nav.querySelector("a");
        if (backLink) {
            backLink.classList.add("board-back-link");
            context.append(backLink);
        }

        const titleWrap = document.createElement("div");
        titleWrap.className = "board-context__title";
        titleWrap.append(boardTitle);
        context.append(titleWrap);

        const tools = createBoardTools(boardPanel);
        if (tools) context.append(tools);

        layout.insertBefore(context, nav);
        nav.remove();
        boardPanel.remove();
    }

    const board = layout.querySelector(".kanban-board");
    if (board) {
        board.classList.add("kanban-board--enhanced");
        const columns = [...board.querySelectorAll(":scope > .kanban-column")];
        columns.forEach(enhanceKanbanColumn);
        moveAddColumnFormIntoBoard(layout, board);
    }

    collapseBoardLabelSettings(layout);
}

function createBoardTools(panel) {
    const contentNodes = [...panel.childNodes].filter(function (node) {
        return !(node.nodeType === Node.ELEMENT_NODE && node.matches("h1"));
    });
    if (contentNodes.length === 0) return null;

    const details = document.createElement("details");
    details.className = "board-tools";

    const summary = document.createElement("summary");
    summary.setAttribute("aria-label", localizedText("Opcions del tauler", "Opciones del tablero"));
    summary.textContent = "•••";

    const content = document.createElement("div");
    content.className = "board-tools__content";
    contentNodes.forEach(function (node) {
        content.append(node);
    });

    const form = content.querySelector(".board-form");
    if (form) {
        const input = form.querySelector("input:not([type=hidden])");
        const label = input ? form.querySelector(`label[for="${input.id}"]`) : null;
        if (label) label.classList.add("visually-hidden");
        if (input && label) {
            input.placeholder = label.textContent.trim();
            input.dataset.placeholderI18n = label.dataset.i18n || "";
        }
        const button = form.querySelector("button");
        if (button) button.classList.add("primary-button");
    }

    details.append(summary, content);
    return details;
}

function enhanceKanbanColumn(column) {
    const heading = column.querySelector(":scope > h2");
    const cardCount = column.querySelectorAll(".card-item").length;

    if (heading) {
        const header = document.createElement("div");
        header.className = "kanban-column__header";

        const count = document.createElement("span");
        count.className = "kanban-column__count";
        count.textContent = String(cardCount);
        count.setAttribute("aria-label", String(cardCount));

        header.append(heading, count);

        const menu = createColumnMenu(column);
        if (menu) header.append(menu);

        column.prepend(header);
    }

    const createForm = column.querySelector(":scope > .card-create-form");
    if (createForm) {
        enhanceInlineEditor(
            createForm,
            "cards.create.submit",
            "Afegir targeta",
            "Añadir tarjeta"
        );
    }

    [...column.querySelectorAll(".card-item")].forEach(enhanceCard);
}

function createColumnMenu(column) {
    const renameForm = column.querySelector(":scope > .column-form");
    const orderActions = column.querySelector(":scope > .column-order-actions");
    if (!renameForm && !orderActions) return null;

    const details = document.createElement("details");
    details.className = "column-menu";

    const summary = document.createElement("summary");
    summary.setAttribute("aria-label", localizedText("Opcions de la columna", "Opciones de la columna"));
    summary.textContent = "•••";

    const content = document.createElement("div");
    content.className = "column-menu__content";

    if (renameForm) {
        const input = renameForm.querySelector("input:not([type=hidden])");
        const label = input ? renameForm.querySelector(`label[for="${input.id}"]`) : null;
        if (label) label.classList.add("visually-hidden");
        if (input && label) {
            input.placeholder = label.textContent.trim();
            input.dataset.placeholderI18n = label.dataset.i18n || "";
        }
        content.append(renameForm);
    }
    if (orderActions) content.append(orderActions);

    details.append(summary, content);
    return details;
}

function enhanceInlineEditor(form, translationKey, fallbackCa, fallbackEs) {
    form.classList.add("inline-editor");
    form.hidden = true;

    const firstField = form.querySelector("input:not([type=hidden]), textarea");
    const firstLabel = firstField && firstField.id
        ? form.querySelector(`label[for="${firstField.id}"]`)
        : null;

    if (firstLabel) {
        firstLabel.classList.add("visually-hidden");
        firstField.placeholder = firstLabel.textContent.trim();
        firstField.dataset.placeholderI18n = firstLabel.dataset.i18n || "";
    }

    const submit = form.querySelector('button[type="submit"]');
    if (submit) submit.classList.add("primary-button");

    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "inline-editor-trigger";
    trigger.dataset.uiI18n = translationKey;
    trigger.dataset.fallbackCa = fallbackCa;
    trigger.dataset.fallbackEs = fallbackEs;
    trigger.textContent = `＋ ${fallbackCa}`;

    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "inline-editor-cancel";
    cancel.dataset.localCa = "Cancel·lar";
    cancel.dataset.localEs = "Cancelar";
    cancel.textContent = localizedText("Cancel·lar", "Cancelar");
    form.append(cancel);

    trigger.addEventListener("click", function () {
        trigger.hidden = true;
        form.hidden = false;
        if (firstField) firstField.focus();
    });

    cancel.addEventListener("click", function () {
        form.reset();
        form.hidden = true;
        trigger.hidden = false;
        trigger.focus();
    });

    form.before(trigger);
}

function moveAddColumnFormIntoBoard(layout, board) {
    const title = layout.querySelector("#create-column-title");
    const panel = title ? title.closest(".boards-panel") : null;
    const form = panel ? panel.querySelector(".board-form") : null;
    if (!panel || !form) return;

    const slot = document.createElement("li");
    slot.className = "add-column-slot";

    enhanceInlineEditor(
        form,
        "columns.create.submit",
        "Afegir una columna",
        "Añadir una columna"
    );

    const trigger = form.previousElementSibling;
    if (trigger) trigger.classList.add("add-column-trigger");

    slot.append(...[...panel.childNodes].filter(function (node) {
        return node !== title && node !== form;
    }));
    if (trigger) slot.append(trigger);
    slot.append(form);

    board.append(slot);
    panel.remove();
}

function collapseBoardLabelSettings(layout) {
    const title = layout.querySelector("#labels-title");
    const panel = title ? title.closest(".boards-panel") : null;
    if (!panel) return;

    const details = document.createElement("details");
    details.className = "board-labels-drawer";

    const summary = document.createElement("summary");
    summary.textContent = title.textContent.trim();
    summary.dataset.copyFrom = "labels.title";

    const content = document.createElement("div");
    content.className = "board-labels-drawer__content";

    [...panel.childNodes].forEach(function (node) {
        if (node !== title) content.append(node);
    });

    details.append(summary, content);
    panel.replaceWith(details);
}

function enhanceCard(card) {
    card.classList.add("card-item--enhanced");
    card.tabIndex = 0;
    card.setAttribute("role", "button");

    const title = card.querySelector(".card-title");
    const titleText = title ? title.textContent.trim() : "";
    card.setAttribute(
        "aria-label",
        localizedText(`Obrir targeta: ${titleText}`, `Abrir tarjeta: ${titleText}`)
    );

    const progress = card.querySelector(".checklist-progress");
    if (progress) {
        const meta = document.createElement("span");
        meta.className = "card-checklist-meta";
        meta.textContent = `☑ ${progress.textContent.trim()}`;
        card.append(meta);
    }

    const openButton = document.createElement("button");
    openButton.type = "button";
    openButton.className = "card-open-button";
    openButton.setAttribute(
        "aria-label",
        localizedText(`Obrir targeta: ${titleText}`, `Abrir tarjeta: ${titleText}`)
    );
    openButton.textContent = "↗";
    openButton.addEventListener("click", function (event) {
        event.stopPropagation();
        openCardModal(card);
    });
    card.append(openButton);

    card.addEventListener("click", function (event) {
        if (event.target.closest("button, input, textarea, select, a, summary, form, label")) {
            return;
        }
        openCardModal(card);
    });

    card.addEventListener("keydown", function (event) {
        if (event.target !== card) return;
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            openCardModal(card);
        }
    });
}

function openCardModal(card) {
    closeCardModal();

    const overlay = document.createElement("div");
    overlay.className = "card-modal-backdrop";

    const dialog = document.createElement("section");
    dialog.className = "card-modal";
    dialog.setAttribute("role", "dialog");
    dialog.setAttribute("aria-modal", "true");

    const header = document.createElement("header");
    header.className = "card-modal__header";

    const title = document.createElement("h2");
    title.className = "card-modal__title";
    title.textContent = card.querySelector(".card-title")?.textContent.trim() || "";

    const close = document.createElement("button");
    close.type = "button";
    close.className = "card-modal__close";
    close.setAttribute("aria-label", localizedText("Tancar", "Cerrar"));
    close.textContent = "×";
    close.addEventListener("click", closeCardModal);

    header.append(title, close);

    const body = document.createElement("div");
    body.className = "card-modal__body";

    const main = document.createElement("div");
    main.className = "card-modal__main";

    const labels = card.querySelector(".card-labels");
    if (labels) {
        const labelBlock = cloneFunctionalBlock(labels, "modal-label");
        labelBlock.classList.add("card-modal__labels");
        attachGlobalLabelForm(labelBlock);
        main.append(labelBlock);
    }

    const description = card.querySelector(".card-description");
    if (description) {
        const descriptionBlock = document.createElement("div");
        descriptionBlock.className = "card-modal__description";
        descriptionBlock.append(description.cloneNode(true));
        main.append(descriptionBlock);
    }

    const checklist = card.querySelector(".checklist-section");
    if (checklist) {
        const checklistBlock = cloneFunctionalBlock(checklist, "modal-checklist");
        const details = checklistBlock.querySelector(".checklist-details");
        if (details) details.open = true;
        addChecklistProgressBar(checklistBlock);
        main.append(checklistBlock);
    }

    const actions = document.createElement("aside");
    actions.className = "card-modal__actions";

    const moveActions = card.querySelector(".card-move-actions");
    if (moveActions && moveActions.children.length > 0) {
        const moveMenu = document.createElement("details");
        moveMenu.className = "modal-action-menu";
        const summary = document.createElement("summary");
        summary.textContent = localizedText("↔ Moure", "↔ Mover");
        const content = cloneFunctionalBlock(moveActions, "modal-move");
        moveMenu.append(summary, content);
        actions.append(moveMenu);
    }

    const edit = card.querySelector(".card-edit");
    if (edit) {
        const editClone = cloneFunctionalBlock(edit, "modal-edit");
        editClone.classList.add("modal-edit-action");
        actions.append(editClone);
    }

    const deleteForm = card.querySelector(".card-delete-form");
    if (deleteForm) {
        const deleteClone = cloneFunctionalBlock(deleteForm, "modal-delete");
        deleteClone.classList.add("modal-delete-action");
        actions.append(deleteClone);
    }

    body.append(main, actions);
    dialog.append(header, body);
    overlay.append(dialog);
    document.body.append(overlay);

    activeCardModal = overlay;
    document.body.classList.add("modal-open");

    overlay.addEventListener("click", function (event) {
        if (event.target === overlay) closeCardModal();
    });

    document.addEventListener("keydown", handleModalEscape);
    close.focus();
}

function closeCardModal() {
    if (!activeCardModal) return;
    activeCardModal.remove();
    activeCardModal = null;
    document.body.classList.remove("modal-open");
    document.removeEventListener("keydown", handleModalEscape);
}

function handleModalEscape(event) {
    if (event.key === "Escape") closeCardModal();
}

function cloneFunctionalBlock(source, prefix) {
    const clone = source.cloneNode(true);
    const idMap = new Map();

    clone.querySelectorAll("[id]").forEach(function (element, index) {
        const oldId = element.id;
        const newId = `${prefix}-${index}-${oldId}`;
        idMap.set(oldId, newId);
        element.id = newId;
    });

    clone.querySelectorAll("label[for]").forEach(function (label) {
        const mapped = idMap.get(label.htmlFor);
        if (mapped) label.htmlFor = mapped;
    });

    return clone;
}

function attachGlobalLabelForm(labelBlock) {
    const manager = labelBlock.querySelector(".card-label-manager");
    if (!manager) return;

    const toggleList = manager.querySelector(":scope > .card-label-toggle-list");
    const popover = document.createElement("div");
    popover.className = "card-label-popover";

    if (toggleList) popover.append(toggleList);

    const globalForm = document.querySelector(".board-labels-drawer .board-form");
    if (globalForm) {
        const create = document.createElement("div");
        create.className = "modal-label-create";
        create.append(cloneFunctionalBlock(globalForm, "modal-new-label"));
        popover.append(create);
    }

    if (popover.children.length > 0) manager.append(popover);
}

function addChecklistProgressBar(checklistBlock) {
    const progress = checklistBlock.querySelector(".checklist-progress");
    if (!progress) return;

    const match = progress.textContent.match(/(\d+)%/);
    const percentage = match ? Number(match[1]) : 0;

    const track = document.createElement("div");
    track.className = "checklist-progress-track";
    track.setAttribute("aria-hidden", "true");

    const value = document.createElement("span");
    value.className = "checklist-progress-value";
    value.style.width = `${Math.min(100, Math.max(0, percentage))}%`;

    track.append(value);

    const summary = checklistBlock.querySelector(".checklist-details > summary");
    if (summary) summary.after(track);
}

function firstLetter(value) {
    const trimmed = value.trim();
    return trimmed ? trimmed.charAt(0).toLocaleUpperCase() : "A";
}

function localizedText(ca, es) {
    return rootElement.lang === "es" ? es : ca;
}

async function initializeLanguageSelector() {
    const languageSelector = document.querySelector("#language-selector");

    if (!(languageSelector instanceof HTMLSelectElement)) {
        console.error("No se ha encontrado el selector de idioma.");
        return;
    }

    const preferredLanguage = readPreferredLanguage();

    try {
        const appliedLanguage = await applyLanguageWithFallback(preferredLanguage);
        languageSelector.value = appliedLanguage;
        storePreferredLanguage(appliedLanguage);
        languageSelector.addEventListener("change", handleLanguageChange);
        languageSelector.disabled = false;
    } catch (error) {
        console.error("No se ha podido inicializar el idioma de AulaFlow.", error);
    }
}

async function handleLanguageChange(event) {
    const languageSelector = event.currentTarget;
    if (!(languageSelector instanceof HTMLSelectElement)) return;

    const previousLanguage = readCurrentLanguage();
    const requestedLanguage = languageSelector.value;

    languageSelector.disabled = true;
    languageSelector.setAttribute("aria-busy", "true");

    try {
        const appliedLanguage = await applyLanguageWithFallback(requestedLanguage);
        languageSelector.value = appliedLanguage;
        storePreferredLanguage(appliedLanguage);
    } catch (error) {
        languageSelector.value = previousLanguage;
        console.error("No se ha podido cambiar el idioma de AulaFlow.", error);
    } finally {
        languageSelector.disabled = false;
        languageSelector.removeAttribute("aria-busy");
        languageSelector.focus();
    }
}

function readCurrentLanguage() {
    const currentLanguage = rootElement.lang;
    if (SUPPORTED_LANGUAGES.has(currentLanguage)) return currentLanguage;
    return DEFAULT_LANGUAGE;
}

async function applyLanguageWithFallback(requestedLanguage) {
    try {
        await applyLanguage(requestedLanguage);
        return requestedLanguage;
    } catch (error) {
        if (requestedLanguage === DEFAULT_LANGUAGE) throw error;

        console.warn(
            "No se ha podido cargar el idioma solicitado. Se aplicará el idioma de reserva.",
            error
        );

        await applyLanguage(DEFAULT_LANGUAGE);
        return DEFAULT_LANGUAGE;
    }
}

async function applyLanguage(language) {
    validateLanguage(language);
    const catalog = await loadCatalog(language);
    rootElement.lang = language;
    applyTranslations(catalog);
}

function validateLanguage(language) {
    if (!SUPPORTED_LANGUAGES.has(language)) {
        throw new RangeError("Idioma no soportado: " + language);
    }
}

async function loadCatalog(language) {
    const response = await fetch(`${CATALOG_BASE_PATH}/${language}.json`, {
        headers: {Accept: "application/json"}
    });

    if (!response.ok) {
        throw new Error(
            "No se ha podido cargar el catálogo "
            + language
            + ". Estado HTTP: "
            + response.status
        );
    }

    return response.json();
}

function applyTranslations(catalog) {
    currentCatalog = catalog;

    const translationElements = document.querySelectorAll("[data-i18n]");

    for (const element of translationElements) {
        const translationKey = element.dataset.i18n;
        const translatedValue = readTranslation(catalog, translationKey);
        const attributeName = element.dataset.i18nAttribute;

        if (attributeName) {
            element.setAttribute(attributeName, translatedValue);
            continue;
        }

        if (element.matches(".card-label-toggle-form button[data-i18n]")) {
            applyDynamicLabelButtonTranslation(element, translatedValue);
            continue;
        }

        element.textContent = translatedValue;
    }

    applyClientUiTranslations(catalog);
}

function applyClientUiTranslations(catalog) {
    document.querySelectorAll("[data-ui-i18n]").forEach(function (element) {
        const key = element.dataset.uiI18n;
        const fallback = rootElement.lang === "es"
            ? element.dataset.fallbackEs
            : element.dataset.fallbackCa;
        const value = typeof catalog[key] === "string" ? catalog[key] : fallback;
        element.textContent = `＋ ${value}`;
    });

    document.querySelectorAll("[data-placeholder-i18n]").forEach(function (input) {
        const key = input.dataset.placeholderI18n;
        if (key && typeof catalog[key] === "string") input.placeholder = catalog[key];
    });

    document.querySelectorAll("[data-local-ca][data-local-es]").forEach(function (element) {
        element.textContent = localizedText(element.dataset.localCa, element.dataset.localEs);
    });

    document.querySelectorAll("[data-copy-from]").forEach(function (element) {
        const key = element.dataset.copyFrom;
        if (typeof catalog[key] === "string") element.textContent = catalog[key];
    });
}

function applyDynamicLabelButtonTranslation(button, translatedAction) {
    const storedLabelName = button.dataset.labelName;
    const labelName = storedLabelName ?? button.textContent.trim();
    button.dataset.labelName = labelName;
    button.textContent = `${translatedAction}: ${labelName}`;
}

function readTranslation(catalog, translationKey) {
    const translatedValue = catalog[translationKey];
    if (typeof translatedValue !== "string") {
        throw new Error("No existe la traducción: " + translationKey);
    }
    return translatedValue;
}

function readPreferredLanguage() {
    try {
        const storedLanguage = localStorage.getItem(LANGUAGE_STORAGE_KEY);
        if (storedLanguage === null) return DEFAULT_LANGUAGE;
        if (SUPPORTED_LANGUAGES.has(storedLanguage)) return storedLanguage;

        localStorage.removeItem(LANGUAGE_STORAGE_KEY);
        console.warn(
            "Se ha descartado una preferencia de idioma no soportada: "
            + storedLanguage
        );
    } catch (error) {
        console.warn("No se ha podido leer la preferencia de idioma.", error);
    }

    return DEFAULT_LANGUAGE;
}

function storePreferredLanguage(language) {
    try {
        localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    } catch (error) {
        console.warn("No se ha podido guardar la preferencia de idioma.", error);
    }
}

function translate(key, fallback) {
    if (currentCatalog && typeof currentCatalog[key] === "string") {
        return currentCatalog[key];
    }
    return fallback;
}

function readCsrfToken() {
    const input = document.querySelector('input[name="_csrf"]');
    return input ? input.value : null;
}

function boardIdFromUrl() {
    const match = window.location.pathname.match(/\/boards\/(\d+)/);
    return match ? match[1] : null;
}

function initializeDragAndDrop() {
    const boardId = boardIdFromUrl();
    if (!boardId) return;

    const statusEl = document.querySelector(".dnd-status");
    const cardItems = document.querySelectorAll(".card-item");
    if (cardItems.length === 0) return;

    cardItems.forEach(function (card) {
        card.draggable = true;
        card.addEventListener("dragstart", onDragStart);
        card.addEventListener("dragend", onDragEnd);
    });

    document.querySelectorAll(".kanban-column").forEach(function (column) {
        column.addEventListener("dragover", onDragOver);
        column.addEventListener("dragleave", onDragLeave);
        column.addEventListener("drop", function (event) {
            onDrop(event, boardId, statusEl);
        });
    });
}

function onDragStart(event) {
    const card = event.currentTarget;
    draggedCardId = card.dataset.cardId;
    event.dataTransfer.setData("text/plain", draggedCardId);
    event.dataTransfer.effectAllowed = "move";
    card.classList.add("card-dragging");
}

function onDragEnd(event) {
    event.currentTarget.classList.remove("card-dragging");
    clearDropTargets();
    draggedCardId = null;
}

function onDragOver(event) {
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    const column = event.target.closest(".kanban-column");
    if (column) {
        clearDropTargets(column);
        column.classList.add("kanban-column--drop-target");
    }
}

function onDragLeave(event) {
    const column = event.currentTarget;
    if (!column.contains(event.relatedTarget)) {
        column.classList.remove("kanban-column--drop-target");
    }
}

function clearDropTargets(except) {
    document.querySelectorAll(".kanban-column--drop-target").forEach(function (column) {
        if (column !== except) column.classList.remove("kanban-column--drop-target");
    });
}

async function onDrop(event, boardId, statusEl) {
    event.preventDefault();

    const cardId = event.dataTransfer.getData("text/plain");
    if (!cardId) return;

    const targetColumn = event.target.closest(".kanban-column");
    if (!targetColumn) return;

    targetColumn.classList.remove("kanban-column--drop-target");

    const targetColumnId = targetColumn.dataset.columnId;
    if (!targetColumnId) return;

    const cardList = targetColumn.querySelector(".card-list");
    const items = cardList ? [...cardList.querySelectorAll(".card-item")] : [];

    const dropY = event.clientY;
    let targetPosition = 0;

    for (const item of items) {
        if (item.dataset.cardId === cardId) continue;
        const rect = item.getBoundingClientRect();
        if (rect.top + rect.height / 2 < dropY) targetPosition++;
    }

    const csrfToken = readCsrfToken();
    if (!csrfToken) return;

    setDndStatus(statusEl, translate("cards.dnd.moving", "Movent targeta..."));

    try {
        const response = await fetch(
            `/api/v1/boards/${boardId}/cards/${cardId}/position`,
            {
                method: "PUT",
                credentials: "same-origin",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                    "X-CSRF-Token": csrfToken
                },
                body: new URLSearchParams({
                    targetColumnId: targetColumnId,
                    targetPosition: String(targetPosition)
                }).toString()
            }
        );

        if (response.ok) {
            setDndStatus(
                statusEl,
                translate("cards.dnd.moved", "Targeta moguda correctament.")
            );
            window.location.reload();
        } else {
            setDndStatus(
                statusEl,
                translate("cards.dnd.error", "No s'ha pogut moure la targeta.")
            );
        }
    } catch (error) {
        console.error("Error al moure la targeta.", error);
        setDndStatus(
            statusEl,
            translate("cards.dnd.error", "No s'ha pogut moure la targeta.")
        );
    }
}

function setDndStatus(element, message) {
    if (!element) return;
    element.textContent = message;
    element.classList.add("is-active");
    window.setTimeout(function () {
        element.classList.remove("is-active");
    }, 2600);
}
