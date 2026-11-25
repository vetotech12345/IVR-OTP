package com.otpservice.sms.twilio;

import com.otpservice.sms.SmsDeliveryException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Twilio's Messages API: one form POST, basic auth. Used for SMS and for WhatsApp, which differ only by the
 * {@code whatsapp:} prefix on both numbers.
 *
 * <p>The message body carries the code, so it is never logged; on failure only Twilio's status and error code
 * are.
 */
public class TwilioClient {

    private static final Logger log = LoggerFactory.getLogger(TwilioClient.class);

    private final TwilioProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TwilioClient(TwilioProperties properties) {
        this.properties = properties;
    }

    public void send(String from, String to, String body) {
        String form = "From=" + enc(from) + "&To=" + enc(to) + "&Body=" + enc(body);
        String auth = Base64.getEncoder().encodeToString(
                (properties.accountSid() + ":" + properties.authToken()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        properties.root() + "/2010-04-01/Accounts/" + properties.accountSid() + "/Messages.json"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new SmsDeliveryException("Twilio could not be reached", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SmsDeliveryException("Interrupted while sending", interrupted);
        }
        if (response.statusCode() / 100 != 2) {
            log.warn("Twilio refused a message: status={} body={}", response.statusCode(),
                    response.body().replaceAll("\"(to|body)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"…\""));
            throw new SmsDeliveryException("Twilio answered " + response.statusCode(), null);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
