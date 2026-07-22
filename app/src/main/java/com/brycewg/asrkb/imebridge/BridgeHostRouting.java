/*
 * Process-wide host routing for Pro / OSS connection candidates.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class BridgeHostRouting {
    private static volatile String hostTarget = BridgeContract.HOST_TARGET_AUTO;
    private static volatile String[] packages = BridgeContract.MAIN_APP_PACKAGES;

    private BridgeHostRouting() {
    }

    static synchronized void apply(String target) {
        hostTarget = BridgeContract.normalizeHostTarget(target);
        packages = BridgeContract.candidatePackagesForHostTarget(hostTarget);
    }

    static String hostTarget() {
        return hostTarget;
    }

    static String[] packages() {
        return packages;
    }

    static String[] permissions() {
        return BridgeContract.candidatePermissionsForHostTarget(hostTarget);
    }

    static boolean allowsPackage(String appPackageName) {
        if (appPackageName == null) return false;
        for (String candidate : packages) {
            if (candidate.equals(appPackageName)) return true;
        }
        return false;
    }
}
