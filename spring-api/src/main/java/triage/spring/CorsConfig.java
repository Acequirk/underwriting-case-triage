package triage.spring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class CorsConfig implements WebMvcConfigurer {
    private final String origin;

    CorsConfig(@Value("${triage.allowed-origin}") String origin) {
        this.origin = origin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins(origin).allowedMethods("GET", "POST", "OPTIONS");
    }
}
