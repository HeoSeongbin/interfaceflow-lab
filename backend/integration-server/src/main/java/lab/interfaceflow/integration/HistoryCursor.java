package lab.interfaceflow.integration;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class HistoryCursor {
 record Position(Instant createdAt,String id){}
 static String encode(Instant time,String id,String filter){return Base64.getUrlEncoder().withoutPadding().encodeToString((time+"|"+id+"|"+filter).getBytes(StandardCharsets.UTF_8));}
 static Position decode(String value,String filter){
  try{
   if(value.length()>512||!value.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException();
   var parts=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8).split("\\|",-1);
   if(parts.length!=3||!lab.interfaceflow.common.Simulations.validId(parts[1])||!filter.equals(parts[2]))throw new IllegalArgumentException();
   Instant time=Instant.parse(parts[0]);
   if(time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))||time.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z")))throw new IllegalArgumentException();
   return new Position(time,parts[1]);
  }catch(Exception e){throw new ApiProblem(400,"INVALID_CURSOR","필터를 변경했거나 올바르지 않은 커서입니다. 처음부터 조회하세요.");}
 }
}
