package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.NullNode;
import lab.interfaceflow.integration.InterfaceModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PayloadEngineTest {
    private final ObjectMapper json=new ObjectMapper();
    private final PayloadEngine engine=new PayloadEngine();
    private JsonNode node(String value)throws Exception{return json.readTree(value);}

    @Test void nestedMappingDefaultAndWhitelistNeverMutateInput() throws Exception {
        var source=node("{\"user\":{\"id\":\"U1\"},\"discard\":true}");var before=source.deepCopy();
        var rules=List.of(TestDefinitions.mapping("user.id","employee.id",Transform.COPY,true),
            new MappingRule(null,"active",Transform.COPY,json.getNodeFactory().booleanNode(true),false,NullPolicy.KEEP,null,null));
        assertEquals(node("{\"employee\":{\"id\":\"U1\"},\"active\":true}"),engine.map(source,rules));
        assertEquals(before,source);assertEquals(engine.map(source,rules),engine.map(source,rules));
    }
    @Test void emptyMappingIsAnIndependentCopy()throws Exception{var input=node("{\"id\":1}");assertEquals(input,engine.map(input,List.of()));assertNotSame(input,engine.map(input,List.of()));}
    @Test void mappingCannotExpandARequestBeyond64KiB(){
        var definition=TestDefinitions.with(List.of(TestDefinitions.mapping("value","one",Transform.COPY,true),TestDefinitions.mapping("value","two",Transform.COPY,true)),List.of());
        var result=engine.prepare(json.createObjectNode().put("value","x".repeat(40000)),definition);
        assertEquals("MAPPING_FAILED",result.status());assertEquals("maxPayloadSize",result.errors().getFirst().rule());assertNull(result.mapped());
    }
    @Test void numericEnumComparesValuesRatherThanNodeRepresentation()throws Exception {
        var rule=new ValidationRule("n",Stage.SOURCE,DataType.NUMBER,true,false,null,null,null,null,List.of(node("1.0")),null,null);
        assertTrue(engine.validate(node("{\"n\":1}"),List.of(rule),Stage.SOURCE).isEmpty());
    }
    @Test void serializedNullDefaultDoesNotHideMissingRequiredSource()throws Exception {
        var rule=new MappingRule("missing","employee_id",Transform.COPY,NullNode.getInstance(),true,NullPolicy.OMIT,null,null);
        assertEquals("MAPPING_FAILED",engine.prepare(node("{}"),TestDefinitions.with(List.of(rule),List.of())).status());
    }
    @Test void scalarConversionsAreExplicit()throws Exception {
        var rules=List.of(TestDefinitions.mapping("n","number",Transform.NUMBER,true),TestDefinitions.mapping("b","boolean",Transform.BOOLEAN,true),TestDefinitions.mapping("s","text",Transform.STRING,true));
        var result=engine.map(node("{\"n\":\"12.5\",\"b\":\"false\",\"s\":42}"),rules);
        assertEquals(new BigDecimal("12.5"),result.path("number").decimalValue());assertFalse(result.path("boolean").asBoolean());assertEquals("42",result.path("text").asText());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"input\":\"bad\"}","{\"input\":true}"})
    void missingOrInvalidNumberIsMappingFailure(String payload)throws Exception {
        var definition=TestDefinitions.with(List.of(TestDefinitions.mapping("input","out",Transform.NUMBER,true)),List.of());
        var prepared=engine.prepare(node(payload),definition);assertEquals("MAPPING_FAILED",prepared.status());assertEquals("out",prepared.errors().getFirst().field());
    }
    @Test void nullPoliciesAreDistinct()throws Exception {
        var payload=node("{\"input\":null}");
        var keep=new MappingRule("input","keep",Transform.COPY,null,true,NullPolicy.KEEP,null,null);
        var omit=new MappingRule("input","omit",Transform.COPY,null,true,NullPolicy.OMIT,null,null);
        var fallback=new MappingRule("input","defaulted",Transform.COPY,node("7"),true,NullPolicy.DEFAULT,null,null);
        assertEquals(node("{\"keep\":null,\"defaulted\":7}"),engine.map(payload,List.of(keep,omit,fallback)));
        var error=new MappingRule("input","bad",Transform.COPY,null,true,NullPolicy.ERROR,null,null);
        assertEquals("MAPPING_FAILED",engine.prepare(payload,TestDefinitions.with(List.of(error),List.of())).status());
    }
    @Test void datesAreStrictAndLocaleIndependent()throws Exception {
        var date=new MappingRule("date","formatted",Transform.DATE,null,true,NullPolicy.ERROR,"uuuu-MM-dd","dd/MM/uuuu");
        assertEquals("29/02/2024",engine.map(node("{\"date\":\"2024-02-29\"}"),List.of(date)).path("formatted").asText());
        assertEquals("MAPPING_FAILED",engine.prepare(node("{\"date\":\"2025-02-29\"}"),TestDefinitions.with(List.of(date),List.of())).status());
    }
    @Test void validationStagesPreventMappingAndSendingBadOutput()throws Exception {
        var definition=TestDefinitions.with(List.of(TestDefinitions.mapping("userId","employee_id",Transform.COPY,true)),List.of(
            TestDefinitions.rule("userId",Stage.SOURCE,DataType.STRING,true),TestDefinitions.rule("employee_id",Stage.TARGET,DataType.NUMBER,true)));
        var sourceFailure=engine.prepare(node("{}"),definition);assertEquals("SOURCE_VALIDATION",sourceFailure.stage());assertNull(sourceFailure.mapped());assertEquals("required",sourceFailure.errors().getFirst().rule());
        var targetFailure=engine.prepare(node("{\"userId\":\"U1\"}"),definition);assertEquals("TARGET_VALIDATION",targetFailure.stage());assertEquals("type",targetFailure.errors().getFirst().rule());
    }
    @Test void stringAndNumberConstraintsReportEachRule()throws Exception {
        var rules=List.of(new ValidationRule("text",Stage.SOURCE,DataType.STRING,true,false,3,5,null,null,List.of(node("\"ABC\"")),null,"[A-Z]+"),
            new ValidationRule("amount",Stage.SOURCE,DataType.NUMBER,true,false,null,null,BigDecimal.ZERO,BigDecimal.TEN,null,null,null));
        var errors=engine.validate(node("{\"text\":\"a\",\"amount\":11}"),rules,Stage.SOURCE);
        assertEquals(Set.of("minLength","pattern","enum","maxValue"),errors.stream().map(e->e.rule()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(engine.validate(node("{\"text\":\"ABC\",\"amount\":5}"),rules,Stage.SOURCE).isEmpty());
    }
    @Test void requiredAndNullableAreIndependent()throws Exception {
        var nullable=new ValidationRule("value",Stage.SOURCE,DataType.STRING,true,true,null,null,null,null,null,null,null);
        assertTrue(engine.validate(node("{\"value\":null}"),List.of(nullable),Stage.SOURCE).isEmpty());
        assertEquals("required",engine.validate(node("{}"),List.of(nullable),Stage.SOURCE).getFirst().rule());
    }
    @Test void invalidPathsAndOverlappingTargetsAreRejected() {
        var overlapping=TestDefinitions.with(List.of(TestDefinitions.mapping("id","employee",Transform.COPY,true),TestDefinitions.mapping("id","employee.id",Transform.COPY,true)),List.of());
        assertThrows(ApiProblem.class,()->engine.validateDefinition(overlapping));
        assertThrows(ApiProblem.class,()->engine.validateDefinition(TestDefinitions.with(List.of(TestDefinitions.mapping("items[0]","id",Transform.COPY,true)),List.of())));
    }
    @Test void unsupportedRegexIsRejectedAtConfigurationTime() {
        var rule=new ValidationRule("value",Stage.SOURCE,DataType.STRING,false,true,null,null,null,null,null,null,"(?=secret).*" );
        assertThrows(ApiProblem.class,()->engine.validateDefinition(TestDefinitions.with(List.of(),List.of(rule))));
    }
    @Test void maskingFollowsSensitiveSourcesAcrossRenamesAndArrays()throws Exception {
        var redactor=new PayloadRedactor();var definition=TestDefinitions.with(List.of(TestDefinitions.mapping("password","opaque",Transform.COPY,true)),List.of());
        var source=node("{\"password\":\"private\",\"rows\":[{\"access_token\":\"private\"}],\"email\":\"demo@example.com\"}");
        assertFalse(redactor.redact(source).toString().contains("private"));assertEquals("private",source.path("password").asText());
        assertEquals("***",redactor.mapped(engine.map(source,definition.mappings()),definition).path("opaque").asText());
        assertEquals("***",redactor.redact(source).path("email").asText());
    }
}
