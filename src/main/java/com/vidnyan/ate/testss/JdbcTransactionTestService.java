package com.vidnyan.ate.testss;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JdbcTransactionTestService {

    private final JdbcExampleRepository repository;

    // SAFE: Annotated with @Transactional
    @Transactional
    public void safeTransactionalMethod(String name, String email) {
        repository.createUser(name, email);
    }

    // UNSAFE: No @Transactional at all
    public void unsafeNonTransactionalMethod(String name, String email) {
        repository.createUser(name, email);
    }

    // UNSAFE: Nested call (assuming caller has no tx).
    // The evaluator scans from root, so if this is a root, it fails.
    public void indirectUnsafeMethod(String email) {
        repository.findUserByEmail(email);
    }
}
