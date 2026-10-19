package lab.interfaceflow.integration;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringControllerTest {
 @Test void periodIsHalfOpenAndBounded(){
  Instant end=Instant.parse("2026-10-19T00:00:00Z");
  assertEquals(end.minus(Duration.ofHours(24)),MonitoringController.window(null,end).from());
  assertThrows(ApiProblem.class,()->MonitoringController.window(end,end));
  assertThrows(ApiProblem.class,()->MonitoringController.window(end.minus(Duration.ofDays(32)),end));
  assertDoesNotThrow(()->MonitoringController.window(end.minus(Duration.ofDays(31)),end));
 }
 @Test void malformedTraceNeverQueriesDatabase(){
  var mapper=mock(MonitoringMapper.class);
  var controller=new MonitoringController(mapper,new com.fasterxml.jackson.databind.ObjectMapper());
  assertThrows(ApiProblem.class,()->controller.trace("invalid"));
  verifyNoInteractions(mapper);
 }
}
