package me.snowdrop.vertx.http.it;

import java.time.Duration;

import io.restassured.path.xml.XmlPath;
import org.junit.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static io.restassured.path.xml.XmlPath.CompatibilityMode.HTML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RouterFunctions.resources;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.noContent;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

public abstract class AbstractHttpIT {

    protected abstract WebClient getClient();

    @Test
    public void shouldGet404Response() {
        Mono<HttpStatus> statusMono = getClient()
            .get()
            .uri("/wrong")
            .exchange()
            .map(ClientResponse::statusCode);

        StepVerifier.create(statusMono)
            .expectNext(HttpStatus.NOT_FOUND)
            .expectComplete()
            .verify(Duration.ofSeconds(2));
    }

    @Test
    public void shouldGetEmptyResponse() {
        Mono<HttpStatus> statusMono = getClient()
            .get()
            .uri("/noop")
            .exchange()
            .map(ClientResponse::statusCode);

        StepVerifier.create(statusMono)
            .expectNext(HttpStatus.NO_CONTENT)
            .expectComplete()
            .verify(Duration.ofSeconds(2));
    }

    @Test
    public void shouldExchangeBodies() {
        Flux<String> bodyFlux = getClient()
            .post()
            .uri("/upper")
            .syncBody("test")
            .retrieve()
            .bodyToFlux(String.class);

        StepVerifier.create(bodyFlux)
            .expectNext("TEST")
            .expectComplete()
            .verify(Duration.ofSeconds(2));
    }

    @Test
    public void shouldGetStaticContent() {
        Mono<XmlPath> xmlMono = getClient()
            .get()
            .uri("static/index.html")
            .retrieve()
            .bodyToMono(String.class)
            .map(body -> new XmlPath(HTML, body));

        StepVerifier.create(xmlMono)
            .assertNext(xml -> assertThat(xml.getString("html.body.div")).isEqualTo("Test div"))
            .expectComplete()
            .verify(Duration.ofSeconds(2));
    }

    @Test
    public void shouldExchangeHeaders() {
        Mono<String> textMono = getClient()
            .get()
            .uri("/header")
            .header("text", "test")
            .exchange()
            .map(ClientResponse::headers)
            .map(headers -> headers.header("text"))
            .map(values -> values.get(0));

        StepVerifier.create(textMono)
            .expectNext("TEST")
            .expectComplete()
            .verify(Duration.ofSeconds(2));
    }

    @Test
    public void shouldExchangeCookies() {
        Mono<String> textMono = getClient()
            .get()
            .uri("/cookie")
            .cookie("text", "test")
            .exchange()
            .map(ClientResponse::cookies)
            .map(cookies -> cookies.getFirst("text"))
            .map(ResponseCookie::getValue);

        StepVerifier.create(textMono)
            .expectNext("TEST")
            .expectComplete()
            .verify(Duration.ofSeconds(2));
    }

    @TestConfiguration
    public static class Routers {

        @Bean
        public RouterFunction<ServerResponse> staticRouter() {
            return resources("/**", new ClassPathResource("static"));
        }

        @Bean
        public RouterFunction<ServerResponse> testRouter() {
            return route()
                .GET("/noop", request -> noContent().build())
                .GET("/cookie", this::cookieHandler)
                .GET("/header", this::headerHandler)
                .POST("/upper", this::upperHandler)
                .build();
        }

        private Mono<ServerResponse> cookieHandler(ServerRequest request) {
            String text = request.cookies()
                .getFirst("text")
                .getValue()
                .toUpperCase();
            ResponseCookie cookie = ResponseCookie.from("text", text).build();

            return noContent().cookie(cookie).build();
        }

        private Mono<ServerResponse> headerHandler(ServerRequest request) {
            String text = request.headers()
                .header("text")
                .get(0)
                .toUpperCase();

            return noContent().header("text", text).build();
        }

        private Mono<ServerResponse> upperHandler(ServerRequest request) {
            Flux<String> body = request.bodyToFlux(String.class)
                .map(String::toUpperCase);

            return ok().body(body, String.class);
        }
    }

}
