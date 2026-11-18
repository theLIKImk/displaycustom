package io.github.displaycustom;

import com.google.gson.*;
import java.util.*;

/** Optional human-facing metadata; model.name remains the file/command ID. */
public record ModelInfo(String name,String author,String description,String contact,String copyright) {
    public static final Set<String> ROLES=Set.of("body","head","arm","leg","foot");
    public static final Set<String> STATES=Set.of("stand","sit","walk","run","sneak","sneak_walk","climb","jump","fall","swim","fly","sleep","hurt");
    private static final Map<String,Integer> LIMITS=Map.of("name",128,"author",128,"description",2048,"contact",256,"copyright",1024);
    public void validate(){check("name",name);check("author",author);check("description",description);check("contact",contact);check("copyright",copyright);}
    private static void check(String field,String text){if(text!=null&&(text.length()>LIMITS.get(field)||text.codePoints().anyMatch(c->(c<32&&c!='\n'&&c!='\t')||c==127||c==167)))throw Messages.failure("error.info.field", field);}
    public static void validateJson(JsonElement value){
        if(value==null||value.isJsonNull())return;
        if(!value.isJsonObject())throw Messages.failure("error.info.object");
        for(var e:value.getAsJsonObject().entrySet()){
            if(!LIMITS.containsKey(e.getKey())||!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isString())throw Messages.failure("error.info.fields");
            check(e.getKey(),e.getValue().getAsString());
        }
    }
    public static void validateTagsJson(JsonElement v){if(v==null||v.isJsonNull())return;if(!v.isJsonArray())throw Messages.failure("error.info.tag-array");for(var tag:v.getAsJsonArray())if(!tag.isJsonPrimitive()||!tag.getAsJsonPrimitive().isString())throw Messages.failure("error.info.tag-array");}
    public static void validateTags(List<String> tags,Set<String> allowed,int max,int version){
        if(tags==null)return;
        if(version<7||tags.size()>max||new HashSet<>(tags).size()!=tags.size()||tags.stream().anyMatch(t->t==null||!allowed.contains(t)))throw Messages.failure("error.info.tags", allowed);
    }
}
