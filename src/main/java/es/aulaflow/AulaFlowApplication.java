package es.aulaflow;

import es.aulaflow.application.auth.ProvisionInitialAdministrator;
import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.application.board.BoardService;
import es.aulaflow.infrastructure.auth.InMemoryLoginAttemptLimiter;
import es.aulaflow.infrastructure.auth.InMemorySessionStore;
import es.aulaflow.infrastructure.http.AulaFlowHttpServer;
import es.aulaflow.infrastructure.auth.InitialAdministratorConfig;
import es.aulaflow.infrastructure.auth.SessionCookieConfig;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConfig;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteAdministratorRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConnectionFactory;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteMigrator;
import es.aulaflow.application.board.CardService;
import es.aulaflow.application.board.ChecklistService;
import es.aulaflow.application.board.LabelService;
import es.aulaflow.application.csv.CsvExportService;
import es.aulaflow.application.csv.CsvImportService;
import es.aulaflow.infrastructure.csv.InMemoryPendingImportStore;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteBoardRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteCardRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteChecklistItemRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteCsvImportRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteLabelRepository;
import es.aulaflow.infrastructure.security.Pbkdf2PasswordHasher;
import es.aulaflow.infrastructure.security.SecureRandomSessionIdGenerator;
import es.aulaflow.infrastructure.security.SecureRandomCsrfTokenGenerator;
import es.aulaflow.presentation.auth.AccountHandler;
import es.aulaflow.presentation.auth.ApiAuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.LoginHandler;
import es.aulaflow.presentation.auth.LogoutHandler;
import es.aulaflow.presentation.auth.SessionCookie;
import es.aulaflow.presentation.health.HealthHandler;
import es.aulaflow.presentation.board.BoardApiHandler;
import es.aulaflow.presentation.board.BoardHtmlHandler;
import es.aulaflow.presentation.csv.CsvImportApiHandler;
import es.aulaflow.presentation.csv.CsvImportHtmlHandler;
import es.aulaflow.presentation.http.staticcontent.StaticResourceHandler;
import es.aulaflow.shared.config.ApplicationConfig;
import es.aulaflow.presentation.http.HttpHandlerPipeline;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class AulaFlowApplication {

    private static final String APPLICATION_VERSION =
            "1.0.0";

    private AulaFlowApplication() {
    }

    public static void main(String[] args) {
        try {
            ApplicationConfig config =
                    ApplicationConfig.fromEnvironment();

            ApplicationRuntime applicationRuntime =
                    initializePersistenceAndAuthentication();

            AulaFlowHttpServer httpServer =
                    AulaFlowHttpServer.create(config);

            registerHttpContexts(
                    httpServer,
                    config,
                    applicationRuntime
            );

            registerShutdownHook(httpServer);

            httpServer.start();

            showStartupInformation(
                    config,
                    httpServer.getAddress()
            );
        } catch (IOException | RuntimeException exception) {
            showStartupError(exception);
            System.exit(1);
        }
    }

    private static ApplicationRuntime
    initializePersistenceAndAuthentication() {
        SqliteConfig sqliteConfig =
                SqliteConfig.fromEnvironment();

        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(
                        sqliteConfig
                );

        SqliteMigrator migrator =
                new SqliteMigrator(
                        connectionFactory
                );

        migrator.migrate();

        SqliteAdministratorRepository repository =
                new SqliteAdministratorRepository(
                        connectionFactory
                );

        Pbkdf2PasswordHasher passwordHasher =
                new Pbkdf2PasswordHasher();

        if (!repository.exists()) {
            try (
                    InitialAdministratorConfig config =
                            InitialAdministratorConfig
                                    .fromEnvironment()
            ) {
                ProvisionInitialAdministrator provisioner =
                        new ProvisionInitialAdministrator(
                                repository,
                                passwordHasher
                        );

                provisioner.execute(
                        config.username(),
                        config.copyPassword()
                );
            }
        }

        AuthenticationService authenticationService =
                new AuthenticationService(
                        repository,
                        passwordHasher,
                        new InMemorySessionStore(),
                        new SecureRandomSessionIdGenerator(),
                        new SecureRandomCsrfTokenGenerator(),
                        new InMemoryLoginAttemptLimiter()
                );

        SessionCookieConfig cookieConfig =
                SessionCookieConfig.fromEnvironment();

        BoardService boardService =
                new BoardService(
                        new SqliteBoardRepository(
                                connectionFactory
                        )
                );

        CardService cardService =
                new CardService(
                        new SqliteCardRepository(
                                connectionFactory
                        )
                );

        LabelService labelService =
                new LabelService(
                        new SqliteLabelRepository(
                                connectionFactory
                        )
                );

        ChecklistService checklistService =
                new ChecklistService(
                        new SqliteChecklistItemRepository(
                                connectionFactory
                        )
                );

        CsvExportService csvExportService =
                new CsvExportService(
                        boardService,
                        cardService
                );

        CsvImportService csvImportService =
                new CsvImportService(
                        new InMemoryPendingImportStore(),
                        new SqliteCsvImportRepository(
                                connectionFactory
                        )
                );

        return new ApplicationRuntime(
                authenticationService,
                new SessionCookie(cookieConfig.isSecure()),
                boardService,
                cardService,
                labelService,
                checklistService,
                csvExportService,
                csvImportService
        );
    }

    private static void registerHttpContexts(
            AulaFlowHttpServer httpServer,
            ApplicationConfig config,
            ApplicationRuntime applicationRuntime
    ) {
        HealthHandler healthHandler =
                new HealthHandler(
                        APPLICATION_VERSION,
                        config.getEnvironment()
                );

        httpServer.registerContext(
                HealthHandler.PATH,
                HttpHandlerPipeline.standard(
                        healthHandler
                )
        );

        StaticResourceHandler staticResourceHandler =
                new StaticResourceHandler();

        httpServer.registerContext(
                StaticResourceHandler.PATH,
                HttpHandlerPipeline.standard(
                        staticResourceHandler
                )
        );

        LoginHandler loginHandler =
                new LoginHandler(
                        applicationRuntime
                                .authenticationService(),
                        applicationRuntime
                                .sessionCookie()
                );

        httpServer.registerContext(
                LoginHandler.PATH,
                HttpHandlerPipeline.standard(
                        loginHandler
                )
        );

        AccountHandler accountHandler =
                new AccountHandler();

        AuthenticationRequiredHandler
                protectedAccountHandler =
                new AuthenticationRequiredHandler(
                        AccountHandler.PATH,
                        applicationRuntime
                                .authenticationService(),
                        applicationRuntime
                                .sessionCookie(),
                        accountHandler
                );

        httpServer.registerContext(
                AccountHandler.PATH,
                HttpHandlerPipeline.standard(
                        protectedAccountHandler
                )
        );

        LogoutHandler logoutHandler =
                new LogoutHandler(
                        applicationRuntime
                                .authenticationService(),
                        applicationRuntime
                                .sessionCookie()
                );

        httpServer.registerContext(
                LogoutHandler.PATH,
                HttpHandlerPipeline.standard(
                        logoutHandler
                )
        );

        BoardHtmlHandler boardHtmlHandler =
                new BoardHtmlHandler(
                        applicationRuntime.boardService(),
                        applicationRuntime.cardService(),
                        applicationRuntime.labelService(),
                        applicationRuntime.checklistService(),
                        applicationRuntime.csvExportService()
                );

        httpServer.registerContext(
                BoardHtmlHandler.PATH,
                HttpHandlerPipeline.standard(
                        new AuthenticationRequiredHandler(
                                BoardHtmlHandler.PATH,
                                true,
                                applicationRuntime
                                        .authenticationService(),
                                applicationRuntime
                                        .sessionCookie(),
                                boardHtmlHandler
                        )
                )
        );

        CsvImportHtmlHandler csvImportHtmlHandler =
                new CsvImportHtmlHandler(
                        applicationRuntime
                                .csvImportService()
                );

        httpServer.registerContext(
                CsvImportHtmlHandler.PATH,
                HttpHandlerPipeline.standard(
                        new AuthenticationRequiredHandler(
                                CsvImportHtmlHandler.PATH,
                                true,
                                applicationRuntime
                                        .authenticationService(),
                                applicationRuntime
                                        .sessionCookie(),
                                csvImportHtmlHandler
                        )
                )
        );

        BoardApiHandler boardApiHandler =
                new BoardApiHandler(
                        applicationRuntime.boardService(),
                        applicationRuntime.cardService(),
                        applicationRuntime.labelService(),
                        applicationRuntime.checklistService(),
                        applicationRuntime.csvExportService()
                );

        httpServer.registerContext(
                BoardApiHandler.PATH,
                HttpHandlerPipeline.standard(
                        new ApiAuthenticationRequiredHandler(
                                BoardApiHandler.PATH,
                                applicationRuntime
                                        .authenticationService(),
                                applicationRuntime
                                        .sessionCookie(),
                                boardApiHandler
                        )
                )
        );

        CsvImportApiHandler csvImportApiHandler =
                new CsvImportApiHandler(
                        applicationRuntime
                                .csvImportService(),
                        applicationRuntime.boardService()
                );

        httpServer.registerContext(
                CsvImportApiHandler.PATH,
                HttpHandlerPipeline.standard(
                        new ApiAuthenticationRequiredHandler(
                                CsvImportApiHandler.PATH,
                                applicationRuntime
                                        .authenticationService(),
                                applicationRuntime
                                        .sessionCookie(),
                                csvImportApiHandler
                        )
                )
        );
    }

    private static void registerShutdownHook(
            AulaFlowHttpServer httpServer
    ) {
        Thread shutdownThread = new Thread(
                () -> {
                    System.out.println();
                    System.out.println(
                            "Deteniendo AulaFlow..."
                    );

                    httpServer.close();

                    System.out.println(
                            "AulaFlow se ha detenido correctamente."
                    );
                },
                "aulaflow-shutdown"
        );

        Runtime.getRuntime().addShutdownHook(
                shutdownThread
        );
    }

    private static void showStartupInformation(
            ApplicationConfig config,
            InetSocketAddress address
    ) {
        System.out.println(
                "AulaFlow " + APPLICATION_VERSION
        );

        System.out.println(
                "Estado: servidor iniciado"
        );

        System.out.println(
                "Entorno: " + config.getEnvironment()
        );

        System.out.println(
                "Dirección: http://"
                        + address.getHostString()
                        + ":"
                        + address.getPort()
        );

        System.out.println(
                "Health: http://"
                        + address.getHostString()
                        + ":"
                        + address.getPort()
                        + HealthHandler.PATH
        );

        System.out.println(
                "Detención: Ctrl+C en terminal o finaliza "
                        + "el proceso desde el entorno de ejecución."
        );
    }

    private static void showStartupError(
            Exception exception
    ) {
        System.err.println(
                "No se ha podido iniciar AulaFlow."
        );

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            System.err.println(
                    "Motivo: error interno de inicialización."
            );
        } else {
            System.err.println(
                    "Motivo: " + message
            );
        }

        System.err.println(
                "Tipo: "
                        + exception
                                .getClass()
                                .getSimpleName()
        );
    }

    private record ApplicationRuntime(
            AuthenticationService authenticationService,
            SessionCookie sessionCookie,
            BoardService boardService,
            CardService cardService,
            LabelService labelService,
            ChecklistService checklistService,
            CsvExportService csvExportService,
            CsvImportService csvImportService
    ) {
    }

}
