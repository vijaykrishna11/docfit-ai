package com.docfitai.backend.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React SPA from the same origin as the API (CLAUDE.md "Render Same-Origin
 * Deployment") -- the single Render Web Service that hosts both a same-origin deployment needs so
 * the {@code SameSite=Lax} refresh cookie keeps working without a custom domain (see
 * `docs/production-deployment-plan.md`).
 *
 * <p>Registered on {@code /**}, but effectively only ever reached as a <em>fallback</em>: Spring's
 * {@code RequestMappingHandlerMapping} (every {@code @RestController}, including everything under
 * {@code /api/**}) and Actuator's own handler mapping (e.g. {@code /actuator/health}) both have
 * higher priority than this auto-configured-style resource-handler mapping and are always tried
 * first, so this class never intercepts a real API or Actuator request. {@link
 * SpaFallbackResourceResolver} adds a second, defensive layer of the same guarantee directly (never
 * serve {@code index.html} for an {@code /api/**} or {@code /actuator/**} path, even one with no
 * matching controller) so a genuinely unmatched API path still gets a real 404, not the SPA shell.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResourceResolver());
    }

    /**
     * Serves a real static file when one exists at the requested path; falls back to
     * {@code index.html} (so React Router can resolve the route client-side, including its own
     * "not found" experience) for everything else -- <strong>except</strong>:
     * <ul>
     *   <li>{@code /api/**} and {@code /actuator/**} -- never served the SPA shell, even with no
     *       matching controller; returns {@code null} so Spring's normal resource-not-found
     *       handling produces a real 404 (CLAUDE.md "API 404s must remain API 404s").
     *   <li>A path whose last segment contains a {@code .} (looks like a static asset request,
     *       e.g. a stale/renamed hashed JS or CSS file) -- never silently swapped for
     *       {@code index.html} (CLAUDE.md "Hashed assets must not accidentally route to
     *       index.html when missing").
     * </ul>
     */
    static final class SpaFallbackResourceResolver extends PathResourceResolver {

        private static final Resource INDEX_HTML = new ClassPathResource("/static/index.html");

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;

            if (isApiOrActuatorPath(normalized)) {
                return null;
            }

            Resource requestedResource = location.createRelative(normalized);
            if (requestedResource.exists() && requestedResource.isReadable()) {
                return requestedResource;
            }

            if (looksLikeAStaticAssetRequest(normalized)) {
                return null;
            }

            return INDEX_HTML.exists() ? INDEX_HTML : null;
        }

        private static boolean isApiOrActuatorPath(String normalized) {
            return normalized.equals("api")
                    || normalized.startsWith("api/")
                    || normalized.equals("actuator")
                    || normalized.startsWith("actuator/");
        }

        private static boolean looksLikeAStaticAssetRequest(String normalized) {
            String lastSegment = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
            return lastSegment.contains(".");
        }
    }
}
