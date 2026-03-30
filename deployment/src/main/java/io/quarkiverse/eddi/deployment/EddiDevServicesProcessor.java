package io.quarkiverse.eddi.deployment;

import java.io.Closeable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

/**
 * Dev Services processor that auto-starts EDDI v6 + MongoDB during dev/test mode.
 * <p>
 * When {@code quarkus.eddi.devservices.enabled=true} (default in dev mode),
 * this processor starts a MongoDB container and an EDDI container, wiring them
 * together and auto-configuring {@code quarkus.eddi.url}.
 * <p>
 * EDDI uses {@code MONGODB_CONNECTIONSTRING} env var for MongoDB connectivity.
 */
public class EddiDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(EddiDevServicesProcessor.class);
    private static final int EDDI_PORT = 7070;
    private static final String MONGO_NETWORK_ALIAS = "mongodb";

    @BuildStep(onlyIfNot = IsNormal.class)
    DevServicesResultBuildItem startDevServices(LaunchModeBuildItem launchMode,
            EddiDevServicesBuildTimeConfig buildTimeConfig) {

        if (launchMode.getLaunchMode() == LaunchMode.NORMAL) {
            return null;
        }

        // Respect the enabled flag
        if (!buildTimeConfig.enabled()) {
            LOG.debug("EDDI Dev Services disabled via config");
            return null;
        }

        LOG.info("Starting EDDI Dev Services...");

        Network network = null;
        MongoDBContainer mongodb = null;
        GenericContainer<?> eddi = null;

        try {
            network = Network.newNetwork();
            final Network networkRef = network;

            // Start MongoDB
            mongodb = new MongoDBContainer(
                    DockerImageName.parse(buildTimeConfig.mongodbImage()))
                    .withNetwork(network)
                    .withNetworkAliases(MONGO_NETWORK_ALIAS);
            mongodb.start();
            LOG.infof("EDDI Dev Services: MongoDB started at %s", mongodb.getConnectionString());

            // Build EDDI's MongoDB connection string pointing at the container
            String mongoConnectionString = "mongodb://" + MONGO_NETWORK_ALIAS
                    + ":27017/eddi?retryWrites=true&w=majority&connectTimeoutMS=10000&socketTimeoutMS=30000";

            // Start EDDI v6
            eddi = new GenericContainer<>(
                    DockerImageName.parse(buildTimeConfig.image()))
                    .withNetwork(network)
                    .withExposedPorts(EDDI_PORT)
                    .withEnv("MONGODB_CONNECTIONSTRING", mongoConnectionString)
                    .withEnv("EDDI_VAULT_MASTER_KEY", "dev-services-key")
                    .waitingFor(Wait.forHttp("/q/health/ready")
                            .forPort(EDDI_PORT)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(2)));
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

            return new DevServicesResultBuildItem.RunningDevService(
                    "eddi",
                    eddi.getContainerId(),
                    closeAll,
                    configOverrides)
                    .toBuildItem();

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

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.debugf("Error closing resource: %s", e.getMessage());
            }
        }
    }
}
