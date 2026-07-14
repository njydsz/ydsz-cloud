package com.njydsz.pmis.common.lock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.lock.impl.FallbackDistributedLock;

@DisplayName("FallbackDistributedLock Test")
class FallbackDistributedLockTest {
    private FallbackDistributedLock lock;
    private String clientId;
    @BeforeEach
    void setUp() { clientId = UUID.randomUUID().toString(); lock = new FallbackDistributedLock(); }
    @Test
    void testAcquireFirstTime() { String k="t1"; assertTrue(lock.tryLock(k,clientId,5000,10000)); lock.unlock(k,clientId); }
    @Test
    void testReentrantAcquire() { String k="t2"; assertTrue(lock.tryLock(k,clientId,5000,10000)); assertTrue(lock.tryLock(k,clientId,5000,10000)); lock.unlock(k,clientId); lock.unlock(k,clientId); }
    @Test
    void testAcquireAfterRelease() { String k="t3"; assertTrue(lock.tryLock(k,clientId,5000,10000)); lock.unlock(k,clientId); assertTrue(lock.tryLock(k,clientId,5000,10000)); lock.unlock(k,clientId); }
    @Test
    void testDifferentClientFails() { String k="t4"; String c2="c2"; assertTrue(lock.tryLock(k,clientId,5000,10000)); assertFalse(lock.tryLock(k,c2,100,10000)); lock.unlock(k,clientId); }
    @Test
    void testLockExpiration() throws InterruptedException { String k="t5"; String c2="c2"; assertTrue(lock.tryLock(k,clientId,5000,100)); Thread.sleep(200); assertTrue(lock.tryLock(k,c2,5000,10000)); lock.unlock(k,c2); }
    @Test
    void testUnlockUnheld() { assertDoesNotThrow(() -> lock.unlock("unheld",clientId)); }
}
