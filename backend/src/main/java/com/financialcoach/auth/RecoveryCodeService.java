package com.financialcoach.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recovery codes as the account recovery channel. Runs in the caller's transaction. */
@Service
class RecoveryCodeService implements AccountRecoveryChannel {

    private final RecoveryCodeRepository codes;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    RecoveryCodeService(RecoveryCodeRepository codes, Clock clock) {
        this.codes = codes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<String> issue(UUID userId) {
        codes.deleteByUser(userId);
        List<String> plain = RecoveryCodes.generate(random);
        plain.forEach(code -> codes.save(new RecoveryCode(userId, RecoveryCodes.hash(code))));
        codes.flush();
        return plain;
    }

    @Override
    @Transactional
    public boolean consume(UUID userId, String secret) {
        if (RecoveryCodes.normalize(secret).isEmpty()) {
            return false;
        }
        return codes.markUsed(userId, RecoveryCodes.hash(secret), clock.instant()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public long remaining(UUID userId) {
        return codes.countUnused(userId);
    }
}
