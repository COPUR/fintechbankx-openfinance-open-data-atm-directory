package com.enterprise.openfinance.atmdirectory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AtmContentDigestTest {

    private static List<AtmLocation> network(int size) {
        return IntStream.range(0, size)
            .mapToObj(i -> new AtmLocation(String.format("ATM-%06d", i), "ATM " + i, "InService",
                24 + (i % 1000) / 1000.0, 54 + (i % 997) / 1000.0, "Road " + i, "Dubai", "AE", "Standard",
                List.of("CashWithdrawal", "CashDeposit"), "AED", Instant.parse("2026-03-01T00:00:00Z").plusSeconds(i)))
            .toList();
    }

    @Test
    void digestIsUnchangedFromTheFirstReleaseEtagSoCachedCopiesStayValid() {
        // Value produced by the original (string-concatenating) implementation for this input.
        assertThat(AtmContentDigest.of(network(3)))
            .isEqualTo("9cbd3376763af290b35c51a54f0d564c29f0196e46cd8607d820bfc7f9c3fac3");
    }

    @Test
    void digestChangesWhenAnAtmChanges() {
        AtmLocation downtown = network(1).getFirst();
        AtmLocation outOfService = new AtmLocation(downtown.atmId(), downtown.name(), "OutOfService",
            downtown.latitude(), downtown.longitude(), downtown.address(), downtown.city(), downtown.country(),
            downtown.accessibility(), downtown.services(), downtown.currency(), downtown.updatedAt());

        assertThat(AtmContentDigest.of(List.of(downtown))).isNotEqualTo(AtmContentDigest.of(List.of(outOfService)));
    }

    @Test
    void digestDoesNotDependOnTheOrderTheStoreReturned() {
        List<AtmLocation> reversed = new ArrayList<>(network(50));
        java.util.Collections.reverse(reversed);

        assertThat(AtmContentDigest.of(reversed)).isEqualTo(AtmContentDigest.of(network(50)));
    }

    @Test
    void digestOfALargeNetworkIsLinear() {
        List<AtmLocation> sixThousand = network(6_000);
        List<AtmLocation> sixtyThousand = network(60_000);
        AtmContentDigest.of(sixThousand); // warm-up

        long start = System.nanoTime();
        AtmContentDigest.of(sixtyThousand);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // The quadratic version needed about 1.7 s for 6,000 rows, so 60,000 would take minutes.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }
}
