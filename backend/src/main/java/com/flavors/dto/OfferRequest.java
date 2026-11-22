package com.flavors.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * What the brand sends to set up or change a campaign's follower offer (UC-F10.1). The service checks every field and
 * names each wrong one, like the campaign form; it also fills in the defaults below.
 *
 * <p>The numbers are {@link BigDecimal} so they reach the checks exactly as sent: bound to {@code Integer}, Jackson
 * would cut {@code 100.9} down to {@code 100} before anyone could refuse it. The service accepts whole numbers only.
 *
 * @param discountPercent the discount for followers, 1 to 100
 * @param expiresOn       the last day the offer works (in the restaurant's time zone); today or later
 * @param validDays       the days it works, at least one; missing means every day
 * @param validFrom       "HH:mm"; with {@code validTo}, the hours it works. Both empty: all day
 * @param validTo         "HH:mm", after {@code validFrom}
 * @param allBranches     empty or true: any of the brand's branches. False: only {@code branchIds}
 * @param branchIds       the brand's own branches, when {@code allBranches} is false
 * @param minPurchaseCents the smallest bill it applies to, in cents; empty means none
 * @param maxRedemptions  how many times it can be used in total; empty means no maximum
 * @param perPersonLimit  how many times one diner can use it; empty means one (EX-F10.1.1)
 */
public record OfferRequest(
        BigDecimal discountPercent,
        LocalDate expiresOn,
        List<DayOfWeek> validDays,
        String validFrom,
        String validTo,
        Boolean allBranches,
        List<Long> branchIds,
        BigDecimal minPurchaseCents,
        BigDecimal maxRedemptions,
        BigDecimal perPersonLimit) {

    /** The same request with whole numbers, as the tests and other code write it. */
    public OfferRequest(Integer discountPercent, LocalDate expiresOn, List<DayOfWeek> validDays, String validFrom,
                        String validTo, Boolean allBranches, List<Long> branchIds, Long minPurchaseCents,
                        Integer maxRedemptions, Integer perPersonLimit) {
        this(decimal(discountPercent), expiresOn, validDays, validFrom, validTo, allBranches, branchIds,
                decimal(minPurchaseCents), decimal(maxRedemptions), decimal(perPersonLimit));
    }

    private static BigDecimal decimal(Number number) {
        return number == null ? null : BigDecimal.valueOf(number.longValue());
    }
}
