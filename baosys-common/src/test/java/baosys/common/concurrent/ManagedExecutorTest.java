package baosys.common.concurrent;

import baosys.common.web.TraceIds;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.*;

class ManagedExecutorTest {
    private ExecutorProperties properties() {
        var p = new ExecutorProperties();
        p.setCorePoolSize(1); p.setMaxPoolSize(1); p.setQueueCapacity(1); p.setShutdownTimeoutSeconds(1);
        return p;
    }

    @Test
    void propagatesMdcAndDoesNotLeakAfterSuccessOrFailure() throws Exception {
        var executor = new ManagedExecutor(properties());
        try {
            MDC.put(TraceIds.MDC_KEY, "request-a");
            assertThat(executor.submit(() -> MDC.get(TraceIds.MDC_KEY)).get(2, TimeUnit.SECONDS)).isEqualTo("request-a");
            var failed = executor.submit(() -> { MDC.put(TraceIds.MDC_KEY, "dirty"); throw new IllegalStateException(); });
            assertThatThrownBy(() -> failed.get(2, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            MDC.clear();
            assertThat(executor.submit(() -> MDC.get(TraceIds.MDC_KEY)).get(2, TimeUnit.SECONDS)).isNull();
        } finally { MDC.clear(); executor.destroy(); }
    }

    @Test
    void rejectsOverloadAndSpringCloseInterruptsTasksAndCancelsQueue() throws Exception {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(ManagedExecutor.class, () -> new ManagedExecutor(properties()));
        context.refresh();
        var executor = context.getBean(ManagedExecutor.class);
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException ex) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            var queued = executor.submit(() -> {});
            assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
            context.close();
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.isTerminated()).isTrue();
            assertThat(queued.isCancelled()).isTrue();
            assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        } finally { context.close(); executor.shutdownNow(); }
    }

    @Test
    void decoratorRestoresExistingContextOnFailure() {
        MDC.put(TraceIds.MDC_KEY, "submitter");
        Runnable wrapped = new MdcTaskDecorator().decorate(() -> {
            assertThat(MDC.get(TraceIds.MDC_KEY)).isEqualTo("submitter");
            throw new IllegalStateException();
        });
        MDC.put(TraceIds.MDC_KEY, "worker");
        try {
            assertThatThrownBy(wrapped::run).isInstanceOf(IllegalStateException.class);
            assertThat(MDC.get(TraceIds.MDC_KEY)).isEqualTo("worker");
        } finally { MDC.clear(); }
    }

    @Test
    void schedulerPropagatesTraceAndStopsWithContext() throws Exception {
        var scheduler = ManagedSchedulers.create(1, "scheduler-test-", 2);
        scheduler.initialize();
        var observed = new CompletableFuture<String>();
        try {
            MDC.put(TraceIds.MDC_KEY, "scheduled-trace");
            scheduler.schedule(() -> observed.complete(MDC.get(TraceIds.MDC_KEY)), java.time.Instant.now());
            MDC.clear();
            assertThat(observed.get(2, TimeUnit.SECONDS)).isEqualTo("scheduled-trace");
        } finally { MDC.clear(); scheduler.destroy(); }
        assertThat(scheduler.getScheduledThreadPoolExecutor().isTerminated()).isTrue();
    }

    @Test
    void backgroundServiceReceivesStopAndTerminates() throws Exception {
        var started = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var task = new ManagedBackgroundTask("background-test-", () -> {
            started.countDown();
            try { stopped.await(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { finished.countDown(); }
        }, stopped::countDown);
        try {
            task.start();
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            task.stop();
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(task.isRunning()).isFalse();
        } finally { task.destroy(); }
    }
}
