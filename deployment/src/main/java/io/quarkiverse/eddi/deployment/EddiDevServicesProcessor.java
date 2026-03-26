package io.quarkiverse.eddi.deployment;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Dev Services processor that auto-starts EDDI + MongoDB during dev/test mode.
 * <p>
 * When {@code quarkus.eddi.devservices.enabled=true} (default in dev mode),
 * this processor starts a MongoDB container and an EDDI container, wiring them
 * together and auto-configuring {@code quarkus.eddi.url}.
 * <p>
 * EDDI uses {@code mongodb.connectionString} (a custom property exposed via
 * its Docker image as env var {@code MONGODB_CONNECTIONSTRING}).
 */
public class EddiDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(EddiDevServicesProcessor.class);
    private static final int EDDI_PORT = 7070;
    private static final String MONGO_NETWORK_ALIAS = "mongodb";

    @BuildStep(onlyIfNot = IsNormal.class)
    DevServicesResultBuildItem startDevServices(LaunchModeBuildItem launchMode) {

        if (launchMode.getLaunchMode() == LaunchMode.NORMAL) {
            return null;
        }

        // TODO: Read EddiConfig.devservices().enabled() via build-time config injection
        // For now, always start in dev/test mode

        LOG.info("Starting EDDI Dev Services...");

        try {
            Network network = Network.newNetwork();

            // Start MongoDB
            MongoDBContainer mongodb = new MongoDBContainer(
                    DockerImageName.parse("mongo:6.0"))
                    .withNetwork(network)
                    .withNetworkAliases(MONGO_NETWORK_ALIAS)
                    .withReuse(true);
            mongodb.start();
            LOG.infof("EDDI Dev Services: MongoDB started at %s", mongodb.getConnectionString());

            // Build EDDI's MongoDB connection string pointing at the container
            // EDDI's application.properties uses: mongodb.connectionString=mongodb://mongodb:27017/eddi?...
            String mongoConnectionString = "mongodb://" + MONGO_NETWORK_ALIAS
                    + ":27017/eddi?retryWrites=true&w=majority&connectTimeoutMS=10000&socketTimeoutMS=30000";

            // Start EDDI
            GenericContainer<?> eddi = new GenericContainer<>(
                    DockerImageName.parse("labsai/eddi:6"))
                    .withNetwork(network)
                    .withExposedPorts(EDDI_PORT)
                    .withEnv("MONGODB_CONNECTIONSTRING", mongoConnectionString)
                    .withEnv("EDDI_VAULT_MASTER_KEY", "dev-services-key")
                    .waitingFor(Wait.forHttp("/q/health/ready")
                            .forPort(EDDI_PORT)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(2)))
                    .withReuse(true);
            eddi.start();

            String eddiUrl = "http://" + eddi.getHost() + ":" + eddi.getMappedPort(EDDI_PORT);
            LOG.infof("EDDI Dev Services: EDDI started at %s", eddiUrl);

            // Auto-configure the REST client to point at the running container
            Map<String, String> configOverrides = new HashMap<>();
            configOverrides.put("quarkus.eddi.url", eddiUrl);
            configOverrides.put("quarkus.rest-client.eddi.url", eddiUrl);

            return new DevServicesResultBuildItem.RunningDevService(
                    "eddi",
                    eddi.getContainerId(),
                    eddi::close,
                    configOverrides)
                    .toBuildItem();

        } catch (Exception e) {
            LOG.warn("EDDI Dev Services failed to start. "
                    + "Make sure Docker is running. Falling back to manual configuration.", e);
            return null;
        }
    }
}
