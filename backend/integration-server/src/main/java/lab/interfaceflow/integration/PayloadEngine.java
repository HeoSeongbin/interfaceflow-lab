package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import lab.interfaceflow.common.Contracts.FieldError;
import lab.interfaceflow.integration.InterfaceModels.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.*;

@Component
public class PayloadEngine {
    public record Prepared(JsonNode mapped, String status, String stage, List<FieldError> errors) {
        public boolean valid(){return errors.isEmpty();}
    }

    public void validateDefinition(Definition definition) {
        Set<String> targets=new HashSet<>();
        for (var rule:definition.mappings()) {
            path(rule.target());
            if (rule.source()!=null && !rule.source().isBlank()) path(rule.source());
            if ((rule.source()==null || rule.source().isBlank()) && rule.defaultValue()==null)
                throw config("추가 필드는 defaultValue가 필요합니다.");
            for (String prior:targets) {
                if(prior.equals(rule.target()) || prior.startsWith(rule.target()+".") || rule.target().startsWith(prior+"."))
                    throw config("대상 경로가 중복되거나 상위 경로와 충돌합니다.");
            }
            targets.add(rule.target());
            if(rule.transform()==Transform.DATE){formatter(rule.sourceFormat());formatter(rule.targetFormat());}
            if(rule.nullPolicy()==NullPolicy.DEFAULT && !hasDefault(rule)) throw config("DEFAULT 정책에는 Null이 아닌 기본값이 필요합니다.");
        }
        Set<String> validations=new HashSet<>();
        for(var rule:definition.validations()) {
            path(rule.field());
            if(!validations.add(rule.stage()+":"+rule.field())) throw config("단계별 필드 규칙이 중복됩니다.");
            if(rule.minLength()!=null && rule.maxLength()!=null && rule.minLength()>rule.maxLength()) throw config("길이 범위가 잘못되었습니다.");
            if(rule.minValue()!=null && rule.maxValue()!=null && rule.minValue().compareTo(rule.maxValue())>0) throw config("숫자 범위가 잘못되었습니다.");
            if(rule.pattern()!=null) {
                try {com.google.re2j.Pattern.compile(rule.pattern());}
                catch(RuntimeException e){throw config("지원하지 않는 정규식입니다. RE2 구문을 사용하세요.");}
            }
            if(rule.dateFormat()!=null) formatter(rule.dateFormat());
            if((rule.pattern()!=null || rule.dateFormat()!=null || rule.minLength()!=null || rule.maxLength()!=null) && rule.type()!=DataType.STRING)
                throw config("문자열 검사는 STRING 타입에만 지정하세요.");
            if((rule.minValue()!=null || rule.maxValue()!=null) && rule.type()!=DataType.NUMBER) throw config("숫자 범위는 NUMBER 타입에만 지정하세요.");
        }
    }

    public Prepared prepare(JsonNode original, Definition definition) {
        var errors=validate(original,definition.validations(),Stage.SOURCE);
        if(!errors.isEmpty()) return new Prepared(null,"VALIDATION_FAILED","SOURCE_VALIDATION",errors);
        JsonNode mapped;
        try {mapped=map(original,definition.mappings());}
        catch(MappingFailure e) {return new Prepared(null,"MAPPING_FAILED","FIELD_MAPPING",List.of(new FieldError(e.field,"mapping","필드 경로·타입·날짜·Null 정책을 확인하세요.","MAPPING")));}
        if(mapped.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536)
            return new Prepared(null,"MAPPING_FAILED","FIELD_MAPPING",List.of(new FieldError("$","maxPayloadSize","변환 결과는 64KB 이하여야 합니다.","MAPPING")));
        errors=validate(mapped,definition.validations(),Stage.TARGET);
        if(!errors.isEmpty()) return new Prepared(mapped,"VALIDATION_FAILED","TARGET_VALIDATION",errors);
        return new Prepared(mapped,"READY","TARGET_VALIDATED",List.of());
    }

    public JsonNode map(JsonNode original,List<MappingRule> rules) {
        if(rules.isEmpty()) return original.deepCopy();
        ObjectNode result=JsonNodeFactory.instance.objectNode();
        for(var rule:rules) {
            try {
                JsonNode value=rule.source()==null || rule.source().isBlank()?rule.defaultValue():get(original,rule.source());
                if(value==null)value=MissingNode.getInstance();
                if(value.isMissingNode()) {
                    if(hasDefault(rule)) value=rule.defaultValue();
                    else if(rule.required()) throw new IllegalArgumentException();
                    else continue;
                }
                if(value.isNull()) {
                    switch(rule.nullPolicy()) {
                        case OMIT -> {continue;}
                        case ERROR -> throw new IllegalArgumentException();
                        case DEFAULT -> value=rule.defaultValue();
                        case KEEP -> {}
                    }
                }
                put(result,rule.target(),value==null || value.isNull()?NullNode.getInstance():convert(value,rule));
            } catch(RuntimeException e){throw new MappingFailure(rule.target());}
        }
        return result;
    }

    private JsonNode convert(JsonNode value,MappingRule rule) {
        return switch(rule.transform()) {
            case COPY -> value.deepCopy();
            case STRING -> {
                if(!value.isValueNode()) throw new IllegalArgumentException();
                yield TextNode.valueOf(value.asText());
            }
            case NUMBER -> {
                if(!value.isNumber() && !value.isTextual()) throw new IllegalArgumentException();
                yield DecimalNode.valueOf(new BigDecimal(value.asText()));
            }
            case BOOLEAN -> {
                if(value.isBoolean()) yield value;
                if(!value.isTextual() || !(value.textValue().equals("true") || value.textValue().equals("false"))) throw new IllegalArgumentException();
                yield BooleanNode.valueOf(Boolean.parseBoolean(value.textValue()));
            }
            case DATE -> {
                if(!value.isTextual()) throw new IllegalArgumentException();
                var date=formatter(rule.sourceFormat()).parseBest(value.textValue(),LocalDateTime::from,LocalDate::from);
                yield TextNode.valueOf(formatter(rule.targetFormat()).format(date));
            }
        };
    }

    public List<FieldError> validate(JsonNode payload,List<ValidationRule> rules,Stage stage) {
        var errors=new ArrayList<FieldError>();
        for(var rule:rules) {
            if(rule.stage()!=stage) continue;
            JsonNode value=get(payload,rule.field());
            if(value.isMissingNode() || value.isNull() || value.isTextual() && value.asText().isBlank()) {
                if(rule.required() && (value.isMissingNode() || value.isTextual() && value.asText().isBlank())) issue(errors,rule,"required","필수값입니다.");
                else if(value.isNull() && !rule.nullable()) issue(errors,rule,"null","Null을 허용하지 않습니다.");
                if(value.isMissingNode() || value.isNull()) continue;
            }
            boolean correct=switch(rule.type()) {
                case STRING -> value.isTextual(); case NUMBER -> value.isNumber();
                case BOOLEAN -> value.isBoolean(); case OBJECT -> value.isObject(); case ARRAY -> value.isArray();
            };
            if(!correct){issue(errors,rule,"type","필드 타입이 올바르지 않습니다.");continue;}
            if(value.isTextual()) {
                int length=value.asText().codePointCount(0,value.asText().length());
                if(rule.minLength()!=null && length<rule.minLength()) issue(errors,rule,"minLength","최소 길이보다 짧습니다.");
                if(rule.maxLength()!=null && length>rule.maxLength()) issue(errors,rule,"maxLength","최대 길이를 초과했습니다.");
                if(rule.pattern()!=null && !com.google.re2j.Pattern.compile(rule.pattern()).matcher(value.asText()).matches()) issue(errors,rule,"pattern","형식이 올바르지 않습니다.");
                if(rule.dateFormat()!=null) {
                    try{formatter(rule.dateFormat()).parseBest(value.asText(),LocalDateTime::from,LocalDate::from);}
                    catch(RuntimeException e){issue(errors,rule,"dateFormat","날짜 형식이 올바르지 않습니다.");}
                }
            }
            if(value.isNumber()) {
                if(rule.minValue()!=null && value.decimalValue().compareTo(rule.minValue())<0) issue(errors,rule,"minValue","최솟값보다 작습니다.");
                if(rule.maxValue()!=null && value.decimalValue().compareTo(rule.maxValue())>0) issue(errors,rule,"maxValue","최댓값을 초과했습니다.");
            }
            if(rule.enumValues()!=null && rule.enumValues().stream().noneMatch(candidate->candidate!=null && (candidate.isNumber() && value.isNumber()?candidate.decimalValue().compareTo(value.decimalValue())==0:candidate.equals(value)))) issue(errors,rule,"enum","허용된 값이 아닙니다.");
        }
        return List.copyOf(errors);
    }

    private void issue(List<FieldError> errors,ValidationRule rule,String name,String message) {
        errors.add(new FieldError(rule.field(),name,message,rule.stage().name()));
    }
    static JsonNode get(JsonNode root,String path) {
        JsonNode current=root;
        for(String part:path.split("\\.")) {if(current==null || !current.isObject()) return MissingNode.getInstance();current=current.path(part);}
        return current==null?MissingNode.getInstance():current;
    }
    static void put(ObjectNode root,String path,JsonNode value) {
        String[] parts=path.split("\\."); ObjectNode current=root;
        for(int i=0;i<parts.length-1;i++) {
            JsonNode node=current.get(parts[i]);
            if(node==null){node=current.objectNode();current.set(parts[i],node);}
            if(!node.isObject()) throw new IllegalArgumentException();
            current=(ObjectNode)node;
        }
        current.set(parts[parts.length-1],value.deepCopy());
    }
    private void path(String value) {
        if(value==null || value.length()>200 || !value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*){0,15}")) throw config("경로는 점으로 구분한 객체 필드만 지원합니다(최대 16단계).");
    }
    private DateTimeFormatter formatter(String value) {
        try {
            if(value==null || value.isBlank() || value.length()>100) throw new IllegalArgumentException();
            return DateTimeFormatter.ofPattern(value,Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
        } catch(RuntimeException e){throw config("날짜 패턴을 확인하세요. 연도는 uuuu를 사용하세요.");}
    }
    private ApiProblem config(String message){return new ApiProblem(400,"INVALID_CONFIGURATION",message);}
    private boolean hasDefault(MappingRule rule){return rule.defaultValue()!=null && !rule.defaultValue().isNull();}
    private static class MappingFailure extends RuntimeException {
        final String field; MappingFailure(String field){this.field=field;}
    }
}
