package org.gtlcore.gtlcore.integration.ae2.wireless;

import java.util.concurrent.*;

public final class JeiPatternWorkers {

    private JeiPatternWorkers() {}

    public static ThreadPoolExecutor worker(String name) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
