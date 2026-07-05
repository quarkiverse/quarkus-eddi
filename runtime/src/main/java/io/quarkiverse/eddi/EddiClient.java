package io.quarkiverse.eddi;

import static io.quarkiverse.eddi.EddiDefaults.DEFAULT_TIMEOUT;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.InboundSseEvent;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.quarkiverse.eddi.client.*;
import io.quarkiverse.eddi.client.EddiGroupRestClient.DiscussRequest;
import io.quarkiverse.eddi.config.EddiConfig;
import io.quarkiverse.eddi.model.*;
import io.smallrye.mutiny.Multi;
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
 *         .agentName("Support Bot").systemPrompt("You are helpful.")
 *         .provider("openai").model("gpt-4o")
 *         .deploy(true).create();
 * }</pre>
 */
@ApplicationScoped
public class EddiClient {

    @Inject
    EddiConfig config;

    private static final Logger LOG = Logger.getLogger(EddiClient.class);

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
     * Creates a new conversation, sends the message, returns the response,
     * and automatically ends the conversation.
     */
    public String chat(String agentId, String message) {
        return chatFull(agentId, message).text();
    }

    /**
     * Send a message and get the full result (including quick replies, actions, etc.).
     * Creates a new conversation, sends the message, returns the result,
     * and automatically ends the conversation.
     * <p>
     * Exception: if the response leaves the conversation paused awaiting a human
     * decision ({@code AWAITING_HUMAN}), it is <em>not</em> ended — ending it would
     * cancel the pending approval. Resume it via
     * {@code eddi.agent(agentId).conversation(result.conversationId())}.
     */
    public ConversationResult chatFull(String agentId, String message) {
        Conversation conv = agent(agentId).startConversation();
        boolean keepAlive = false;
        try {
            ConversationResult result = conv.say(message);
            keepAlive = result.isAwaitingHuman();
            return result;
        } finally {
            if (!keepAlive) {
                conv.endQuietly();
            }
        }
    }

    /**
     * Reactive one-liner — starts a conversation, sends a message,
     * and automatically ends the conversation afterwards (unless it is left
     * paused awaiting a human decision).
     */
    public Uni<ConversationResult> chatAsync(String agentId, String message) {
        return agent(agentId).startConversationAsync()
                .flatMap(conv -> conv.sayAsync(message)
                        .call(result -> result.isAwaitingHuman()
                                ? Uni.createFrom().voidItem()
                                : endQuietlyAsync(conv))
                        .onFailure().call(() -> endQuietlyAsync(conv)));
    }

    private Uni<Void> endQuietlyAsync(Conversation conv) {
        return conv.endAsync()
                .onFailure().invoke(e -> LOG.debugf(e,
                        "Failed to end conversation %s (cleanup)", conv.id()))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
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
     *         .agentName("Bot").systemPrompt("Be helpful.")
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
     *         .agentName("API Bot").systemPrompt("You call APIs.")
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

    // ─── HITL approvals inbox ─────────────────────

    /**
     * Access the HITL (Human-in-the-Loop) approvals inboxes — conversations and
     * group discussions currently awaiting a human decision.
     */
    public ApprovalOps approvals() {
        return new ApprovalOps();
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

        /**
         * Start a group discussion and stream progress events (SSE).
         */
        public Multi<StreamToken> discussStreaming(String question) {
            return groupClient.discussStreaming(groupId, new DiscussRequest(question, userId))
                    .map(EddiClient::toStreamToken);
        }

        // ─── Cancel ───────────────────────────────

        public Uni<Response> cancelAsync(String groupConversationId) {
            return groupClient.cancelDiscussion(groupId, groupConversationId);
        }

        public void cancel(String groupConversationId) {
            cancelAsync(groupConversationId).await().atMost(DEFAULT_TIMEOUT);
        }

        // ─── HITL approvals ───────────────────────

        /**
         * Approve/resume a paused group discussion (reactive).
         */
        public Uni<Response> approveAsync(String groupConversationId, HitlDecision decision) {
            return groupClient.approveGroupPhase(groupId, groupConversationId, GroupApprovalRequest.of(decision));
        }

        /**
         * Approve/resume a paused group discussion (blocking, 30s timeout).
         */
        public Response approve(String groupConversationId, HitlDecision decision) {
            return approveAsync(groupConversationId, decision).await().atMost(DEFAULT_TIMEOUT);
        }

        /**
         * Approve/resume a paused group discussion with an optional note.
         */
        public Response approve(String groupConversationId, String note) {
            return approve(groupConversationId, HitlDecision.approve(note));
        }

        /**
         * Reject and resume a paused group discussion with an optional note.
         */
        public Response reject(String groupConversationId, String note) {
            return approve(groupConversationId, HitlDecision.reject(note));
        }

        /**
         * Approve/resume a paused group discussion and stream the resumed progress
         * (SSE).
         */
        public Multi<StreamToken> approveStreaming(String groupConversationId, HitlDecision decision) {
            return groupClient.approveGroupPhaseStreaming(groupId, groupConversationId,
                    GroupApprovalRequest.of(decision)).map(EddiClient::toStreamToken);
        }

        /**
         * Read a group conversation's approval status (reactive).
         */
        public Uni<Response> approvalStatusAsync(String groupConversationId, String detail) {
            return groupClient.getGroupApprovalStatus(groupId, groupConversationId, detail);
        }

        /**
         * Read a group conversation's approval status summary (blocking, 30s timeout).
         */
        public Response approvalStatus(String groupConversationId) {
            return approvalStatusAsync(groupConversationId, "summary").await().atMost(DEFAULT_TIMEOUT);
        }

        /**
         * List this group's conversations awaiting human approval (reactive).
         */
        public Uni<List<PendingApprovalSummary>> pendingApprovalsAsync(int limit) {
            return groupClient.listGroupPendingApprovals(groupId, limit);
        }

        /**
         * List this group's conversations awaiting human approval (blocking, 30s
         * timeout).
         */
        public List<PendingApprovalSummary> pendingApprovals() {
            return pendingApprovalsAsync(100).await().atMost(DEFAULT_TIMEOUT);
        }
    }

    // ════════════════════════════════════════════════
    //  Fluent setup builders
    // ════════════════════════════════════════════════

    /**
     * Fluent builder for standard agent setup with terminal {@code create()} method.
     * <p>
     * All setter methods are inherited from {@link SetupAgentRequest.Builder}
     * via self-referential generics — no override boilerplate needed.
     */
    public class FluentSetupBuilder extends SetupAgentRequest.Builder<FluentSetupBuilder> {

        @Override
        protected FluentSetupBuilder self() {
            return this;
        }

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
    }

    /**
     * Fluent builder for API agent setup with terminal {@code create()} method.
     * <p>
     * All setter methods are inherited from {@link CreateApiAgentRequest.Builder}
     * via self-referential generics — no override boilerplate needed.
     */
    public class FluentApiSetupBuilder extends CreateApiAgentRequest.Builder<FluentApiSetupBuilder> {

        @Override
        protected FluentApiSetupBuilder self() {
            return this;
        }

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

        /**
         * Get recent logs with default filters (INFO level, limit 100).
         */
        public Uni<List<LogEntry>> recentAsync() {
            return recentAsync(null, null, "INFO", 100);
        }

        /**
         * Get recent logs with custom filters.
         */
        public Uni<List<LogEntry>> recentAsync(String agentId, String conversationId, String level, int limit) {
            return logClient.getRecentLogs(agentId, conversationId, level, limit);
        }

        public List<LogEntry> recent() {
            return recentAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        /**
         * Get historical logs for a specific agent.
         */
        public Uni<List<LogEntry>> historyAsync(String agentId) {
            return historyAsync(null, agentId, null, null, null, null, 0, 100);
        }

        /**
         * Get historical logs with full filter control.
         */
        public Uni<List<LogEntry>> historyAsync(String environment, String agentId,
                Integer agentVersion, String conversationId, String userId,
                String instanceId, int skip, int limit) {
            return logClient.getHistoryLogs(environment, agentId, agentVersion, conversationId, userId,
                    instanceId, skip, limit);
        }

        /**
         * Get historical logs for a specific agent (blocking, 30s timeout).
         */
        public List<LogEntry> history(String agentId) {
            return historyAsync(agentId).await().atMost(DEFAULT_TIMEOUT);
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

    /**
     * HITL approvals inbox operations — list conversations and group discussions
     * awaiting a human decision.
     */
    public class ApprovalOps {

        /**
         * List single-agent conversations awaiting human approval (reactive,
         * default limit 200).
         */
        public Uni<List<PendingApprovalSummary>> pendingAsync() {
            return pendingAsync(200);
        }

        public Uni<List<PendingApprovalSummary>> pendingAsync(int limit) {
            return agentClient.listPendingApprovals(limit);
        }

        public List<PendingApprovalSummary> pending() {
            return pendingAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        public List<PendingApprovalSummary> pending(int limit) {
            return pendingAsync(limit).await().atMost(DEFAULT_TIMEOUT);
        }

        /**
         * Cross-group inbox — list group conversations awaiting human approval
         * across every group (reactive, default limit 100).
         */
        public Uni<List<PendingApprovalSummary>> pendingGroupsAsync() {
            return pendingGroupsAsync(100);
        }

        public Uni<List<PendingApprovalSummary>> pendingGroupsAsync(int limit) {
            return groupClient.listAllGroupPendingApprovals(limit);
        }

        public List<PendingApprovalSummary> pendingGroups() {
            return pendingGroupsAsync().await().atMost(DEFAULT_TIMEOUT);
        }

        public List<PendingApprovalSummary> pendingGroups(int limit) {
            return pendingGroupsAsync(limit).await().atMost(DEFAULT_TIMEOUT);
        }
    }

    // ════════════════════════════════════════════════
    //  Utilities
    // ════════════════════════════════════════════════

    /**
     * Map a raw SSE event to a {@link StreamToken}, preserving the server's event
     * name (defaulting to {@code token} for unnamed events).
     */
    static StreamToken toStreamToken(InboundSseEvent sseEvent) {
        String eventName = sseEvent.getName();
        String data = sseEvent.readData();
        String type = (eventName != null && !eventName.isEmpty()) ? eventName : "token";
        return new StreamToken(type, data);
    }

    static String extractIdFromUri(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            throw new IllegalStateException("EDDI returned no Location header");
        }
        URI uri = URI.create(uriString);
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("EDDI Location header has no path: " + uriString);
        }
        String[] segments = path.split("/");
        // Walk backwards to find the last non-empty segment
        for (int i = segments.length - 1; i >= 0; i--) {
            if (!segments[i].isEmpty()) {
                return segments[i];
            }
        }
        throw new IllegalStateException("EDDI Location header has no path segments: " + uriString);
    }
}
