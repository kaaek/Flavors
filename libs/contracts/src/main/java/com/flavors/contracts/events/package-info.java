/**
 * Versioned event payloads, one sub-package per schema version ({@code v1}, {@code v2}, ...).
 * A published version is never edited; a breaking change adds a new version next to it.
 * Payment and escrow event shapes are deliberately absent: they follow the saga design document.
 */
package com.flavors.contracts.events;
