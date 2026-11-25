package com.otpservice.app;

/** How an app's codes go out. Chosen by the operator per app. */
public enum Channel {
    SMS,
    WHATSAPP,
    /** SMS or WhatsApp, picked for each send. The challenge records which. */
    RANDOM
}
