package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import lab.interfaceflow.integration.InterfaceModels.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PayloadRedactor {
    private static final Set<String> SENSITIVE=Set.of("password","passwd","token","accesstoken","refreshtoken","authorization","secret","apikey","email","ssn","residentregistrationnumber");
    private boolean sensitive(String key){return SENSITIVE.contains(key.toLowerCase(Locale.ROOT).replace("_","").replace("-",""));}
    public JsonNode redact(JsonNode value) {
        if(value==null) return null;
        if(value.isObject()) {
            ObjectNode result=JsonNodeFactory.instance.objectNode();
            value.fields().forEachRemaining(entry->result.set(entry.getKey(),sensitive(entry.getKey())?TextNode.valueOf("***"):redact(entry.getValue())));
            return result;
        }
        if(value.isArray()) {ArrayNode result=JsonNodeFactory.instance.arrayNode();value.forEach(item->result.add(redact(item)));return result;}
        return value.deepCopy();
    }
    public JsonNode mapped(JsonNode value,Definition definition) {
        JsonNode result=redact(value);
        if(result==null) return null;
        for(var rule:definition.mappings()) {
            if(rule.source()!=null && Arrays.stream(rule.source().split("\\.")).anyMatch(this::sensitive)
                    && !PayloadEngine.get(result,rule.target()).isMissingNode())
                PayloadEngine.put((ObjectNode)result,rule.target(),TextNode.valueOf("***"));
        }
        return result;
    }
}
