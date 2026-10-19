package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
public class MonitoringController {
    private final MonitoringMapper mapper;
    private final ObjectMapper json;
    public MonitoringController(MonitoringMapper mapper,ObjectMapper json){this.mapper=mapper;this.json=json;}
    @GetMapping("/api/traces/{traceId}")
    public List<JsonNode> trace(@PathVariable String traceId){
        if(!traceId.matches("[a-f0-9]{32}")) throw new ApiProblem(400,"INVALID_TRACE_ID","Trace ID는 소문자 16진수 32자리입니다.");
        var rows=mapper.trace(traceId);
        if(rows.isEmpty()) throw new ApiProblem(404,"TRACE_NOT_FOUND","Trace 기록이 없습니다.");
        if(rows.size()>100) throw new ApiProblem(422,"TRACE_TOO_LARGE","Trace당 요청 조회 한도는 100건입니다. 전송 이력에서 개별 조회하세요.");
        return rows.stream().map(this::decode).toList();
    }
    public record Window(Instant from,Instant to) {}
    static Window window(Instant from,Instant to){
        Instant end=to==null?Instant.now():to,start=from==null?end.minus(Duration.ofHours(24)):from;
        if(!start.isBefore(end)||Duration.between(start,end).compareTo(Duration.ofDays(31))>0)
            throw new ApiProblem(400,"INVALID_PERIOD","기간은 시작 이상·종료 미만, 최대 31일입니다.");
        return new Window(start,end);
    }
    @GetMapping("/api/dashboard/summary")
    public JsonNode summary(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){
        var w=window(from,to);return decode(mapper.summary(w.from(),w.to()));
    }
    @GetMapping("/api/dashboard/statistics")
    public List<JsonNode> statistics(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){
        var w=window(from,to);return mapper.statistics(w.from(),w.to()).stream().map(this::decode).toList();
    }
    private JsonNode decode(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid monitoring data",e);}}
}
