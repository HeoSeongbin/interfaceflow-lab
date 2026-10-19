package lab.interfaceflow.integration;

import java.util.List;
import lab.interfaceflow.integration.InterfaceModels.*;

final class TestDefinitions {
    static Definition user(){return with(List.of(mapping("userId","employee_id",Transform.COPY,true)),List.of(rule("userId",Stage.SOURCE,DataType.STRING,true)));}
    static MappingRule mapping(String source,String target,Transform transform,boolean required){return new MappingRule(source,target,transform,null,required,NullPolicy.KEEP,null,null);}
    static ValidationRule rule(String field,Stage stage,DataType type,boolean required){return new ValidationRule(field,stage,type,required,false,null,null,null,null,null,null,null);}
    static Definition with(List<MappingRule> mappings,List<ValidationRule> validations){return new Definition("IF-USER-001","테스트","","SYSTEM_A","SYSTEM_B","POST","/api/receive",true,1000,1,1,mappings,validations);}
}
