package com.esign.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TxnRef {
    private TxnRef() {
    }

    public static String encode(String txn, String resCode) {
        return Base64.getEncoder().encodeToString((txn + "|" + resCode).getBytes(StandardCharsets.UTF_8));
    }

    public static String[] decode(String txnref) {
        String raw = new String(Base64.getDecoder().decode(txnref), StandardCharsets.UTF_8);
        int split = raw.indexOf('|');
        if (split < 1 || split == raw.length() - 1) {
            throw new IllegalArgumentException("txnref");
        }
        return new String[]{raw.substring(0, split), raw.substring(split + 1)};
    }
}
