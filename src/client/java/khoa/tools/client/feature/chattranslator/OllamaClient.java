package khoa.tools.client.feature.chattranslator;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
final class OllamaClient {
 private OllamaClient(){}
 static CompletableFuture<String> translate(String text,String language){
  String body="{\"model\":\""+escape(ChatTranslatorConfig.model())+"\",\"prompt\":\"Translate to "+escape(language)+": "+escape(text)+"\",\"stream\":false}";
  HttpRequest r=HttpRequest.newBuilder(URI.create(ChatTranslatorConfig.url()+"/api/generate")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
  return HttpClient.newHttpClient().sendAsync(r,HttpResponse.BodyHandlers.ofString()).thenApply(x->{String raw=x.body(); int p=raw.indexOf("\"response\""); if(p<0)return ""; p=raw.indexOf(':',p)+1; int e=raw.indexOf("\"",p+1); return e<0?"":raw.substring(p+1,e).replace("\\n","\n");});
 }
 static void pull(Consumer<String> progress) { progress.accept("Automatic model download is not implemented; install the model with Ollama first."); }
 private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
}
