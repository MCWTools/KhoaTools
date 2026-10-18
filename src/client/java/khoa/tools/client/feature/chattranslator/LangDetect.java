package khoa.tools.client.feature.chattranslator;
import java.util.Locale;
final class LangDetect {
 private LangDetect(){}
 static boolean worthTranslating(String s){return s != null && s.chars().anyMatch(Character::isLetter);}
 static String detect(String s){
  if(s.codePoints().anyMatch(c->c>=0x3040&&c<=0x30ff)) return "ja";
  if(s.codePoints().anyMatch(c->c>=0xac00&&c<=0xd7af)) return "ko";
  if(s.codePoints().anyMatch(c->c>=0x4e00&&c<=0x9fff)) return "zh";
  String x=s.toLowerCase(Locale.ROOT);
  if(x.matches(".*\\b(the|and|you|this|is|are)\\b.*")) return "en";
  if(x.matches(".*\\b(và|là|không|bạn|tôi|một)\\b.*")) return "vi";
  return "unknown";
 }
}
