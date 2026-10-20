package lab.interfaceflow.integration;
import java.util.List;
import java.util.concurrent.*;

final class RetryBatch implements AutoCloseable {
 private final ExecutorService workers;
 RetryBatch(int concurrency){
  if(concurrency<1||concurrency>4)throw new IllegalArgumentException("Retry concurrency must be between 1 and 4");
  workers=Executors.newFixedThreadPool(concurrency,Thread.ofPlatform().daemon(true).name("delivery-worker-",0).factory());
 }
 void execute(List<Runnable> jobs) throws InterruptedException {
  workers.invokeAll(jobs.stream().<Callable<Void>>map(job->()->{job.run();return null;}).toList());
 }
 public void close(){workers.shutdownNow();}
}
