package com.otpservice.web;

import com.otpservice.otp.OtpService;
import com.otpservice.otp.SendResult;
import com.otpservice.otp.VerifyResult;
import com.otpservice.security.CurrentApp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The two calls every application makes, authenticated by {@code X-Api-Key}. */
@RestController
@RequestMapping("/v1/otp")
class OtpController {

    record SendRequest(@NotBlank String phone, String purpose) {
    }

    record VerifyRequest(@NotNull UUID requestId, @NotBlank String code) {
    }

    private final OtpService otp;

    OtpController(OtpService otp) {
        this.otp = otp;
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    SendResult send(@Valid @RequestBody SendRequest request, HttpServletRequest http) {
        return otp.send(CurrentApp.of(http), CurrentApp.keyId(http), request.phone(), request.purpose(),
                http.getRemoteAddr());
    }

    /** Always 200: "verified": false is an answer, not an error. */
    @PostMapping("/verify")
    VerifyResult verify(@Valid @RequestBody VerifyRequest request, HttpServletRequest http) {
        return otp.verify(CurrentApp.of(http), request.requestId(), request.code().trim());
    }
}
