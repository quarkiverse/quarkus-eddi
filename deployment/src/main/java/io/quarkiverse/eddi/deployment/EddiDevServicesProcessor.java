package io.quarkiverse.eddi.deployment;

import java.io.Closeable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

/**
 * Dev Services processor that auto-starts EDDI v6 + MongoDB containers via
 * Testcontainers in dev/test mode with zero configuration.
 * <p>
 * Implements the Quarkus Dev Services lifecycle pattern with:
 * <ul>
 * <li>Container reuse across live reloads via static state tracking</li>
 * <li>Shared containers support via {@code serviceName} config</li>
 * <li>Proper shutdown via {@link CuratedApplicationShutdownBuildItem}</li>
 * </ul>
 *
 * @see EddiDevServicesBuildTimeConfig
 */
public class EddiDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(EddiDevServicesProcessor.class);
    private static final int EDDI_PORT = 7070;
    private static final String MONGO_NETWORK_ALIAS = "mongodb";
    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-eddi";

    // Static state survives live reloads
    private static volatile RunningEddiDevService runningService;

    @BuildStep(onlyIfNot = IsNormal.class)
    DevServicesResultBuildItem startDevServices(LaunchModeBuildItem launchMode,
            CuratedApplicationShutdownBuildItem shutdown) {

        if (launchMode.getLaunchMode() == LaunchMode.NORMAL) {
            return null;
        }

        // Read build-time config via ConfigProvider
        var config = ConfigProvider.getConfig();
        boolean enabled = config.getOptionalValue("quarkus.eddi.devservices.enabled", Boolean.class)
                .orElse(true);
        String eddiImage = config.getOptionalValue("quarkus.eddi.devservices.image", String.class)
                .orElse("labsai/eddi:6");
        String mongoImage = config.getOptionalValue("quarkus.eddi.devservices.mongodb-image", String.class)
                .orElse("mongo:6.0");
        boolean shared = config.getOptionalValue("quarkus.eddi.devservices.shared", Boolean.class)
                .orElse(true);
        String serviceName = config.getOptionalValue("quarkus.eddi.devservices.service-name", String.class)
                .orElse("eddi");

        if (!enabled) {
            LOG.debug("EDDI Dev Services disabled via config");
            return null;
        }

        // Skip if explicit URL is configured
        var explicitUrl = config.getOptionalValue("quarkus.rest-client.eddi.url", String.class);
        if (explicitUrl.isPresent() && !explicitUrl.get().contains("${")) {
            LOG.debugf("EDDI URL explicitly configured (%s), skipping Dev Services", explicitUrl.get());
            return null;
        }

        // Reuse existing containers if they are still running and config hasn't changed
        if (runningService != null && runningService.isRunning()
                && eddiImage.equals(runningService.eddiImage)
                && mongoImage.equals(runningService.mongoImage)) {
            LOG.debug("Reusing existing EDDI Dev Services containers");
            return runningService.toBuildItem();
        }

        // Clean up previous containers if config changed
        shutdownContainers();

        LOG.infof("Starting EDDI Dev Services (eddi=%s, mongo=%s, shared=%s)", eddiImage, mongoImage, shared);

        Network network = null;
        MongoDBContainer mongodb = null;
        GenericContainer<?> eddi = null;

        try {
            network = Network.newNetwork();
            final Network networkRef = network;

            // Start MongoDB
            mongodb = new MongoDBContainer(DockerImageName.parse(mongoImage))
                    .withNetwork(network)
                    .withNetworkAliases(MONGO_NETWORK_ALIAS);
            if (shared) {
                mongodb.withLabel(DEV_SERVICE_LABEL, serviceName + "-mongo");
                mongodb.withReuse(true);
            }
            mongodb.start();
            LOG.infof("EDDI Dev Services: MongoDB started at %s", mongodb.getConnectionString());

            // Build EDDI's MongoDB connection string pointing at the container
            String mongoConnectionString = "mongodb://" + MONGO_NETWORK_ALIAS
                    + ":27017/eddi?retryWrites=true&w=majority&connectTimeoutMS=10000&socketTimeoutMS=30000";

            // Start EDDI v6
            eddi = new GenericContainer<>(DockerImageName.parse(eddiImage))
                    .withNetwork(network)
                    .withExposedPorts(EDDI_PORT)
                    .withEnv("MONGODB_CONNECTIONSTRING", mongoConnectionString)
                    .withEnv("EDDI_VAULT_MASTER_KEY", "dev-services-key")
                    .waitingFor(Wait.forHttp("/q/health/ready")
                            .forPort(EDDI_PORT)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));
            if (shared) {
                eddi.withLabel(DEV_SERVICE_LABEL, serviceName + "-eddi");
                eddi.withReuse(true);
            }
            eddi.start();

            String eddiUrl = "http://" + eddi.getHost() + ":" + eddi.getMappedPort(EDDI_PORT);
            LOG.infof("EDDI Dev Services: EDDI started at %s", eddiUrl);

            // Auto-configure the REST client to point at the running container
            Map<String, String> configOverrides = new HashMap<>();
            configOverrides.put("quarkus.eddi.url", eddiUrl);
            configOverrides.put("quarkus.rest-client.eddi.url", eddiUrl);

            // Capture refs for cleanup
            final MongoDBContainer mongoRef = mongodb;
            final GenericContainer<?> eddiRef = eddi;

            Closeable closeAll = () -> {
                closeQuietly(eddiRef);
                closeQuietly(mongoRef);
                closeQuietly(networkRef);
            };

            // Store state for reuse across live reloads
            runningService = new RunningEddiDevService(eddiImage, mongoImage, eddiUrl,
                    eddi.getContainerId(), configOverrides, closeAll);

            // Register shutdown handler for application shutdown
            shutdown.addCloseTask(EddiDevServicesProcessor::shutdownContainers, true);

            return runningService.toBuildItem();

        } catch (Exception e) {
            LOG.warn("EDDI Dev Services failed to start. "
                    + "Make sure Docker is running. Falling back to manual configuration.", e);
            // Clean up partial resources
            closeQuietly(eddi);
            closeQuietly(mongodb);
            closeQuietly(network);
            return null;
        }
    }

    private static synchronized void shutdownContainers() {
        if (runningService != null) {
            LOG.info("Stopping EDDI Dev Services containers");
            runningService.close();
            runningService = null;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.debugf("Error closing resource: %s", e.getMessage());
            }
        }
    }

    /**
     * Holds the running container state for reuse across live reloads.
     */
    private static class RunningEddiDevService {
        final String eddiImage;
        final String mongoImage;
        final String eddiUrl;
        final String containerId;
        final Map<String, String> configOverrides;
        final Closeable closeable;
        private volatile boolean closed = false;

        RunningEddiDevService(String eddiImage, String mongoImage, String eddiUrl,
                String containerId, Map<String, String> configOverrides, Closeable closeable) {
            this.eddiImage = eddiImage;
            this.mongoImage = mongoImage;
            this.eddiUrl = eddiUrl;
            this.containerId = containerId;
            this.configOverrides = configOverrides;
            this.closeable = closeable;
        }

        boolean isRunning() {
            return !closed;
        }

        DevServicesResultBuildItem toBuildItem() {
            return new DevServicesResultBuildItem.RunningDevService(
                    "eddi",
                    containerId,
                    closeable,
                    configOverrides)
                    .toBuildItem();
        }

        void close() {
            if (!closed) {
                closed = true;
                try {
                    closeable.close();
                } catch (Exception e) {
                    LOG.warn("Error stopping EDDI Dev Services", e);
                }
            }
        }
    }
}
