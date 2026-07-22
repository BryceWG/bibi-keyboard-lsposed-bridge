package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BridgeContractTest {
    @Test
    public void mainAppPackagesPreferProThenOpenSource() {
        assertArrayEquals(
            new String[] {
                BridgeContract.PACKAGE_PRO,
                BridgeContract.PACKAGE_OPEN_SOURCE
            },
            BridgeContract.MAIN_APP_PACKAGES
        );
    }

    @Test
    public void permissionMappingKeepsSeparatePackageOwners() {
        assertEquals(
            BridgeContract.PERMISSION_PRO,
            BridgeContract.permissionForAppPackage(BridgeContract.PACKAGE_PRO)
        );
        assertEquals(
            BridgeContract.PERMISSION_OPEN_SOURCE,
            BridgeContract.permissionForAppPackage(BridgeContract.PACKAGE_OPEN_SOURCE)
        );
    }

    @Test
    public void hostTargetCandidatesMatchAutoAndManualModes() {
        assertArrayEquals(
            BridgeContract.MAIN_APP_PACKAGES,
            BridgeContract.candidatePackagesForHostTarget(BridgeContract.HOST_TARGET_AUTO)
        );
        assertArrayEquals(
            new String[] { BridgeContract.PACKAGE_PRO },
            BridgeContract.candidatePackagesForHostTarget(BridgeContract.HOST_TARGET_PRO)
        );
        assertArrayEquals(
            new String[] { BridgeContract.PACKAGE_OPEN_SOURCE },
            BridgeContract.candidatePackagesForHostTarget(BridgeContract.HOST_TARGET_OPEN_SOURCE)
        );
        assertEquals(
            BridgeContract.HOST_TARGET_AUTO,
            BridgeContract.normalizeHostTarget("unknown")
        );
    }

    @Test
    public void hostTargetPermissionsFollowCandidatePackages() {
        assertArrayEquals(
            new String[] { BridgeContract.PERMISSION_PRO },
            BridgeContract.candidatePermissionsForHostTarget(BridgeContract.HOST_TARGET_PRO)
        );
        assertArrayEquals(
            new String[] {
                BridgeContract.PERMISSION_PRO,
                BridgeContract.PERMISSION_OPEN_SOURCE
            },
            BridgeContract.candidatePermissionsForHostTarget(BridgeContract.HOST_TARGET_AUTO)
        );
    }
}
