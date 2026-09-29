package org.gtlcore.gtlcore.integration.ae2.wireless;

public final class MeInventoryRequestLimiterTest {

    private MeInventoryRequestLimiterTest() {}

    public static void main(String[] args) {
        limitsEachRequesterWithinSlidingWindow();
        resetsAfterClockRollback();
    }

    private static void limitsEachRequesterWithinSlidingWindow() {
        var limiter = new MeInventoryRequestLimiter<Object>(2, 20);
        Object firstRequester = new Object();
        Object secondRequester = new Object();

        require(limiter.tryAcquire(firstRequester, 100));
        require(limiter.tryAcquire(firstRequester, 119));
        require(!limiter.tryAcquire(firstRequester, 119));
        require(limiter.tryAcquire(secondRequester, 119));
        require(limiter.tryAcquire(firstRequester, 120));
        require(!limiter.tryAcquire(firstRequester, 120));
    }

    private static void resetsAfterClockRollback() {
        var limiter = new MeInventoryRequestLimiter<Object>(1, 20);
        Object requester = new Object();

        require(limiter.tryAcquire(requester, 100));
        require(!limiter.tryAcquire(requester, 101));
        require(limiter.tryAcquire(requester, 5));
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }
}
