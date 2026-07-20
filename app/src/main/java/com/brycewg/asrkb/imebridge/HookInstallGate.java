/*
 * Process-local ownership seam for installing IME hooks.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class HookInstallGate {
    private String installedPackage;

    synchronized boolean tryInstall(String packageName) {
        if (installedPackage != null) return false;
        if (packageName == null || packageName.length() == 0) return false;
        installedPackage = packageName;
        return true;
    }

    synchronized String getInstalledPackage() {
        return installedPackage;
    }

    synchronized String resolveHostPackage(String servicePackage) {
        if (servicePackage != null && servicePackage.length() > 0) return servicePackage;
        return installedPackage;
    }
}
