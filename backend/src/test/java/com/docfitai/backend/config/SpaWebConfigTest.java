package com.docfitai.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Render same-origin deployment (CLAUDE.md): Spring Boot serves the built React SPA from the same
 * origin as the API. Uses a minimal test-only fixture under {@code src/test/resources/static/}
 * (never a real built frontend -- that only exists inside the Docker build) so this is
 * deterministic and independent of whether {@code frontend/dist} has ever been built locally.
 */
@AutoConfigureMockMvc
class SpaWebConfigTest extends PostgresIntegrationSupport {

    private static final String SPA_SHELL_MARKER = "docfit-ai-spa-shell-test-fixture";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootForwardsToTheSpaShell() throws Exception {
        // "/" is special-cased by Spring Boot's own auto-configured WelcomePageHandlerMapping
        // (detected because classpath:/static/index.html exists) -- it issues a server-side
        // forward to index.html rather than going through SpaWebConfig's resource handler
        // directly. MockMvc records the forward target but, unlike a real running server, does
        // not replay it through the handler chain to produce a real response body -- so this
        // asserts the forward target itself; the full round trip (real body content served for
        // "/") is verified separately against the real running container (see
        // docs/production-deployment-plan.md "Docker + production rehearsal").
        MvcResult result = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getForwardedUrl()).isEqualTo("index.html");
    }

    @Test
    void aDeepClientSideRouteServesTheSpaShellOnDirectNavigation() throws Exception {
        // A hard refresh / direct navigation to a React Router route must serve the same SPA
        // shell so the client-side router can resolve it -- not a container 404.
        mockMvc.perform(get("/providers/123"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(SPA_SHELL_MARKER)));
        mockMvc.perform(get("/signin")).andExpect(status().isOk());
        mockMvc.perform(get("/navigator")).andExpect(status().isOk());
    }

    @Test
    void aGenuinelyUnknownFrontendRouteStillReachesTheSpaShellForReactsOwnNotFoundPage() throws Exception {
        mockMvc.perform(get("/this-route-does-not-exist-anywhere"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(SPA_SHELL_MARKER)));
    }

    @Test
    void anExistingStaticAssetIsServedDirectlyNotAsTheSpaShell() throws Exception {
        mockMvc.perform(get("/assets/app-test-fixture.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("docfit-ai-static-asset-test-fixture")));
    }

    @Test
    void aMissingHashedAssetIsARealNotFoundNeverTheSpaShell() throws Exception {
        // A stale reference to a since-renamed hashed JS/CSS file must 404, never silently
        // resolve to index.html (CLAUDE.md "Hashed assets must not accidentally route to
        // index.html when missing").
        mockMvc.perform(get("/assets/some-old-hash-that-no-longer-exists.js")).andExpect(status().isNotFound());
    }

    @Test
    void anUnmatchedApiPathIsARealNotFoundNeverTheSpaShell() throws Exception {
        // /api/discovery/** is publicly permitted by SecurityConfig, but only /api/discovery/coverage
        // is actually mapped -- this sub-path matches no controller and must still 404, never fall
        // back to the SPA shell (CLAUDE.md "API 404s must remain API 404s").
        mockMvc.perform(get("/api/discovery/this-endpoint-does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void actuatorHealthRemainsReachableAndIsNeverSwallowedByTheSpaFallback() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
