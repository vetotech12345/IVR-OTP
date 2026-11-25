package com.otpservice.sms.twilio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otpservice.sms.SmsDeliveryException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Against a local stand-in for Twilio's Messages endpoint. */
class TwilioClientTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> form = new AtomicReference<>();
    private volatile int status = 201;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            form.set(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8));
            byte[] body = "{\"code\":21608,\"to\":\"+91x\",\"body\":\"secret 123456\"}".getBytes();
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private TwilioClient client() {
        return new TwilioClient(new TwilioProperties("AC123", "tok", "+15550001111", "+14155238886",
                "http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @Test
    void postsTheMessageToTheAccountsEndpointWithBasicAuth() {
        client().send("whatsapp:+14155238886", "whatsapp:+919876543210", "Your code is 482913");

        assertThat(path.get()).isEqualTo("/2010-04-01/Accounts/AC123/Messages.json");
        assertThat(auth.get()).isEqualTo("Basic " + Base64.getEncoder().encodeToString("AC123:tok".getBytes()));
        assertThat(form.get()).isEqualTo(
                "From=whatsapp:+14155238886&To=whatsapp:+919876543210&Body=Your code is 482913");
    }

    @Test
    void aRefusalBecomesADeliveryFailure() {
        status = 400;
        assertThatThrownBy(() -> client().send("+15550001111", "+919876543210", "x"))
                .isInstanceOf(SmsDeliveryException.class).hasMessageContaining("400");
    }

    @Test
    void anUnreachableTwilioBecomesADeliveryFailure() {
        server.stop(0);
        assertThatThrownBy(() -> client().send("+15550001111", "+919876543210", "x"))
                .isInstanceOf(SmsDeliveryException.class);
    }
}
