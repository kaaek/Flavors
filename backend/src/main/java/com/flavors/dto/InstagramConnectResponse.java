package com.flavors.dto;

/** Where the browser goes next: Instagram's login page for this connection (EEC-33, step 2). */
public record InstagramConnectResponse(String authorizationUrl) {
}
