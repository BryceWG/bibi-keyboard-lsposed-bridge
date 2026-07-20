/*
 * Tracks clipboard observation subscriptions independently for Pro and OSS hosts.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ClipboardObserveRegistry {
    static final class Subscription {
        final String appPackageName;
        final String token;

        Subscription(String appPackageName, String token) {
            this.appPackageName = appPackageName;
            this.token = token;
        }
    }

    private final Map<String, String> tokensByPackage = new HashMap<>();

    synchronized boolean subscribe(String appPackageName, String token) {
        if (!isKnownPackage(appPackageName) || token == null || token.length() == 0) return false;
        tokensByPackage.put(appPackageName, token);
        return true;
    }

    synchronized void unsubscribe(String appPackageName) {
        tokensByPackage.remove(appPackageName);
    }

    synchronized boolean hasSubscribers() {
        return !tokensByPackage.isEmpty();
    }

    synchronized List<Subscription> snapshot() {
        List<Subscription> result = new ArrayList<>();
        for (String appPackageName : BridgeContract.MAIN_APP_PACKAGES) {
            String token = tokensByPackage.get(appPackageName);
            if (token != null) result.add(new Subscription(appPackageName, token));
        }
        return result;
    }

    private static boolean isKnownPackage(String appPackageName) {
        return BridgeContract.PACKAGE_PRO.equals(appPackageName) ||
            BridgeContract.PACKAGE_OPEN_SOURCE.equals(appPackageName);
    }
}
