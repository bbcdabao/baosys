package baosys.common.concurrent;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 公共调度器工厂；返回的调度器应注册为 Spring Bean。
 */
public final class ManagedSchedulers {
    private ManagedSchedulers() {}
    public static ThreadPoolTaskScheduler create(int poolSize, String prefix, int shutdownSeconds) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setTaskDecorator(new MdcTaskDecorator());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setAwaitTerminationSeconds(shutdownSeconds);
        scheduler.setErrorHandler(ex -> LoggerFactory.getLogger(ManagedSchedulers.class).error("Scheduled task failed", ex));
        return scheduler;
    }
}
