package io.quarkiverse.eddi.deployment;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.Startable;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.smallrye.config.Expressions;

/**
 * Dev Services processor that starts EDDI + MongoDB via Testcontainers in dev and
 * test mode with zero configuration.
 * <p>
 * Uses Quarkus' owned Dev Services API: Quarkus starts the containers, keeps them
 * across live reloads while the configuration is unchanged, and stops them on
 * shutdown. The connection URL is exposed as {@code quarkus.rest-client.eddi.url}
 * and {@code quarkus.eddi.url}.
 *
 * @see EddiDevServicesBuildTimeConfig
 */
public class EddiDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(EddiDevServicesProcessor.class);

    static final String FEATURE = "eddi";
    static final int EDDI_PORT = 7070;
    private static final String MONGO_NETWORK_ALIAS = "mongodb";
    private static final List<String> URL_KEYS = List.of("quarkus.eddi.url", "quarkus.rest-client.eddi.url",
            "quarkus.rest-client.eddi.uri");

    @BuildStep(onlyIf = { IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class })
    DevServicesResultBuildItem startDevServices(EddiDevServicesBuildTimeConfig config,
            DockerStatusBuildItem dockerStatus) {

        if (!config.enabled()) {
            LOG.debug("EDDI Dev Services disabled via quarkus.eddi.devservices.enabled");
            return null;
        }

        String configuredUrl = userConfiguredUrl();
        if (configuredUrl != null) {
            LOG.debugf("EDDI URL explicitly configured (%s), skipping Dev Services", configuredUrl);
            return null;
        }

        if (!dockerStatus.isDockerAvailable()) {
            LOG.warn("EDDI Dev Services need a working Docker environment; not starting them. "
                    + "Configure quarkus.eddi.url to use an existing EDDI server.");
            return null;
        }

        var images = new Images(config.image(), config.mongodbImage());
        Function<EddiContainers, String> url = EddiContainers::url;
        return DevServicesResultBuildItem.<EddiContainers> owned()
                .feature(FEATURE)
                .description("EDDI server (" + images.eddi() + ") with MongoDB (" + images.mongo() + ")")
                .serviceConfig(images)
                .startable(() -> new EddiContainers(images))
                .configProvider(Map.of(
                        "quarkus.eddi.url", url,
                        "quarkus.rest-client.eddi.url", url))
                .build();
    }

    /**
     * The URL the user configured themselves, if any. Values injected by Dev Services
     * (from a previous start) and extension defaults do not count. Read without
     * expanding expressions so {@code ${EDDI_URL}} with an unset variable does not
     * fail the build.
     */
    private static String userConfiguredUrl() {
        var config = ConfigProvider.getConfig();
        for (String key : URL_KEYS) {
            ConfigValue value = Expressions.withoutExpansion(() -> config.getConfigValue(key));
            String sourceName = value.getSourceName();
            if (value.getRawValue() != null && !value.getRawValue().isBlank()
                    && value.getSourceOrdinal() > Integer.MIN_VALUE
                    && (sourceName == null || !sourceName.toLowerCase().contains("devservice"))) {
                return value.getRawValue();
            }
        }
        return null;
    }

    /** Service identity; Dev Services are reused across live reloads while this is equal. */
    record Images(String eddi, String mongo) {
    }

    /**
     * EDDI + MongoDB on a private network.
     */
    static final class EddiContainers implements Startable {

        private final Network network = Network.newNetwork();
        private final MongoDBContainer mongodb;
        private final GenericContainer<?> eddi;

        @SuppressWarnings("resource")
        EddiContainers(Images images) {
            mongodb = new MongoDBContainer(DockerImageName.parse(images.mongo()))
                    .withNetwork(network)
                    .withNetworkAliases(MONGO_NETWORK_ALIAS);

            eddi = new GenericContainer<>(DockerImageName.parse(images.eddi()))
                    .withNetwork(network)
                    .withExposedPorts(EDDI_PORT)
                    .dependsOn(mongodb)
                    .withEnv("EDDI_DATASTORE_TYPE", "mongodb")
                    .withEnv("MONGODB_CONNECTIONSTRING", "mongodb://" + MONGO_NETWORK_ALIAS
                            + ":27017/eddi?retryWrites=true&w=majority&connectTimeoutMS=10000&socketTimeoutMS=30000")
                    .withEnv("EDDI_VAULT_MASTER_KEY", "dev-services-key")
                    // The image runs in prod mode with OIDC off; since EDDI 6.3 its
                    // AuthStartupGuard / HighValueSurfaceGuard refuse to boot unless
                    // unauthenticated access is explicitly opted into (dev only).
                    .withEnv("QUARKUS_OIDC_TENANT_ENABLED", "false")
                    .withEnv("EDDI_SECURITY_ALLOW_UNAUTHENTICATED", "true")
                    .withEnv("EDDI_MCP_ALLOW_UNAUTHENTICATED", "true")
                    .withEnv("EDDI_SECRETSTORE_ALLOW_UNAUTHENTICATED", "true")
                    .waitingFor(Wait.forHttp("/q/health/ready")
                            .forPort(EDDI_PORT)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));
        }

        @Override
        public void start() {
            eddi.start(); // starts MongoDB first (dependsOn)
            LOG.infof("EDDI Dev Services: EDDI started at %s", url());
        }

        String url() {
            return "http://" + eddi.getHost() + ":" + eddi.getMappedPort(EDDI_PORT);
        }

        @Override
        public String getConnectionInfo() {
            return url();
        }

        @Override
        public String getContainerId() {
            return eddi.getContainerId();
        }

        @Override
        public void close() {
            eddi.stop();
            mongodb.stop();
            network.close();
        }
    }
}
