package lab.interfaceflow.integration;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class Phase6Test {
 @Test void cursorPreservesMicrosecondsAndBindsFilters(){
  Instant time=Instant.parse("2026-10-20T12:34:56.123456Z");String id=UUID.randomUUID().toString();
  String cursor=HistoryCursor.encode(time,id,"filter-a");
  assertEquals(time,HistoryCursor.decode(cursor,"filter-a").createdAt());assertEquals(id,HistoryCursor.decode(cursor,"filter-a").id());
  assertThrows(ApiProblem.class,()->HistoryCursor.decode(cursor,"filter-b"));
  assertThrows(ApiProblem.class,()->HistoryCursor.decode("bad!","filter-a"));
  assertThrows(ApiProblem.class,()->HistoryCursor.decode(HistoryCursor.encode(Instant.MAX,id,"filter-a"),"filter-a"));
 }
 @Test void retryBatchBoundsActiveWorkAndOverlapsIndependentRequests() throws Exception {
  assertThrows(IllegalArgumentException.class,()->new RetryBatch(0));assertThrows(IllegalArgumentException.class,()->new RetryBatch(5));
  var active=new AtomicInteger();var peak=new AtomicInteger();var completed=new AtomicInteger();
  var started=new CountDownLatch(2);var release=new CountDownLatch(1);
  Runnable job=()->{int count=active.incrementAndGet();peak.accumulateAndGet(count,Math::max);started.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{active.decrementAndGet();completed.incrementAndGet();}};
  try(var batch=new RetryBatch(2);var caller=Executors.newSingleThreadExecutor()){
   var future=caller.submit(()->{try{batch.execute(List.of(job,job,job,job));}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   assertTrue(started.await(5,TimeUnit.SECONDS));assertEquals(2,active.get());release.countDown();future.get(5,TimeUnit.SECONDS);
   assertEquals(2,peak.get());assertEquals(4,completed.get());
  }finally{release.countDown();}
 }
}
