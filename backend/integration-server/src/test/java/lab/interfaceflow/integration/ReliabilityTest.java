package lab.interfaceflow.integration;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import lab.interfaceflow.common.CanonicalJson;
import static org.junit.jupiter.api.Assertions.*;
class ReliabilityTest {
 @Test void canonicalObjectsAndNumbersPreserveArrayMeaning() throws Exception {
  var json=new ObjectMapper();
  assertEquals(CanonicalJson.hash(json.readTree("{\"b\":1.00,\"a\":[1,2]}")),CanonicalJson.hash(json.readTree("{\"a\":[1.0,2],\"b\":1}")));
  assertNotEquals(CanonicalJson.hash(json.readTree("[1,2]")),CanonicalJson.hash(json.readTree("[2,1]")));
  assertNotEquals(CanonicalJson.hash(json.readTree("1")),CanonicalJson.hash(json.readTree("\"1\"")));
 }
 @Test void encryptedReplayUsesRandomNonceAndAuthenticatesRequestIdentity(){
  var cipher=new ReplayCipher(java.util.Base64.getEncoder().encodeToString(new byte[32]));
  String a=cipher.encrypt("sensitive-original","id-a"),b=cipher.encrypt("sensitive-original","id-a");
  assertNotEquals(a,b);assertFalse(a.contains("sensitive-original"));assertEquals("sensitive-original",cipher.decrypt(a,"id-a"));
  assertThrows(IllegalStateException.class,()->cipher.decrypt(a,"id-b"));
  assertThrows(ApiProblem.class,()->new ReplayCipher("").encrypt("data","id"));
 }
 @Test void retryClassificationAndBackoffAreBounded(){
  assertFalse(DeliveryStore.retryable("FAILED",400,"TARGET_HTTP_ERROR"));assertFalse(DeliveryStore.retryable("VALIDATION_FAILED",null,"VALIDATION_ERROR"));
  assertTrue(DeliveryStore.retryable("UNKNOWN",null,"TARGET_IO_ERROR"));assertTrue(DeliveryStore.retryable("UNKNOWN",500,"TARGET_HTTP_ERROR"));
  assertTrue(DeliveryStore.retryable("FAILED",429,"TARGET_HTTP_ERROR"));
  for(int a=1;a<=10;a++){long delay=DeliveryStore.backoff(a);assertTrue(delay>=250&&delay<=30000);}
 }
}
