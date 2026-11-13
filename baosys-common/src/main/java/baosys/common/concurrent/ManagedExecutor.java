package baosys.common.concurrent;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Spring 管理的有界线程池：传播 MDC，拒绝任务时抛异常，关闭超时后中断剩余任务。
 */
public final class ManagedExecutor extends ThreadPoolExecutor implements DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(ManagedExecutor.class);
    private final int shutdownTimeoutSeconds;
    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    public ManagedExecutor(ExecutorProperties properties) {
        super(properties.getCorePoolSize(), properties.getMaxPoolSize(), properties.getKeepAliveSeconds(),
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(properties.getQueueCapacity()),
            factory(properties.getThreadNamePrefix()), new AbortPolicy());
        if (properties.getShutdownTimeoutSeconds() < 0) throw new IllegalArgumentException("Negative shutdown timeout");
        shutdownTimeoutSeconds = properties.getShutdownTimeoutSeconds();
    }

    private static ThreadFactory factory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + counter.incrementAndGet());
            thread.setUncaughtExceptionHandler((worker, ex) -> LOG.error("Worker {} failed", worker.getName(), ex));
            return thread;
        };
    }

    private record ContextTask(Runnable original, Runnable decorated) implements Runnable {
        @Override
        public void run() { decorated.run(); }
    }

    @Override
    public void execute(Runnable task) {
        super.execute(new ContextTask(task, decorator.decorate(task)));
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> remaining = super.shutdownNow().stream()
            .map(task -> task instanceof ContextTask context ? context.original() : task).toList();
        remaining.forEach(task -> { if (task instanceof Future<?> future) future.cancel(false); });
        return remaining;
    }

    @Override
    public void destroy() {
        shutdown();
        try {
            if (!awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                int abandoned = shutdownNow().size();
                if (!awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                    LOG.warn("Executor did not terminate after interruption, active={}, abandoned={}", getActiveCount(), abandoned);
                }
            }
        } catch (InterruptedException ex) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
