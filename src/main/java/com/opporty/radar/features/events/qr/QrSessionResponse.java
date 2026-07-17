package com.opporty.radar.features.events.qr;

public record QrSessionResponse(
    EventQrSessionsViewDTO session,
    String qrCodeBase64,
    long remainingSeconds
) {}
