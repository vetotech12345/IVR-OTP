package com.otpservice.web;

import com.otpservice.app.AppAdminService;
import com.otpservice.app.AppAdminService.AppView;
import com.otpservice.app.AppAdminService.CreatedApp;
import com.otpservice.app.AppAdminService.IssuedKey;
import com.otpservice.app.AppAdminService.KeyView;
import com.otpservice.app.AppAdminService.Settings;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Apps and their keys. Behind {@code Authorization: Bearer <OTP_ADMIN_TOKEN>}. */
@RestController
@RequestMapping("/admin/apps")
class AdminController {

    record CreateAppRequest(String name, Settings settings) {
    }

    record NewKeyRequest(String label) {
    }

    private final AppAdminService admin;

    AdminController(AppAdminService admin) {
        this.admin = admin;
    }

    /** Returns the app and its first API key. The key is shown here once and cannot be read again. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedApp create(@RequestBody CreateAppRequest request) {
        return admin.create(request.name(), request.settings());
    }

    @GetMapping
    List<AppView> list() {
        return admin.list();
    }

    @GetMapping("/{appId}")
    AppView get(@PathVariable UUID appId) {
        return admin.get(appId);
    }

    @PatchMapping("/{appId}")
    AppView update(@PathVariable UUID appId, @RequestBody Settings settings) {
        return admin.update(appId, settings);
    }

    @PostMapping("/{appId}/keys")
    @ResponseStatus(HttpStatus.CREATED)
    IssuedKey newKey(@PathVariable UUID appId, @RequestBody(required = false) NewKeyRequest request) {
        return admin.newKey(appId, request == null ? null : request.label());
    }

    @GetMapping("/{appId}/keys")
    List<KeyView> keys(@PathVariable UUID appId) {
        return admin.keys(appId);
    }

    @DeleteMapping("/{appId}/keys/{keyId}")
    KeyView revoke(@PathVariable UUID appId, @PathVariable UUID keyId) {
        return admin.revoke(appId, keyId);
    }
}
