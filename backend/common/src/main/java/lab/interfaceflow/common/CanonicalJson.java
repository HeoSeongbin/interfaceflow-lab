package lab.interfaceflow.common;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class CanonicalJson {
 private static final ObjectMapper JSON=new ObjectMapper();
 private CanonicalJson(){}
 public static String hash(JsonNode node){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(node).getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e){throw new IllegalStateException("Cannot hash JSON",e);}
 }
 public static String canonical(JsonNode n){
  if(n.isNumber())return n.decimalValue().stripTrailingZeros().toString();
  if(n.isObject()){
   var keys=new TreeSet<String>();n.fieldNames().forEachRemaining(keys::add);
   var parts=new ArrayList<String>();for(String key:keys)parts.add(quote(key)+":"+canonical(n.get(key)));
   return "{"+String.join(",",parts)+"}";
  }
  if(n.isArray()){var parts=new ArrayList<String>();for(var child:n)parts.add(canonical(child));return "["+String.join(",",parts)+"]";}
  return n.toString();
 }
 private static String quote(String value){try{return JSON.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
