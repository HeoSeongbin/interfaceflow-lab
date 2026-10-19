package lab.interfaceflow.integration;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Component
public class ReplayCipher {
 private final byte[] key;
 public ReplayCipher(@Value("${lab.replay-key:}") String encoded){
  try{key=encoded.isBlank()?null:Base64.getDecoder().decode(encoded);if(key!=null && key.length!=32)throw new IllegalArgumentException();}
  catch(IllegalArgumentException e){throw new IllegalStateException("REPLAY_KEY must be a base64 encoded 32-byte key");}
 }
 public String encrypt(String value,String id){return crypt(value,id,true);}
 public String decrypt(String value,String id){return crypt(value,id,false);}
 private String crypt(String value,String id,boolean encrypt){
  if(key==null)throw new ApiProblem(503,"REPLAY_KEY_REQUIRED","REPLAY_KEY를 설정하세요.");
  try{
   byte[] nonce=new byte[12],input;
   if(encrypt){new SecureRandom().nextBytes(nonce);input=value.getBytes(StandardCharsets.UTF_8);}
   else{byte[] all=Base64.getDecoder().decode(value);System.arraycopy(all,0,nonce,0,12);input=java.util.Arrays.copyOfRange(all,12,all.length);}
   var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
   cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));byte[] result=cipher.doFinal(input);
   if(!encrypt)return new String(result,StandardCharsets.UTF_8);
   byte[] all=new byte[12+result.length];System.arraycopy(nonce,0,all,0,12);System.arraycopy(result,0,all,12,result.length);return Base64.getEncoder().encodeToString(all);
  }catch(Exception e){throw new IllegalStateException("Protected replay data unavailable");}
 }
}
