package baosys.common.concurrent;

import org.springframework.context.SmartLifecycle;
import org.springframework.beans.factory.DisposableBean;

/**
 * 为阻塞式后台服务提供 Spring 启停及独立的有界执行器。
 */
public final class ManagedBackgroundTask implements SmartLifecycle, DisposableBean {
    private final ManagedExecutor executor;
    private final Runnable task;
    private final Runnable stop;
    private volatile boolean running;
    private boolean stopped;

    public ManagedBackgroundTask(String name, Runnable task, Runnable stop) {
        var properties = new ExecutorProperties();
        properties.setCorePoolSize(1);
        properties.setMaxPoolSize(1);
        properties.setQueueCapacity(1);
        properties.setThreadNamePrefix(name);
        executor = new ManagedExecutor(properties);
        this.task = task;
        this.stop = stop;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        if (stopped) throw new IllegalStateException("Background task has been stopped");
        running = true;
        executor.execute(() -> {
            try { task.run(); }
            finally { running = false; }
        });
    }

    @Override
    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        try { stop.run(); }
        finally { executor.destroy(); running = false; }
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public void destroy() { stop(); }
}
