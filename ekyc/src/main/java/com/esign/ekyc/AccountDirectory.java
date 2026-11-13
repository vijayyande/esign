package com.esign.ekyc;

import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.hash.Hashes;
import com.esign.common.validation.ParsedSignerId;
import com.esign.common.validation.SignerIds;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AccountDirectory {
    static final String DEMO_PHOTO =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private final ConcurrentHashMap<String, SignerAccount> accounts = new ConcurrentHashMap<>();

    public void seedDemo() {
        SignerAccount account = new SignerAccount();
        account.signerId = "demo.user@username.ESP001";
        account.pinHash = Hashes.sha256Hex("123456");
        account.name = "Demo Signer";
        account.mobile = "9876543210";
        account.email = "demo@example.com";
        account.address = "1 MG Road";
        account.stateProvince = "KA";
        account.country = "IN";
        account.postalCode = "560001";
        account.pan = "ABCDE1234F";
        account.dob = "1990-01-15";
        account.gender = "M";
        account.aadhaarLastFour = "1234";
        account.ekycType = "OFFLINE_AADHAAR";
        account.photoBase64 = DEMO_PHOTO;
        accounts.put(account.signerId, account);
    }

    public void save(SignerAccount account) {
        if (accounts.containsKey(account.signerId)) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Signer id already exists");
        }
        boolean personal = isPersonal(account.ekycType);
        if (personal) {
            accounts.values().stream()
                    .filter(existing -> isPersonal(existing.ekycType))
                    .forEach(existing -> {
                        if (account.mobile.equals(existing.mobile)) {
                            throw new ApiException(EsignErrorCode.BANK_INVALID_MOBILE);
                        }
                        if (account.pan != null && !account.pan.isBlank() && account.pan.equals(existing.pan)) {
                            throw new ApiException(EsignErrorCode.BANK_INVALID_PAN);
                        }
                    });
        }
        accounts.put(account.signerId, account);
    }

    public SignerAccount require(String signerId) {
        return find(signerId).orElseThrow(() -> new ApiException(EsignErrorCode.AUTHENTICATION_FAILED));
    }

    public Optional<SignerAccount> find(String signerId) {
        SignerAccount exact = accounts.get(signerId);
        if (exact != null) {
            return Optional.of(exact);
        }
        ParsedSignerId parsed = SignerIds.parse(signerId);
        if (parsed == null) {
            return Optional.empty();
        }
        return accounts.values().stream().filter(account -> matchesAlias(account, parsed)).findFirst();
    }

    public SignerAccount getExact(String signerId) {
        SignerAccount account = accounts.get(signerId);
        if (account == null) {
            throw new ApiException(EsignErrorCode.INVALID_SIGNER_ID);
        }
        return account;
    }

    private static boolean matchesAlias(SignerAccount account, ParsedSignerId parsed) {
        ParsedSignerId owner = SignerIds.parse(account.signerId);
        if (owner == null || !owner.espId().equals(parsed.espId())) {
            return false;
        }
        if ("Mobile".equals(parsed.idType())) {
            return parsed.id().equals(account.mobile);
        }
        if ("PAN".equals(parsed.idType())) {
            return parsed.id().equalsIgnoreCase(account.pan);
        }
        return false;
    }

    static boolean isPersonal(String ekycType) {
        return "OFFLINE_AADHAAR".equals(ekycType)
                || "BANK".equals(ekycType)
                || "PAN".equals(ekycType)
                || "FOREIGN_NATIONAL".equals(ekycType);
    }
}
