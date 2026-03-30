package io.quarkiverse.eddi;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.eddi.client.*;
import io.quarkiverse.eddi.client.EddiGroupRestClient.DiscussRequest;
import io.quarkiverse.eddi.config.EddiConfig;
import io.quarkiverse.eddi.model.*;
import io.smallrye.mutiny.Uni;

/**
 * The main entry point for the Quarkus EDDI SDK.
 * <p>
 * Inject this bean to interact with an EDDI v6 server:
 *
 * <pre>{@code
 * @Inject
 * EddiClient eddi;
 *
 * // One-liner (blocking convenience)
 * String answer = eddi.chat("my-agent", "Hello!");
 *
 * // Reactive
 * Uni<ConversationResult> result = eddi.agent("my-agent")
 *         .startConversationAsync()
 *         .flatMap(conv -> conv.sayAsync("Hello!"));
 *
 * // Full lifecycle
 * Conversation conv = eddi.agent("my-agent").startConversation();
 * ConversationResult result = conv.say("Hello!");
 *
 * // Managed (intent-based, no conversation ID)
 * ManagedConversation mc = eddi.managed("support").userId("user-1").build();
 * ConversationResult result = mc.say("Help me!");
 *
 * // Fluent setup
 * SetupResult result = eddi.setup()
 *         .name("Support Bot").systemPrompt("You are helpful.")
 *         .provider("openai").model("gpt-4o")
 *         .deploy(true).create();
 * }</pre>
 */
@ApplicationScoped
public class EddiClient {

    /** Default timeout for blocking operations. */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    EddiConfig config;

    @Inject
    @RestClient
    EddiAgentRestClient agentClient;

    @Inject
    @RestClient
    EddiSetupRestClient setupClient;

    @Inject
    @RestClient
    EddiGroupRestClient groupClient;

    @Inject
    @RestClient
    EddiAdminRestClient adminClient;

    @Inject
    @RestClient
    EddiStreamingRestClient streamingClient;

    @Inject
    @RestClient
    EddiManagedRestClient managedClient;

    @Inject
    @RestClient
    EddiLogRestClient logClient;

    @Inject
    @RestClient
    EddiCoordinatorRestClient coordinatorClient;

    // ─── One-liner API (blocking convenience) ─────

    /**
     * Send a message to an agent in a single call.
     * Creates a new conversation, sends the message, and returns the response.
     */
    public String chat(String agentId, String message) {
        Conversation conv = agent(agentId).startConversation();
        ConversationResult result = conv.say(message);
        return result.text();
    }

    /**
     * Send a message and get the full result (including quick replies, actions, etc.).
     */
    public ConversationResult chatFull(String agentId, String message) {
        Conversation conv = agent(agentId).startConversation();
        return conv.say(message);
    }

    /**
     * Reactive one-liner — starts a conversation and sends a message.
     */
    public Uni<ConversationResult> chatAsync(String agentId, String message) {
        return agent(agentId).startConversationAsync()
                .flatMap(conv -> conv.sayAsync(message));
    }

    // ─── Agent builder ────────────────────────────

    /**
     * Start building an interaction with a specific agent.
     */
    public AgentBuilder agent(String agentId) {
        return new AgentBuilder(agentId);
    }

    // ─── Managed agent (intent-based) ─────────────

    /**
     * Start building a managed agent interaction by intent.
     * <p>
     * Managed agents auto-resolve the active conversation for a given
     * intent/userId pair — no conversation ID management needed.
     */
    public ManagedAgentBuilder managed(String intent) {
        return new ManagedAgentBuilder(intent);
    }

    // ─── Group discussions ────────────────────────

    /**
     * Start building a group discussion.
     */
    public GroupBuilder group(String groupId) {
        return new GroupBuilder(groupId);
    }

    // ─── Agent setup (fluent) ─────────────────────

    /**
     * Start building a standard agent setup request.
     * <p>
     * Call {@code .create()} on the builder to execute the setup:
     *
     * <pre>{@code
     * SetupResult result = eddi.setup()
     *         .name("Bot").systemPrompt("Be helpful.")
     *         .provider("openai").model("gpt-4o")
     *         .deploy(true).create();
     * }</pre>
     */
    public FluentSetupBuilder setup() {
        return new FluentSetupBuilder();
    }

    /**
     * Start building an API agent setup request (from OpenAPI spec).
     * <p>
     * Call {@code .create()} on the builder to execute the setup:
     *
     * <pre>{@code
     * SetupResult result = eddi.setupApi()
     *         .name("API Bot").systemPrompt("You call APIs.")
     *         .openApiSpec(spec).deploy(true).create();
     * }</pre>
     */
    public FluentApiSetupBuilder setupApi() {
        return new FluentApiSetupBuilder();
    }

    /**
     * Execute a standard agent setup directly.
     */
    public Uni<SetupResult> createAgent(SetupAgentRequest request) {
        return setupClient.setupAgent(request);
    }

    /**
     * Execute an API agent setup directly.
     */
    public Uni<SetupResult> createApiAgent(CreateApiAgentRequest request) {
        return setupClient.createApiAgent(request);
    }

    // ─── Admin ────────────────────────────────────

    /**
     * Access admin operations (deploy, undeploy, status).
     */
    public AdminOps admin() {
        return new AdminOps();
    }

    // ─── Logs ─────────────────────────────────────

    /**
     * Access log administration operations.
     */
    public LogOps logs() {
        return new LogOps();
    }

    // ─── Coordinator ──────────────────────────────

    /**
     * Access coordinator administration operations.
     */
    public CoordinatorOps coordinator() {
        return new CoordinatorOps();
    }

    // ════════════════════════════════════════════════
    //  Inner builders
    // ════════════════════════════════════════════════

    /**
     * Builder for agent interactions.
     */
    public class AgentBuilder {
        private final String agentId;
        private String environment;
        private String userId;
        private Map<String, Context> context;

        AgentBuilder(String agentId) {
            this.agentId = agentId;
        }

        public AgentBuilder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public AgentBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public AgentBuilder context(Map<String, Context> context) {
            this.context = context;
            return this;
        }

        /**
         * Start a new conversation (blocking, 30s timeout).
         */
        public Conversation startConversation() {
            return startConversationAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        /**
         * Start a new conversation (reactive).
         */
        public Uni<Conversation> startConversationAsync() {
            String env = environment != null ? environment : config.environment();
            Uni<Response> startUni;

            if (context != null && !context.isEmpty()) {
                startUni = agentClient.startConversationWithContext(agentId, env, userId, context);
            } else {
                startUni = agentClient.startConversation(agentId, env, userId);
            }

            return startUni.map(response -> {
                String location = response.getHeaderString("Location");
                String conversationId = extractIdFromUri(location);
                return new Conversation(conversationId, agentId, agentClient, streamingClient);
            });
        }

        /**
         * Resume an existing conversation.
         */
        public Conversation conversation(String conversationId) {
            return new Conversation(conversationId, agentId, agentClient, streamingClient);
        }
    }

    /**
     * Builder for managed agent interactions (intent-based).
     */
    public class ManagedAgentBuilder {
        private final String intent;
        private String userId;

        ManagedAgentBuilder(String intent) {
            this.intent = intent;
        }

        public ManagedAgentBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Build the managed conversation wrapper.
         */
        public ManagedConversation build() {
            return new ManagedConversation(intent, userId, managedClient);
        }
    }

    /**
     * Builder for group discussions.
     */
    public class GroupBuilder {
        private final String groupId;
        private String userId;

        GroupBuilder(String groupId) {
            this.groupId = groupId;
        }

        public GroupBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Start a group discussion (reactive).
         */
        public Uni<Response> discussAsync(String question) {
            return groupClient.discuss(groupId, new DiscussRequest(question, userId));
        }

        /**
         * Start a group discussion (blocking, 30s timeout).
         */
        public Response discuss(String question) {
            return discussAsync(question).await().atMost(DEFAULT_TIMEOUT);
        }
    }

    // ════════════════════════════════════════════════
    //  Fluent setup builders (D2)
    // ════════════════════════════════════════════════

    /**
     * Fluent builder for standard agent setup with terminal {@code create()} method.
     */
    public class FluentSetupBuilder extends SetupAgentRequest.Builder {

        /**
         * Execute the setup and return the result (reactive).
         */
        public Uni<SetupResult> createAsync() {
            return setupClient.setupAgent(build());
        }

        /**
         * Execute the setup and return the result (blocking, 30s timeout).
         */
        public SetupResult create() {
            return createAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        // Override all setters to return FluentSetupBuilder for chaining
        @Override
        public FluentSetupBuilder name(String name) {
            super.name(name);
            return this;
        }

        @Override
        public FluentSetupBuilder systemPrompt(String s) {
            super.systemPrompt(s);
            return this;
        }

        @Override
        public FluentSetupBuilder provider(String p) {
            super.provider(p);
            return this;
        }

        @Override
        public FluentSetupBuilder model(String m) {
            super.model(m);
            return this;
        }

        @Override
        public FluentSetupBuilder apiKey(String k) {
            super.apiKey(k);
            return this;
        }

        @Override
        public FluentSetupBuilder baseUrl(String u) {
            super.baseUrl(u);
            return this;
        }

        @Override
        public FluentSetupBuilder introMessage(String m) {
            super.introMessage(m);
            return this;
        }

        @Override
        public FluentSetupBuilder enableBuiltInTools(Boolean b) {
            super.enableBuiltInTools(b);
            return this;
        }

        @Override
        public FluentSetupBuilder builtInToolsWhitelist(String w) {
            super.builtInToolsWhitelist(w);
            return this;
        }

        @Override
        public FluentSetupBuilder enableQuickReplies(Boolean b) {
            super.enableQuickReplies(b);
            return this;
        }

        @Override
        public FluentSetupBuilder enableSentimentAnalysis(Boolean b) {
            super.enableSentimentAnalysis(b);
            return this;
        }

        @Override
        public FluentSetupBuilder mcpServerUrls(String u) {
            super.mcpServerUrls(u);
            return this;
        }

        @Override
        public FluentSetupBuilder deploy(Boolean d) {
            super.deploy(d);
            return this;
        }

        @Override
        public FluentSetupBuilder environment(String e) {
            super.environment(e);
            return this;
        }
    }

    /**
     * Fluent builder for API agent setup with terminal {@code create()} method.
     */
    public class FluentApiSetupBuilder extends CreateApiAgentRequest.Builder {

        /**
         * Execute the API agent setup and return the result (reactive).
         */
        public Uni<SetupResult> createAsync() {
            return setupClient.createApiAgent(build());
        }

        /**
         * Execute the API agent setup and return the result (blocking, 30s timeout).
         */
        public SetupResult create() {
            return createAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        // Override all setters to return FluentApiSetupBuilder for chaining
        @Override
        public FluentApiSetupBuilder name(String n) {
            super.name(n);
            return this;
        }

        @Override
        public FluentApiSetupBuilder systemPrompt(String s) {
            super.systemPrompt(s);
            return this;
        }

        @Override
        public FluentApiSetupBuilder openApiSpec(String s) {
            super.openApiSpec(s);
            return this;
        }

        @Override
        public FluentApiSetupBuilder provider(String p) {
            super.provider(p);
            return this;
        }

        @Override
        public FluentApiSetupBuilder model(String m) {
            super.model(m);
            return this;
        }

        @Override
        public FluentApiSetupBuilder apiKey(String k) {
            super.apiKey(k);
            return this;
        }

        @Override
        public FluentApiSetupBuilder apiBaseUrl(String u) {
            super.apiBaseUrl(u);
            return this;
        }

        @Override
        public FluentApiSetupBuilder apiAuth(String a) {
            super.apiAuth(a);
            return this;
        }

        @Override
        public FluentApiSetupBuilder endpoints(String e) {
            super.endpoints(e);
            return this;
        }

        @Override
        public FluentApiSetupBuilder enableQuickReplies(Boolean b) {
            super.enableQuickReplies(b);
            return this;
        }

        @Override
        public FluentApiSetupBuilder enableSentimentAnalysis(Boolean b) {
            super.enableSentimentAnalysis(b);
            return this;
        }

        @Override
        public FluentApiSetupBuilder deploy(Boolean d) {
            super.deploy(d);
            return this;
        }

        @Override
        public FluentApiSetupBuilder environment(String e) {
            super.environment(e);
            return this;
        }
    }

    // ════════════════════════════════════════════════
    //  Operations facades
    // ════════════════════════════════════════════════

    /**
     * Admin operations for deploy/undeploy/status.
     */
    public class AdminOps {

        public Uni<Response> deployAsync(String agentId, int version) {
            return deployAsync(agentId, version, config.environment());
        }

        public Uni<Response> deployAsync(String agentId, int version, String environment) {
            return adminClient.deployAgent(environment, agentId, version, true, false);
        }

        public void deploy(String agentId, int version) {
            deployAsync(agentId, version).await().atMost(DEFAULT_TIMEOUT);
        }

        public void deploy(String agentId, int version, String environment) {
            deployAsync(agentId, version, environment).await().atMost(DEFAULT_TIMEOUT);
        }

        /**
         * Deploy and wait for completion (up to 30s server-side).
         */
        public Uni<Response> deployAndWaitAsync(String agentId, int version, String environment) {
            return adminClient.deployAgent(environment, agentId, version, true, true);
        }

        public Uni<Response> undeployAsync(String agentId, int version) {
            return undeployAsync(agentId, version, config.environment());
        }

        public Uni<Response> undeployAsync(String agentId, int version, String environment) {
            return adminClient.undeployAgent(environment, agentId, version, false, false);
        }

        public void undeploy(String agentId, int version) {
            undeployAsync(agentId, version).await().atMost(DEFAULT_TIMEOUT);
        }

        public void undeploy(String agentId, int version, String environment) {
            undeployAsync(agentId, version, environment).await().atMost(DEFAULT_TIMEOUT);
        }

        public Uni<List<AgentDeploymentStatus>> listDeployedAsync() {
            return listDeployedAsync(config.environment());
        }

        public Uni<List<AgentDeploymentStatus>> listDeployedAsync(String environment) {
            return adminClient.getDeploymentStatuses(environment);
        }

        public List<AgentDeploymentStatus> listDeployed() {
            return listDeployedAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        public List<AgentDeploymentStatus> listDeployed(String environment) {
            return listDeployedAsync(environment).await().atMost(DEFAULT_TIMEOUT);
        }
    }

    /**
     * Log administration operations.
     */
    public class LogOps {

        public Uni<List<LogEntry>> recentAsync() {
            return recentAsync(null, null, "INFO", 100);
        }

        public Uni<List<LogEntry>> recentAsync(String agentId, String conversationId, String level, int limit) {
            return logClient.getRecentLogs(agentId, conversationId, level, limit);
        }

        public List<LogEntry> recent() {
            return recentAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        public Uni<List<Map<String, Object>>> historyAsync(String agentId) {
            return logClient.getHistoryLogs(null, agentId, null, null, null, null, 0, 100);
        }

        public Uni<Map<String, String>> instanceIdAsync() {
            return logClient.getInstanceId();
        }
    }

    /**
     * Coordinator administration operations.
     */
    public class CoordinatorOps {

        public Uni<CoordinatorStatus> statusAsync() {
            return coordinatorClient.getStatus();
        }

        public CoordinatorStatus status() {
            return statusAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        public Uni<List<DeadLetterEntry>> deadLettersAsync() {
            return coordinatorClient.getDeadLetters();
        }

        public List<DeadLetterEntry> deadLetters() {
            return deadLettersAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        public Uni<Void> replayAsync(String entryId) {
            return coordinatorClient.replayDeadLetter(entryId);
        }

        public Uni<Void> discardAsync(String entryId) {
            return coordinatorClient.discardDeadLetter(entryId);
        }

        public Uni<Integer> purgeAsync() {
            return coordinatorClient.purgeDeadLetters();
        }
    }

    // ════════════════════════════════════════════════
    //  Utilities
    // ════════════════════════════════════════════════

    static String extractIdFromUri(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            throw new IllegalStateException("EDDI returned no Location header");
        }
        URI uri = URI.create(uriString);
        String path = uri.getPath();
        String[] segments = path.split("/");
        return segments[segments.length - 1];
    }
}
