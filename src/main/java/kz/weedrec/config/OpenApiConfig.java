package kz.weedrec.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: http://localhost:8080/swagger-ui.html (the Authorize button accepts a JWT). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI weedRecOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WeedRec API")
                        .version("1.0.0")
                        .description("Backend of a weed-control recommendation platform. "
                                + "Users are farmers and agronomists, items are weed-control treatments, "
                                + "interactions are views, saves, applications and ratings."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
