package com.enterprise.openfinance.atmdirectory.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;

/**
 * Content version of a set of ATMs: SHA-256 over ";" + canonical row for every ATM,
 * rows sorted, in one digest pass. Linear in the number of ATMs, independent of the
 * order the store returned, and unchanged from the first release's ETag value.
 */
public final class AtmContentDigest {

    private static final byte[] SEPARATOR = ";".getBytes(StandardCharsets.UTF_8);

    private AtmContentDigest() {
    }

    public static String of(Collection<AtmLocation> atms) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
        atms.stream().map(AtmContentDigest::canonicalRow).sorted().forEach(row -> {
            digest.update(SEPARATOR);
            digest.update(row.getBytes(StandardCharsets.UTF_8));
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String canonicalRow(AtmLocation a) {
        return String.join("|",
            a.atmId(),
            a.name(),
            a.status(),
            String.valueOf(a.latitude()),
            String.valueOf(a.longitude()),
            a.address(),
            a.city(),
            a.country(),
            a.accessibility(),
            String.join(",", a.services()),
            a.currency(),
            a.updatedAt().toString());
    }
}
