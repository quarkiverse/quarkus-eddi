package io.quarkiverse.eddi;

import io.quarkiverse.eddi.client.EddiAdminRestClient;
import io.quarkiverse.eddi.client.EddiAgentRestClient;
import io.quarkiverse.eddi.client.EddiGroupRestClient;
import io.quarkiverse.eddi.client.EddiGroupRestClient.DiscussRequest;
import io.quarkiverse.eddi.client.EddiSetupRestClient;
import io.quarkiverse.eddi.config.EddiConfig;
import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkiverse.eddi.model.GroupResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.net.URI;
import java.util.*;

/**
 * The main entry point for the Quarkus EDDI SDK.
 * <p>
 * Inject this bean to interact with an EDDI server:
 * <pre>{@code
 * @Inject EddiClient eddi;
 *
 * // One-liner
 * String answer = eddi.chat("my-agent", "Hello!");
 *
 * // Full lifecycle
 * Conversation conv = eddi.agent("my-agent").startConversation();
 * ConversationResult result = conv.say("Hello!");
 * }</pre>
 */
@ApplicationScoped
public class EddiClient {

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

    // ─── One-liner API ─────────────────────────────

    /**
     * Send a message to an agent in a single call.
     * Creates a new conversation, sends the message, and returns the response.
     * <p>
     * This is the simplest way to interact with an EDDI agent.
     *
     * @param agentId the EDDI agent ID
     * @param message the user message
     * @return the agent's text response
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

    // ─── Agent builder ─────────────────────────────

    /**
     * Start building an interaction with a specific agent.
     */
    public AgentBuilder agent(String agentId) {
        return new AgentBuilder(agentId);
    }

    // ─── Group discussions ─────────────────────────

    /**
     * Start building a group discussion.
     */
    public GroupBuilder group(String groupId) {
        return new GroupBuilder(groupId);
    }

    // ─── Agent setup ───────────────────────────────

    /**
     * Start building a new agent setup request.
     */
    public SetupBuilder setup() {
        return new SetupBuilder();
    }

    // ─── Admin ─────────────────────────────────────

    /**
     * Access admin operations (deploy, undeploy, status).
     */
    public AdminOps admin() {
        return new AdminOps();
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

        /**
         * Start a new conversation with this agent.
         */
        public Conversation startConversation() {
            String env = environment != null ? environment : config.environment();
            Response response = agentClient.startConversation(agentId, env, userId)
                    .await().indefinitely();

            String location = response.getHeaderString("Location");
            String conversationId = extractIdFromUri(location);
            return new Conversation(conversationId, agentId, agentClient);
        }

        /**
         * Resume an existing conversation.
         */
        public Conversation conversation(String conversationId) {
            return new Conversation(conversationId, agentId, agentClient);
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
         * Start a group discussion with the given question.
         */
        @SuppressWarnings("unchecked")
        public GroupResult discuss(String question) {
            var request = new DiscussRequest(question, userId);

            Response response = groupClient.discuss(groupId, request)
                    .await().indefinitely();
            Map<String, Object> body = response.readEntity(Map.class);

            return new GroupResult(
                    groupId,
                    (String) body.get("groupConversationId"),
                    question,
                    (List<Map<String, Object>>) body.get("transcript"),
                    (String) body.get("synthesis"));
        }
    }

    /**
     * Builder for one-command agent setup.
     */
    public class SetupBuilder {
        private final Map<String, Object> params = new LinkedHashMap<>();

        public SetupBuilder name(String name) {
            params.put("name", name);
            return this;
        }

        public SetupBuilder systemPrompt(String prompt) {
            params.put("systemPrompt", prompt);
            return this;
        }

        public SetupBuilder provider(String provider) {
            params.put("provider", provider);
            return this;
        }

        public SetupBuilder model(String model) {
            params.put("model", model);
            return this;
        }

        public SetupBuilder apiKey(String apiKey) {
            params.put("apiKey", apiKey);
            return this;
        }

        public SetupBuilder baseUrl(String baseUrl) {
            params.put("baseUrl", baseUrl);
            return this;
        }

        public SetupBuilder introMessage(String message) {
            params.put("introMessage", message);
            return this;
        }

        public SetupBuilder enableBuiltInTools(boolean enable) {
            params.put("enableBuiltInTools", enable);
            return this;
        }

        public SetupBuilder mcpServers(String servers) {
            params.put("mcpServers", servers);
            return this;
        }

        public SetupBuilder deploy() {
            params.put("deploy", true);
            return this;
        }

        public SetupBuilder deploy(boolean deploy) {
            params.put("deploy", deploy);
            return this;
        }

        public SetupBuilder environment(String env) {
            params.put("environment", env);
            return this;
        }

        /**
         * Execute the setup and return the created agent ID.
         */
        public String create() {
            Response response = setupClient.setupAgent(params)
                    .await().indefinitely();
            String location = response.getHeaderString("Location");
            return extractIdFromUri(location);
        }
    }

    /**
     * Admin operations for deploy/undeploy/status.
     */
    public class AdminOps {

        public void deploy(String agentId, int version) {
            deploy(agentId, version, config.environment());
        }

        public void deploy(String agentId, int version, String environment) {
            adminClient.deployAgent(environment, agentId, version, true, false)
                    .await().indefinitely();
        }

        public void undeploy(String agentId, int version) {
            undeploy(agentId, version, config.environment());
        }

        public void undeploy(String agentId, int version, String environment) {
            adminClient.undeployAgent(environment, agentId, version, false, false)
                    .await().indefinitely();
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> listDeployed() {
            return listDeployed(config.environment());
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> listDeployed(String environment) {
            return adminClient.getDeploymentStatuses(environment)
                    .await().indefinitely();
        }
    }

    // ════════════════════════════════════════════════
    //  Utilities
    // ════════════════════════════════════════════════

    private static String extractIdFromUri(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            throw new IllegalStateException("EDDI returned no Location header");
        }
        URI uri = URI.create(uriString);
        String path = uri.getPath();
        String[] segments = path.split("/");
        // ID is the last path segment
        return segments[segments.length - 1];
    }
}
