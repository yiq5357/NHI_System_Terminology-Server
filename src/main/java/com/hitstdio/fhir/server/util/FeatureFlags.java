package com.hitstdio.fhir.server.util;

/**
 * TEMPORARY — DELETE THIS CLASS IN NEXT VERSION.
 *
 * Feature flags for rolling back specific TX 1.9.0 behaviour changes.
 * Default is {@code false} (new behaviour). Set to {@code true} to revert to
 * the pre-fix behaviour for the following test cases:
 * <ul>
 *   <li>extensions/extensions-echo-bad-supplement</li>
 *   <li>version/vs-expand-v-wb</li>
 *   <li>errors/broken-filter-expand</li>
 *   <li>default-valueset-version/indirect-expand-zero-pinned</li>
 * </ul>
 *
 * To delete: remove this file and every reference to {@link #LEGACY_TX_BEHAVIOR}.
 */
public final class FeatureFlags {

    private FeatureFlags() {}

    /**
     * When {@code true}, reverts to legacy (pre-TX-1.9.0-fix) behaviour.
     * Affects: extensions-echo-bad-supplement, vs-expand-v-wb,
     *          broken-filter-expand, indirect-expand-zero-pinned.
     */
    public static boolean LEGACY_TX_BEHAVIOR = true;
}
