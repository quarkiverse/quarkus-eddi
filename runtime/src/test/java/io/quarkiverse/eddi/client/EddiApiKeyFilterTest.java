package io.quarkiverse.eddi.client;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Optional;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.junit.jupiter.api.Test;

import io.quarkiverse.eddi.config.EddiConfig;

/**
 * Unit tests for {@link EddiApiKeyFilter} — no Mockito needed.
 */
class EddiApiKeyFilterTest {

    @Test
    void addsAuthorizationHeaderWhenKeyPresent() throws Exception {
        EddiApiKeyFilter filter = new EddiApiKeyFilter();
        filter.config = stubConfig(Optional.of("my-secret-key"));

        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        filter.filter(new StubClientRequestContext(headers));

        assertEquals("Bearer my-secret-key", headers.getFirst("Authorization"));
    }

    @Test
    void doesNotAddHeaderWhenKeyEmpty() throws Exception {
        EddiApiKeyFilter filter = new EddiApiKeyFilter();
        filter.config = stubConfig(Optional.empty());

        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        filter.filter(new StubClientRequestContext(headers));

        assertNull(headers.getFirst("Authorization"));
    }

    @Test
    void doesNotAddHeaderWhenKeyBlank() throws Exception {
        EddiApiKeyFilter filter = new EddiApiKeyFilter();
        filter.config = stubConfig(Optional.of("   "));

        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        filter.filter(new StubClientRequestContext(headers));

        assertNull(headers.getFirst("Authorization"));
    }

    // --- Stubs ---

    private EddiConfig stubConfig(Optional<String> apiKey) {
        return new EddiConfig() {
            @Override
            public String url() {
                return "http://localhost:7070";
            }

            @Override
            public String environment() {
                return "production";
            }

            @Override
            public Optional<String> apiKey() {
                return apiKey;
            }

            @Override
            public int connectTimeoutMs() {
                return 5000;
            }

            @Override
            public int readTimeoutMs() {
                return 30000;
            }

            @Override
            public McpBridgeConfig mcpBridge() {
                return new McpBridgeConfig() {
                    @Override
                    public boolean enabled() {
                        return true;
                    }

                    @Override
                    public String agents() {
                        return "*";
                    }
                };
            }

            @Override
            public HealthConfig health() {
                return new HealthConfig() {
                    @Override
                    public boolean enabled() {
                        return true;
                    }
                };
            }
        };
    }

    /**
     * Minimal ClientRequestContext stub — only getHeaders() is used by the filter.
     */
    private static class StubClientRequestContext implements ClientRequestContext {
        private final MultivaluedMap<String, Object> headers;

        StubClientRequestContext(MultivaluedMap<String, Object> headers) {
            this.headers = headers;
        }

        @Override
        public MultivaluedMap<String, Object> getHeaders() {
            return headers;
        }

        // --- Unused methods ---
        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public java.util.Collection<String> getPropertyNames() {
            return java.util.List.of();
        }

        @Override
        public void setProperty(String name, Object object) {
        }

        @Override
        public void removeProperty(String name) {
        }

        @Override
        public URI getUri() {
            return URI.create("http://localhost");
        }

        @Override
        public void setUri(URI uri) {
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public void setMethod(String method) {
        }

        @Override
        public MultivaluedMap<String, String> getStringHeaders() {
            return new MultivaluedHashMap<>();
        }

        @Override
        public String getHeaderString(String name) {
            return null;
        }

        @Override
        public java.util.Date getDate() {
            return null;
        }

        @Override
        public java.util.Locale getLanguage() {
            return null;
        }

        @Override
        public jakarta.ws.rs.core.MediaType getMediaType() {
            return null;
        }

        @Override
        public java.util.List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<java.util.Locale> getAcceptableLanguages() {
            return java.util.List.of();
        }

        @Override
        public java.util.Map<String, jakarta.ws.rs.core.Cookie> getCookies() {
            return java.util.Map.of();
        }

        @Override
        public boolean hasEntity() {
            return false;
        }

        @Override
        public Object getEntity() {
            return null;
        }

        @Override
        public Class<?> getEntityClass() {
            return null;
        }

        @Override
        public java.lang.reflect.Type getEntityType() {
            return null;
        }

        @Override
        public void setEntity(Object entity) {
        }

        @Override
        public void setEntity(Object entity, java.lang.annotation.Annotation[] annotations,
                jakarta.ws.rs.core.MediaType mediaType) {
        }

        @Override
        public java.lang.annotation.Annotation[] getEntityAnnotations() {
            return new java.lang.annotation.Annotation[0];
        }

        @Override
        public java.io.OutputStream getEntityStream() {
            return null;
        }

        @Override
        public void setEntityStream(java.io.OutputStream outputStream) {
        }

        @Override
        public jakarta.ws.rs.client.Client getClient() {
            return null;
        }

        @Override
        public jakarta.ws.rs.core.Configuration getConfiguration() {
            return null;
        }

        @Override
        public void abortWith(jakarta.ws.rs.core.Response response) {
        }
    }
}
