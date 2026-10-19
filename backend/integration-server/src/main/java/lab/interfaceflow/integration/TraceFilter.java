package lab.interfaceflow.integration;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class TraceFilter extends OncePerRequestFilter {
 private static final Logger log=LoggerFactory.getLogger(TraceFilter.class);
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  String trace=req.getHeader("X-Trace-Id");
  if(trace==null || !trace.matches("[a-f0-9]{32}")) trace=UUID.randomUUID().toString().replace("-","");
  MDC.put("traceId",trace);res.setHeader("X-Trace-Id",trace);
  String requestId=req.getHeader("X-Request-Id");
  if(requestId!=null && requestId.matches("[A-Za-z0-9_-]{1,80}")) MDC.put("requestId",requestId);
  String attemptId=req.getHeader("X-Attempt-Id");
  if(attemptId!=null && attemptId.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) MDC.put("attemptId",attemptId);
  long start=System.nanoTime();
  try {
   if(req.getContentLengthLong()>65536){rejectOversize(res,trace);return;}
   if(java.util.Set.of("POST","PUT","PATCH").contains(req.getMethod()) && req.getRequestURI().startsWith("/api/")) {
    byte[] body=req.getInputStream().readNBytes(65537);
    if(body.length>65536){rejectOversize(res,trace);return;}
    var wrapped=new HttpServletRequestWrapper(req){
     @Override public ServletInputStream getInputStream(){
      var input=new java.io.ByteArrayInputStream(body);
      return new ServletInputStream(){
       public int read(){return input.read();}
       public boolean isFinished(){return input.available()==0;}
       public boolean isReady(){return true;}
       public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous API only");}
      };
     }
    };
    chain.doFilter(wrapped,res);
   } else chain.doFilter(req,res);
  } finally {log.info("HTTP completed status={} durationMs={}",res.getStatus(),(System.nanoTime()-start)/1000000);MDC.clear();}
 }
 private void rejectOversize(HttpServletResponse response,String traceId) throws IOException {
  response.setStatus(413);
  response.setContentType("application/json");
  response.setCharacterEncoding("UTF-8");
  response.getWriter().write("{\"code\":\"PAYLOAD_TOO_LARGE\",\"traceId\":\""+traceId+"\"}");
 }
}
