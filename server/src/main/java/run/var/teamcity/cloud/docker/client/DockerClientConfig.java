package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class DockerClientConfig {

    private final static int DEFAULT_CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(1);

    private final URI instanceURI;
    private boolean usingTLS = false;
    private int threadPoolSize = 1;
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;

    public DockerClientConfig(@NotNull URI instanceURI) {
        DockerCloudUtils.requireNonNull(instanceURI, "Docker instance URI cannot be null.");
        this.instanceURI = instanceURI;
    }

    public DockerClientConfig usingTls(boolean usingTls) {
        this.usingTLS = usingTls;
        return this;
    }

    public DockerClientConfig threadPoolSize(int threadPoolSize) {
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Invalid thread pool size: " + threadPoolSize);
        }
        this.threadPoolSize = threadPoolSize;
        return this;
    }

    public DockerClientConfig connectTimeoutMillis(int connectTimeoutMillis) {
        if (connectTimeoutMillis < 0) {
            throw new IllegalArgumentException("Timeout specification must be positive: " + connectTimeoutMillis +
                    ". Use 0 for no timeout.");
        }

        this.connectTimeoutMillis = connectTimeoutMillis;
        return this;
    }

    @NotNull
    public URI getInstanceURI() {
        return instanceURI;
    }

    public boolean isUsingTLS() {
        return usingTLS;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }
}