/*
 * Tracks clipboard observation subscriptions independently for Pro and OSS hosts.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import java.util.Collections;
import java.util.List;

final class ClipboardObserveRegistry {
    static final class Subscription {
        final String appPackageName;
        final String token;

        Subscription(String appPackageName, String token) {
            this.appPackageName = appPackageName;
            this.token = token;
        }
    }

    private String activePackageName;
    private String activeToken;

    synchronized boolean subscribe(String appPackageName, String token) {
        if (!BridgeHostRouting.allowsPackage(appPackageName) ||
            token == null ||
            token.length() == 0) {
            return false;
        }
        if (activePackageName != null && !activePackageName.equals(appPackageName)) {
            for (String candidate : BridgeHostRouting.packages()) {
                if (candidate.equals(activePackageName)) return false;
                if (candidate.equals(appPackageName)) break;
            }
        }
        activePackageName = appPackageName;
        activeToken = token;
        return true;
    }

    synchronized void unsubscribe(String appPackageName) {
        if (!appPackageName.equals(activePackageName)) return;
        activePackageName = null;
        activeToken = null;
    }

    synchronized void retainAllowedHosts() {
        if (activePackageName == null || BridgeHostRouting.allowsPackage(activePackageName)) return;
        activePackageName = null;
        activeToken = null;
    }

    synchronized boolean hasSubscribers() {
        return activePackageName != null;
    }

    synchronized List<Subscription> snapshot() {
        if (activePackageName == null) return Collections.emptyList();
        return Collections.singletonList(new Subscription(activePackageName, activeToken));
    }
}
