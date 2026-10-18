package khoa.tools.client.feature.chattranslator;
import java.util.List;
import khoa.tools.client.config.KhoaConfig;
final class ChatTranslatorConfig {
 static final String SECTION="chattranslator", DEFAULT_MODEL="qwen2.5:1.5b";
 static final List<String> LANGUAGES=List.of("English","Vietnamese","Japanese","Korean","Chinese","French","German","Spanish");
 private ChatTranslatorConfig(){}
 static int target(){return Math.floorMod(KhoaConfig.getInt(SECTION,"target",0),LANGUAGES.size());}
 static void setTarget(int v){KhoaConfig.put(SECTION,"target",v);}
 static String targetName(){return LANGUAGES.get(target());}
 static String targetCode(){return new String[]{"en","vi","ja","ko","zh","fr","de","es"}[target()];}
 static String model(){return KhoaConfig.getString(SECTION,"model",DEFAULT_MODEL);}
 static void setModel(String v){KhoaConfig.put(SECTION,"model",v);}
 static String url(){return KhoaConfig.getString(SECTION,"url","http://localhost:11434");}
 static void setUrl(String v){KhoaConfig.put(SECTION,"url",v);}
 static boolean auto(){return KhoaConfig.getBool(SECTION,"auto",false);}
 static void setAuto(boolean v){KhoaConfig.put(SECTION,"auto",v);}
 static boolean pluginFormats(){return KhoaConfig.getBool(SECTION,"pluginformats",true);}
 static void setPluginFormats(boolean v){KhoaConfig.put(SECTION,"pluginformats",v);}
}
