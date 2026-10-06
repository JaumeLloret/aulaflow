package es.aulaflow.infrastructure.http;

import es.aulaflow.shared.config.ApplicationConfig;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AulaFlowHttpServer implements AutoCloseable {
    private static final int SYSTEM_DEFAULT_BACKLOG = 0;
    private static final int SHUTDOWN_DELAY_SECONDS = 5;

    private final HttpServer server;
    private final ExecutorService requestExecutor;

    private boolean started;
    private boolean closed;

    private AulaFlowHttpServer(
            HttpServer server,
            ExecutorService requestExecutor
    ) {
        this.server = server;
        this.requestExecutor = requestExecutor;
    }

    public static AulaFlowHttpServer create(
            ApplicationConfig config
    ) throws IOException {
        Objects.requireNonNull(
                config,
                "La configuración no puede ser null."
        );

        InetSocketAddress address = new InetSocketAddress(
                config.getHttpHost(),
                config.getHttpPort()
        );

        return create(address);
    }

    static AulaFlowHttpServer create(
            InetSocketAddress address
    ) throws IOException {
        Objects.requireNonNull(
                address,
                "La dirección HTTP no puede ser null."
        );

        HttpServer server = HttpServer.create(
                address,
                SYSTEM_DEFAULT_BACKLOG
        );

        ExecutorService requestExecutor =
                Executors.newVirtualThreadPerTaskExecutor();

        server.setExecutor(requestExecutor);

        return new AulaFlowHttpServer(
                server,
                requestExecutor
        );
    }

    public void registerContext(
            String path,
            HttpHandler handler
    ) {
        ensureNotClosed();

        if (started) {
            throw new IllegalStateException(
                    "No se pueden registrar rutas después de iniciar el servidor."
            );
        }

        Objects.requireNonNull(
                path,
                "La ruta HTTP no puede ser null."
        );

        Objects.requireNonNull(
                handler,
                "El manejador HTTP no puede ser null."
        );

        if (path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Una ruta HTTP debe comenzar por '/'. "
                            + "Valor recibido: "
                            + path
            );
        }

        server.createContext(path, handler);
    }

    public void start() {
        ensureNotClosed();

        if (started) {
            throw new IllegalStateException(
                    "El servidor HTTP ya está iniciado."
            );
        }

        server.start();
        started = true;
    }

    public InetSocketAddress getAddress() {
        return server.getAddress();
    }

    public boolean isStarted() {
        return started && !closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        server.stop(SHUTDOWN_DELAY_SECONDS);
        requestExecutor.close();

        closed = true;
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException(
                    "Un servidor HTTP detenido no puede reiniciarse."
            );
        }
    }
}
