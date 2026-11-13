package baosys.common.concurrent;

/**
 * 有界执行器配置，由使用方绑定自己的配置前缀。
 */
public class ExecutorProperties {
    private int corePoolSize = 100;
    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int value) { corePoolSize = value; }
    private int maxPoolSize = 500;
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int value) { maxPoolSize = value; }
    private int queueCapacity = 1000;
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int value) { queueCapacity = value; }
    private int keepAliveSeconds = 30;
    public int getKeepAliveSeconds() { return keepAliveSeconds; }
    public void setKeepAliveSeconds(int value) { keepAliveSeconds = value; }
    private int shutdownTimeoutSeconds = 20;
    public int getShutdownTimeoutSeconds() { return shutdownTimeoutSeconds; }
    public void setShutdownTimeoutSeconds(int value) { shutdownTimeoutSeconds = value; }
    private String threadNamePrefix = "worker-";
    public String getThreadNamePrefix() { return threadNamePrefix; }
    public void setThreadNamePrefix(String value) { threadNamePrefix = value; }
}
