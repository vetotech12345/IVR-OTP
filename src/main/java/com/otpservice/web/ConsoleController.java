package com.otpservice.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The console's reads. Under /admin, so the admin token guards them like every other operator call. */
@RestController
@RequestMapping("/admin")
class ConsoleController {

    private final ConsoleService console;

    ConsoleController(ConsoleService console) {
        this.console = console;
    }

    @GetMapping("/overview")
    ConsoleService.Overview overview() {
        return console.overview();
    }

    @GetMapping("/otp-log")
    ConsoleService.LogPage log(@RequestParam(required = false) UUID appId,
                               @RequestParam(required = false) String phone,
                               @RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size) {
        return console.log(appId, phone, status, page, size);
    }
}
