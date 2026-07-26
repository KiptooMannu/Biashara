package com.biashara.common.enums;

/**
 * The five root classes of the chart of accounts. Normal balance side is encoded
 * so the ledger can compute running balances without a lookup table.
 */
public enum AccountType {
    ASSET(true),
    LIABILITY(false),
    EQUITY(false),
    REVENUE(false),
    EXPENSE(true);

    private final boolean debitNormal;

    AccountType(boolean debitNormal) {
        this.debitNormal = debitNormal;
    }

    public boolean isDebitNormal() {
        return debitNormal;
    }
}
