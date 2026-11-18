package io.github.displaycustom;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;
import org.joml.Matrix4f;

/** JSON v1-v6: typed displays, grouped TRS layers and timed sound events. */
public record Model(int version, String name, List<Part> parts, Map<String, Animation> animations, List<Group> groups, Playback playback, ModelInfo info) {
    public static final int MAX_PARTS = 1024;
    public static final int MIN_VERSION = 1;
    public static final int MAX_VERSION = 9;
    public static final String SUPPORTED_VERSIONS = MIN_VERSION + "–" + MAX_VERSION;
    public Model(int version,String name,List<Part> parts,Map<String,Animation> animations,List<Group> groups,Playback playback){this(version,name,parts,animations,groups,playback,null);}
    public Model(int version, String name, List<Part> parts) { this(version,name,parts,null,null,null); }
    public Model(int version,String name,List<Part> parts,Map<String,Animation> animations){this(version,name,parts,animations,null,null);}
    public record Group(String id,float[] position,float[] rotation,float[] scale,String group,String kind,float[] pivot,Float length,List<String> tags) {
        public Group(String id,float[] p,float[] r,float[] s,String group,String kind,float[] pivot,Float length){this(id,p,r,s,group,kind,pivot,length,null);}
        public Group(String id,float[] p,float[] r,float[] s){this(id,p,r,s,null,null,null,null);}
    }
    public record Playback(List<String> animations) {}
    public record SoundEvent(float time,String sound,float volume,float pitch) {}
    public record Trigger(String animation,SoundEvent event,double at) {}
    public record Part(String id, String material, float[] position, float[] rotation,
                       float[] scale, float[] swing, float phase, String itemModel,
                       String type, String itemMaterial, String text, String group, TextStyle textStyle,List<String> tags,String heldItem) {
        public Part(String id,String material,float[] p,float[] r,float[] s,float[] swing,float phase,String itemModel,String type,String itemMaterial,String text,String group,TextStyle textStyle,List<String> tags){this(id,material,p,r,s,swing,phase,itemModel,type,itemMaterial,text,group,textStyle,tags,null);}
        public Part(String id,String material,float[] p,float[] r,float[] s,float[] swing,float phase,String itemModel,String type,String itemMaterial,String text,String group,TextStyle textStyle){this(id,material,p,r,s,swing,phase,itemModel,type,itemMaterial,text,group,textStyle,null);}
        public Part {
            if(swing == null) swing=new float[]{0,0,0};
            if(material == null)material="IRON_BLOCK";
        }
        public Part(String id,String material,float[] position,float[] rotation,float[] scale,float[] swing,float phase) {
            this(id,material,position,rotation,scale,swing,phase,null,null,null,null,null,null);
        }
        public Part(String id,String material,float[] position,float[] rotation,float[] scale,float[] swing,float phase,String itemModel) {
            this(id,material,position,rotation,scale,swing,phase,itemModel,null,null,null,null,null);
        }
        public Part(String id,String material,float[] position,float[] rotation,float[] scale,float[] swing,float phase,String itemModel,String type,String itemMaterial,String text){this(id,material,position,rotation,scale,swing,phase,itemModel,type,itemMaterial,text,null,null);}
        public Part(String id,String material,float[] position,float[] rotation,float[] scale,float[] swing,float phase,String itemModel,String type,String itemMaterial,String text,String group){this(id,material,position,rotation,scale,swing,phase,itemModel,type,itemMaterial,text,group,null);}
        public TextStyle appearance(){return textStyle==null?TextStyle.DEFAULT:textStyle;}
        public String entityType(){return type==null ? itemModel==null ? "block" : "item" : type;}
        public String carrier(){return itemMaterial==null ? "PAPER" : itemMaterial;}
    }
    public record Keyframe(float time,float[] position,float[] rotation,float[] scale,String text,TextStyle textStyle,Boolean visible,String interpolation) {
        public Keyframe(float t,float[] p,float[] r,float[] s,String text,TextStyle style){this(t,p,r,s,text,style,null,null);}
        public Keyframe(float t,float[] p,float[] r,float[] s){this(t,p,r,s,null,null,null,null);}
    }
    public record TextValue(String text,TextStyle style) {}
    public record ItemUse(String state,List<String> items,String timing) {
        public ItemUse(String state,List<String> items){this(state,items,null);}
    }
    public record Animation(float duration,boolean loop,Map<String,List<Keyframe>> tracks,List<SoundEvent> sounds,String interpolation,List<String> tags,ItemUse itemUse,Boolean parallel) {
        public Animation(float d,boolean l,Map<String,List<Keyframe>> t,List<SoundEvent> s,String interpolation,List<String> tags,ItemUse itemUse){this(d,l,t,s,interpolation,tags,itemUse,null);}
        public Animation(float d,boolean l,Map<String,List<Keyframe>> t,List<SoundEvent> s,String interpolation,List<String> tags){this(d,l,t,s,interpolation,tags,null);}
        public Animation(float d,boolean l,Map<String,List<Keyframe>> t,List<SoundEvent> s,String interpolation){this(d,l,t,s,interpolation,null);}
        public Animation(float d,boolean l,Map<String,List<Keyframe>> t,List<SoundEvent> s){this(d,l,t,s,null);}
        public Animation(float duration,boolean loop,Map<String,List<Keyframe>> tracks){this(duration,loop,tracks,null);}
        public boolean isHurt(){return tags!=null&&tags.contains("hurt");}
        public boolean runsInParallel(){return parallel!=null?parallel:itemUse!=null||isHurt();}
    }

    public static Model read(Reader reader) {
        JsonElement element=JsonParser.parseReader(reader);
        if(!element.isJsonObject())throw Messages.failure("error.model.object");
        JsonObject root=element.getAsJsonObject();
        ModelInfo.validateJson(root.get("info"));
        for(String field:List.of("parts","groups"))if(root.has(field)&&root.get(field).isJsonArray())for(var node:root.getAsJsonArray(field))if(node.isJsonObject())ModelInfo.validateTagsJson(node.getAsJsonObject().get("tags"));
        if(root.has("animations")&&root.get("animations").isJsonObject())for(var clip:root.getAsJsonObject("animations").entrySet())if(clip.getValue().isJsonObject())ModelInfo.validateTagsJson(clip.getValue().getAsJsonObject().get("tags"));
        // Migrate 0.2 colors in memory; never rewrite the user's source file.
        if(root.has("parts") && root.get("parts").isJsonArray()) for(JsonElement entry:root.getAsJsonArray("parts")) {
            if(entry.isJsonObject()) {
                var p=entry.getAsJsonObject();
                for(String field:List.of("type","itemMaterial","text"))if(p.has(field) && !p.get(field).isJsonNull() && (!p.get(field).isJsonPrimitive() || !p.get(field).getAsJsonPrimitive().isString()))throw Messages.failure("error.model.string", field);
                TextStyle.validateJson(p.get("textStyle"));
                if(p.has("color") && !p.get("color").isJsonNull())p.addProperty("itemModel",Palette.key(p.get("color").getAsString()));
                p.remove("color");
            }
        }
        if(root.has("animations") && root.get("animations").isJsonObject())for(var clip:root.getAsJsonObject("animations").entrySet()) {
            if(clip.getValue().isJsonObject() && clip.getValue().getAsJsonObject().has("sounds") && clip.getValue().getAsJsonObject().get("sounds").isJsonArray())
                for(var event:clip.getValue().getAsJsonObject().getAsJsonArray("sounds"))if(event.isJsonObject()) {
                    var sound=event.getAsJsonObject();
                    if(!sound.has("time") || !sound.get("time").isJsonPrimitive() || !sound.getAsJsonPrimitive("time").isNumber())throw Messages.failure("error.model.sound-time");
                    if(!sound.has("sound") || !sound.get("sound").isJsonPrimitive() || !sound.getAsJsonPrimitive("sound").isString())throw Messages.failure("error.model.sound-key");
                    for(String field:List.of("volume","pitch"))if(sound.has(field) && (!sound.get(field).isJsonPrimitive() || !sound.getAsJsonPrimitive(field).isNumber()))throw Messages.failure("error.model.sound-values");
                    if(!sound.has("volume"))sound.addProperty("volume",1);
                    if(!sound.has("pitch"))sound.addProperty("pitch",1);
                }
        }
        if(root.has("animations")&&root.get("animations").isJsonObject())for(var entry:root.getAsJsonObject("animations").entrySet())if(entry.getValue().isJsonObject()){
            var clipObject=entry.getValue().getAsJsonObject();validateInterpolationJson(clipObject.get("interpolation"));
            var parallel=clipObject.get("parallel");
            if(parallel!=null&&!parallel.isJsonNull()&&(!parallel.isJsonPrimitive()||!parallel.getAsJsonPrimitive().isBoolean()))throw Messages.failure("error.model.parallel-boolean");
            var tracks=clipObject.get("tracks");
            if(tracks!=null&&tracks.isJsonObject())for(var track:tracks.getAsJsonObject().entrySet())if(track.getValue().isJsonArray())for(var f:track.getValue().getAsJsonArray())if(f.isJsonObject()){
                var frame=f.getAsJsonObject();validateInterpolationJson(frame.get("interpolation"));if(frame.has("visible")&&!frame.get("visible").isJsonNull()&&(!frame.get("visible").isJsonPrimitive()||!frame.getAsJsonPrimitive("visible").isBoolean()))throw Messages.failure("error.model.visible-boolean");TextStyle.validateJson(frame.get("textStyle"));
                if(frame.has("text")&&!frame.get("text").isJsonNull()&&(!frame.get("text").isJsonPrimitive()||!frame.getAsJsonPrimitive("text").isString()))throw Messages.failure("error.model.frame-text-type");
            }
        }
        return new Gson().fromJson(root,Model.class).validate();
    }
    public Model validate() {
        if(version<MIN_VERSION || version>MAX_VERSION)
            throw Messages.failure("error.model.version", version, SUPPORTED_VERSIONS);
        if(info!=null){if(version<7)throw Messages.failure("error.model.info-version");info.validate();}
        if(version<3 && (groups!=null || playback!=null || parts!=null&&parts.stream().anyMatch(p->p!=null&&p.group!=null) || animations!=null&&animations.values().stream().anyMatch(a->a!=null&&a.sounds!=null)))throw Messages.failure("error.model.group-version");
        if(name==null || !name.matches("[a-z0-9_-]{1,48}"))throw Messages.failure("error.model.name");
        if(parts==null || parts.isEmpty() || ModelLimits.exceeds(parts.size(),ModelLimits.maxParts))throw Messages.failure("error.model.part-limit", ModelLimits.describe("max-parts",ModelLimits.maxParts));
        Set<String> ids=new HashSet<>();
        Set<String> heldHands=new HashSet<>();
        for(Part p:parts) {
            if(p!=null)ModelInfo.validateTags(p.tags,ModelInfo.ROLES,1,version);
            if(p==null || p.id==null || !p.id.matches("[a-zA-Z0-9_-]{1,48}") || Set.of("__proto__","prototype","constructor").contains(p.id) || !ids.add(p.id))throw Messages.failure("error.model.part-id");
            if(version==1 && (p.type!=null || p.itemMaterial!=null || p.text!=null))throw Messages.failure("error.model.part-version");
            if(p.textStyle!=null && (version<4 || !p.entityType().equals("text")))throw Messages.failure("error.model.text-style-version");
            if(p.material==null || !p.material.matches("[A-Z_]+"))throw Messages.failure("error.model.material");
            if(!Set.of("block","item","text").contains(p.entityType()))throw Messages.failure("error.model.type");
            if(p.heldItem!=null&&(version<8||!p.entityType().equals("item")||p.itemModel!=null||
                !Set.of("left","right").contains(p.heldItem)||!heldHands.add(p.heldItem)))
                throw Messages.failure("error.model.held-item");
            if(p.itemModel!=null && !p.entityType().equals("item"))throw Messages.failure("error.model.item-model-type");
            if(p.itemMaterial!=null && (!p.entityType().equals("item") || !p.itemMaterial.matches("[A-Z0-9_]+")))throw Messages.failure("error.model.item-material");
            if(p.entityType().equals("text")) {
                if(p.text==null || p.text.length()>1024 || p.text.split("\\n",-1).length>16)throw Messages.failure("error.model.text-size");
            } else if(p.text!=null)throw Messages.failure("error.model.text-type");
            if(p.itemModel!=null && !p.itemModel.matches("[a-z0-9_-]+:[a-z0-9_-]+(?:/[a-z0-9_-]+)*"))throw Messages.failure("error.model.item-model-key");
            if(p.itemModel!=null && !Palette.validReference(p.itemModel))throw Messages.failure("error.model.palette-key", p.itemModel);
            validateTRS(p.position,p.rotation,p.scale);
            vector(p.swing,-180,180,"swing");
            if(!Float.isFinite(p.phase) || Math.abs(p.phase)>360)throw Messages.failure("error.model.phase");
        }
        Set<String> groupIds=new HashSet<>();
        if(groups!=null) {
            if(groups.size()>32)throw Messages.failure("error.model.group-limit");
            for(Group g:groups){
                if(g!=null)ModelInfo.validateTags(g.tags,ModelInfo.ROLES,1,version);
                if(g==null || g.id==null || !g.id.matches("[a-zA-Z0-9_-]{1,48}") || Set.of("__proto__","prototype","constructor").contains(g.id) || !ids.add(g.id))throw Messages.failure("error.model.group-id");
                if(version<5 && (g.group!=null||g.kind!=null||g.pivot!=null||g.length!=null))throw Messages.failure("error.model.bone-version");
                if(g.kind!=null&&!Set.of("group","bone").contains(g.kind))throw Messages.failure("error.model.node-type");
                if(g.pivot!=null)vector(g.pivot,-32,32,"pivot");
                if(g.length!=null&&(!Float.isFinite(g.length)||g.length<.01||g.length>32))throw Messages.failure("error.model.bone-length");
                groupIds.add(g.id);validateTRS(g.position,g.rotation,g.scale);
            }
        }
        if(groups!=null)for(Group g:groups){Set<String> chain=new HashSet<>();chain.add(g.id);String parent=g.group;while(parent!=null){if(!chain.add(parent))throw Messages.failure("error.model.cycle");Group next=null;for(Group n:groups)if(n.id.equals(parent)){next=n;break;}if(next==null)throw Messages.failure("error.model.parent");parent=next.group;}}
        for(Part p:parts)if(p.group!=null&&!groupIds.contains(p.group))throw Messages.failure("error.model.part-group");
        if(playback!=null){
            if(playback.animations==null || playback.animations.size()>16 || new HashSet<>(playback.animations).size()!=playback.animations.size())throw Messages.failure("error.model.playlist");
            for(String n:playback.animations)if(animations==null||!animations.containsKey(n))throw Messages.failure("error.model.playlist-clip");
        }
        if(animations!=null) {
            Set<String> stateTags=new HashSet<>();
            Set<String> itemRules=new HashSet<>();
            int regularClips=(int)animations.values().stream().filter(clip->clip==null||!clip.isHurt()).count();
            if(animations.isEmpty() || ModelLimits.exceeds(regularClips,ModelLimits.maxAnimations))throw Messages.failure("error.model.clip-limit", ModelLimits.describe("max-animations",ModelLimits.maxAnimations));
            int count=0;
            for(var e:animations.entrySet()) {
                if(!e.getKey().matches("[a-z][a-z0-9_-]{0,47}") || Set.of("constructor","prototype","__proto__").contains(e.getKey()))throw Messages.failure("error.model.clip-id");
                Animation a=e.getValue();
                if(a!=null){ModelInfo.validateTags(a.tags,ModelInfo.STATES,ModelInfo.STATES.size(),version);if(a.tags!=null)for(String tag:a.tags)if(!stateTags.add(tag))throw Messages.failure("error.model.tag-duplicate", tag);}
                if(a==null || !Float.isFinite(a.duration) || a.duration<.1f || a.duration>120 || a.tracks==null)throw Messages.failure("error.model.clip-shape");
                if(a.parallel!=null&&version<9)throw Messages.failure("error.model.parallel-version");
                if(a.isHurt()&&(version<9||a.loop||!a.runsInParallel()||a.tags.size()!=1||a.itemUse!=null))
                    throw Messages.failure("error.model.hurt");
                if(a.itemUse!=null){
                    var rule=a.itemUse;
                    if(version<8||rule.state==null||!Set.of("hold","charge","use").contains(rule.state)||
                        rule.items==null||rule.items.isEmpty()||rule.items.size()>64)throw Messages.failure("error.model.item-rule");
                    if(rule.timing!=null&&(version<9||!"charge".equals(rule.state)||!"kinetic".equals(rule.timing)||a.loop))
                        throw Messages.failure("error.model.kinetic");
                    for(String selector:rule.items)if(!ItemAction.validSelector(selector)||!itemRules.add(rule.state+":"+selector))
                        throw Messages.failure("error.model.selector", selector);
                }
                if(a.sounds!=null){
                    if(ModelLimits.exceeds(a.sounds.size(),ModelLimits.maxSounds))throw Messages.failure("error.model.sound-limit", ModelLimits.describe("max-sounds-per-animation",ModelLimits.maxSounds));
                    for(SoundEvent event:a.sounds)if(event==null || !Float.isFinite(event.time) || event.time<0 || event.time>a.duration || event.sound==null || !event.sound.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !Float.isFinite(event.volume) || event.volume<0 || event.volume>4 || !Float.isFinite(event.pitch) || event.pitch<.5 || event.pitch>2)throw Messages.failure("error.model.sound");
                }
                if(a.interpolation!=null&&(version<6||!List.of("linear","step").contains(a.interpolation)))throw Messages.failure("error.model.interpolation-version");
                for(var track:a.tracks.entrySet()) {
                    if(!ids.contains(track.getKey()))throw Messages.failure("error.model.track-target", track.getKey());
                    List<Keyframe> frames=track.getValue();
                    if(frames==null || frames.isEmpty() || ModelLimits.exceeds(frames.size(),ModelLimits.maxFrames))throw Messages.failure("error.model.track-limit", ModelLimits.describe("max-frames-per-track",ModelLimits.maxFrames));
                    float previous=-1;
                    for(Keyframe f:frames) {
                        if(f==null || !Float.isFinite(f.time) || f.time<0 || f.time>a.duration || f.time<=previous)throw Messages.failure("error.model.frame-time");
                        if((f.visible!=null||f.interpolation!=null)&&version<6)throw Messages.failure("error.model.frame-version");
                        if(f.interpolation!=null&&!List.of("linear","step").contains(f.interpolation))throw Messages.failure("error.model.frame-interpolation");
                        previous=f.time;validateTRS(f.position,f.rotation,f.scale);
                        if(f.text!=null||f.textStyle!=null){
                            Part target=parts.stream().filter(p->p.id.equals(track.getKey())).findFirst().orElse(null);
                            if(version<5||target==null||!target.entityType().equals("text"))throw Messages.failure("error.model.frame-text-version");
                            if(f.text!=null&&(f.text.length()>1024||f.text.split("\\n",-1).length>16))throw Messages.failure("error.model.frame-text");
                        }
                        count++;
                        int total=ModelLimits.maxTotalFrames();
                        if(total!=ModelLimits.UNLIMITED && count>total)throw Messages.failure("error.model.frame-limit", total);
                    }
                }
            }
        }
        return this;
    }
    private static void validateInterpolationJson(JsonElement v){
        if(v!=null&&!v.isJsonNull()&&(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||!List.of("linear","step").contains(v.getAsString())))throw Messages.failure("error.model.interpolation");
    }
    public record MotionState(boolean visible,boolean step,String stepSegments) {
        public boolean snapFrom(MotionState previous){return previous==null?step:visible!=previous.visible||!stepSegments.equals(previous.stepSegments);}
    }
    public static MotionState frameState(String id,Animation clip,double seconds,boolean wrap){
        double time=clip==null?0:clip.loop&&wrap?((seconds%clip.duration)+clip.duration)%clip.duration:Math.max(0,Math.min(clip.duration,seconds));
        boolean visible=true;String interpolation=clip==null?null:clip.interpolation;int index=-1;
        for(var f:clip==null?List.<Keyframe>of():clip.tracks.getOrDefault(id,List.of())){if(f.time>time+1e-7)break;index++;if(f.visible!=null)visible=f.visible;interpolation=f.interpolation==null?clip.interpolation:f.interpolation;}
        boolean step=clip!=null&&clip.tracks.containsKey(id)&&"step".equals(interpolation);return new MotionState(visible,step,step?id+":"+index+";":"");
    }
    public MotionState motion(Part part,Animation clip,double seconds,boolean wrap){
        return motion(part,clip,seconds,wrap,null,0);
    }
    record Layer(Animation clip,double seconds) {
        static Layer of(Animation clip,double seconds){return clip==null?null:new Layer(clip,seconds);}
        boolean appliesTo(String id){return !clip.runsInParallel()||clip.tracks.containsKey(id);}
    }
    private static Layer overlay(String id,Layer action,Layer reaction){
        if(reaction!=null&&reaction.appliesTo(id))return reaction;
        return action!=null&&action.appliesTo(id)?action:null;
    }
    MotionState motion(Part part,Animation clip,double seconds,boolean wrap,Animation action,double actionTime){
        return motion(part,clip,seconds,wrap,Layer.of(action,actionTime),null);
    }
    MotionState motion(Part part,Animation clip,double seconds,boolean wrap,Layer action,Layer reaction){
        var state=layeredState(part.id,clip,seconds,wrap,action,reaction);boolean visible=state.visible,step=state.step;String segments=state.stepSegments,parent=part.group;
        while(parent!=null){var id=parent;var g=groups.stream().filter(n->n.id.equals(id)).findFirst().orElseThrow();state=layeredState(g.id,clip,seconds,wrap,action,reaction);visible&=state.visible;step|=state.step;segments+=state.stepSegments;parent=g.group;}
        return new MotionState(visible,step,segments);
    }
    private static MotionState layeredState(String id,Animation clip,double seconds,boolean wrap,Layer action,Layer reaction){
        var layer=overlay(id,action,reaction);
        return layer!=null?frameState(id,layer.clip,layer.seconds,true):frameState(id,clip,seconds,wrap);
    }
    TextValue layeredText(Part part,Animation clip,double seconds,Layer action,Layer reaction){
        var layer=overlay(part.id,action,reaction);
        return layer!=null?sampleText(part,layer.clip,layer.seconds,true):sampleText(part,clip,seconds,true);
    }
    private static void validateTRS(float[] p,float[] r,float[] s) {
        vector(p,-32,32,"position");vector(r,-360,360,"rotation");vector(s,.01f,16,"scale");
    }
    private static void vector(float[] v,float min,float max,String field) {
        if(v==null || v.length!=3)throw Messages.failure("error.model.vector", field);
        for(float n:v)if(!Float.isFinite(n) || n<min || n>max)throw Messages.failure("error.model.range", field);
    }
    private static float[] lerp(float[] a,float[] b,float t) {
        return new float[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t};
    }
    public static Keyframe sample(Part p,Animation a,double seconds,boolean wrap) {
        var frames=a.tracks.get(p.id);
        if(frames==null || frames.isEmpty())return new Keyframe(0,p.position,p.rotation,p.scale);
        double time=a.loop && wrap ? ((seconds%a.duration)+a.duration)%a.duration : Math.max(0,Math.min(a.duration,seconds));
        if(time<=frames.getFirst().time)return frames.getFirst();
        if(time>=frames.getLast().time)return frames.getLast();
        for(int i=1;i<frames.size();i++) {
            Keyframe right=frames.get(i),left=frames.get(i-1);
            if(right.time>time) {
                if("step".equals(left.interpolation==null?a.interpolation:left.interpolation))return left;
                float t=(float)((time-left.time)/(right.time-left.time));
                return new Keyframe((float)time,lerp(left.position,right.position,t),lerp(left.rotation,right.rotation,t),lerp(left.scale,right.scale,t));
            }
        }
        return frames.getLast();
    }
    public static Matrix4f matrix(Part p,Animation clip,double seconds,boolean wrap) {
        var value=sample(p,clip,seconds,wrap);
        return matrix(p,value.position,value.rotation,value.scale);
    }
    public static Matrix4f matrix(Part p,double seconds,boolean animated) {
        double wave=animated?Math.sin(seconds*Math.PI*2*.8+Math.toRadians(p.phase)):0;
        float[] pos=p.position.clone(),rotation=p.rotation.clone();
        if(animated)pos[1]+=(float)Math.sin(seconds*Math.PI*2*.8)*.03f;
        for(int i=0;i<3;i++)rotation[i]+=(float)wave*p.swing[i];
        return matrix(p,pos,rotation,p.scale);
    }
    public List<String> defaultAnimations(){
        if(playback!=null && !playback.animations.isEmpty())return playback.animations;
        return animations==null||animations.isEmpty()?List.of():List.of(animations.keySet().iterator().next());
    }
    private static Keyframe layered(String id,float[] pos,float[] rotation,float[] scale,Map<String,Animation> clips,List<String> names,double seconds,boolean wrap,Layer action,Layer reaction){
        Keyframe value=new Keyframe(0,pos,rotation,scale);
        var proxy=new Part(id,"IRON_BLOCK",pos,rotation,scale,null,0);
        var layer=overlay(id,action,reaction);
        if(layer!=null)return sample(proxy,layer.clip,layer.seconds,true);
        for(String name:names){Animation clip=clips.get(name);if(clip.tracks.containsKey(id))value=sample(proxy,clip,seconds,wrap);}
        return value;
    }
    public Matrix4f matrix(Part p,List<String> names,double seconds,boolean wrap){
        return matrix(p,names,seconds,wrap,0,0);
    }
    public Matrix4f matrix(Part p,List<String> names,double seconds,boolean wrap,float headYaw,float headPitch){
        return matrix(p,names,seconds,wrap,headYaw,headPitch,null,0);
    }
    Matrix4f matrix(Part p,List<String> names,double seconds,boolean wrap,float headYaw,float headPitch,Animation action,double actionTime){
        return matrix(p,names,seconds,wrap,headYaw,headPitch,Layer.of(action,actionTime),null);
    }
    Matrix4f matrix(Part p,List<String> names,double seconds,boolean wrap,float headYaw,float headPitch,Layer action,Layer reaction){
        var value=layered(p.id,p.position,p.rotation,p.scale,animations==null?Map.of():animations,names,seconds,wrap,action,reaction);
        Matrix4f local=matrix(p,value.position,value.rotation,value.scale);
        if(headRoot(p.tags,p.group))local=new Matrix4f().translation(value.position[0],value.position[1],value.position[2]).rotateY((float)Math.toRadians(-headYaw)).rotateX((float)Math.toRadians(headPitch)).translate(-value.position[0],-value.position[1],-value.position[2]).mul(local);
        if(p.group!=null)for(Group g:groups)if(g.id.equals(p.group))return groupMatrix(g,names,seconds,wrap,new HashSet<>(),headYaw,headPitch,action,reaction).mul(local);
        return local;
    }
    private boolean headRoot(List<String> tags,String parent){
        if(tags==null||!tags.contains("head"))return false;
        while(parent!=null){String id=parent;var g=groups.stream().filter(n->n.id.equals(id)).findFirst().orElseThrow();if(g.tags!=null&&g.tags.contains("head"))return false;parent=g.group;}
        return true;
    }
    boolean affectedBy(Part part,Animation action){
        if(action==null)return false;
        if(!action.runsInParallel())return true;
        if(action.tracks.containsKey(part.id))return true;
        for(String parent=part.group;parent!=null;){
            String id=parent;
            if(action.tracks.containsKey(id))return true;
            parent=groups.stream().filter(group->group.id.equals(id)).findFirst().orElseThrow().group;
        }
        return false;
    }
    private Matrix4f groupMatrix(Group g,List<String> names,double seconds,boolean wrap,Set<String> seen,float headYaw,float headPitch,Layer action,Layer reaction){
        if(!seen.add(g.id))throw Messages.failure("error.model.cycle");
        var value=layered(g.id,g.position,g.rotation,g.scale,animations==null?Map.of():animations,names,seconds,wrap,action,reaction);
        float[] pivot=g.pivot==null?new float[]{0,0,0}:g.pivot;
        Matrix4f local=new Matrix4f().translation(value.position[0],value.position[1],value.position[2]).translate(pivot[0],pivot[1],pivot[2]);
        if(headRoot(g.tags,g.group))local.rotateY((float)Math.toRadians(-headYaw)).rotateX((float)Math.toRadians(headPitch));
        local.mul(transform(new float[]{0,0,0},value.rotation,value.scale)).translate(-pivot[0],-pivot[1],-pivot[2]);
        if(g.group!=null)for(Group parent:groups)if(parent.id.equals(g.group))return groupMatrix(parent,names,seconds,wrap,seen,headYaw,headPitch,action,reaction).mul(local);
        return local;
    }
    public static TextValue sampleText(Part part,Animation clip,double seconds,boolean wrap){
        String text=part.text;TextStyle style=part.appearance(),previousStyle=style;float previousTime=0;
        double time=clip==null?0:clip.loop&&wrap?((seconds%clip.duration)+clip.duration)%clip.duration:Math.max(0,Math.min(clip.duration,seconds));
        var frames=clip==null?List.<Keyframe>of():clip.tracks.getOrDefault(part.id,List.of());
        for(var f:frames)if(f.text!=null&&f.time<=time+1e-7)text=f.text;
        for(var f:frames)if(f.textStyle!=null){
            if(f.time<=time+1e-7){style=f.textStyle;previousStyle=style;previousTime=f.time;continue;}
            double ratio=frameState(part.id,clip,seconds,wrap).step?0:f.time==previousTime?1:Math.max(0,(time-previousTime)/(f.time-previousTime));style=TextStyle.interpolate(previousStyle,f.textStyle,ratio);break;
        }
        return new TextValue(text,style);
    }
    public List<Trigger> soundsBetween(List<String> names,double previous,double current){
        List<Trigger> result=new ArrayList<>();
        if(current<previous || animations==null)return result;
        for(String name:names){Animation clip=animations.get(name);if(clip.sounds==null)continue;
            for(SoundEvent e:clip.sounds){
                if(clip.loop){
                    long first=Math.max(0,(long)Math.floor((previous-e.time+1e-7)/clip.duration)+1),last=(long)Math.floor((current-e.time+1e-7)/clip.duration);
                    for(long cycle=first;cycle<=last;cycle++)result.add(new Trigger(name,e,cycle*clip.duration+e.time));
                }else if(e.time>previous+1e-7 && e.time<=current+1e-7)result.add(new Trigger(name,e,e.time));
            }
        }
        result.sort(Comparator.comparingDouble(Trigger::at));return result;
    }
    private static Matrix4f transform(float[] pos,float[] rotation,float[] scale){
        float rad=(float)(Math.PI/180);
        return new Matrix4f().translation(pos[0],pos[1],pos[2]).rotateXYZ(rotation[0]*rad,rotation[1]*rad,rotation[2]*rad).scale(scale[0],scale[1],scale[2]);
    }
    private static Matrix4f matrix(Part p,float[] pos,float[] rotation,float[] scale) {
        float rad=(float)(Math.PI/180);
        var matrix=new Matrix4f().translation(pos[0],pos[1],pos[2])
            .rotateXYZ(rotation[0]*rad,rotation[1]*rad,rotation[2]*rad).scale(scale[0],scale[1],scale[2]);
        return p.entityType().equals("block")?matrix.translate(-.5f,-.5f,-.5f):matrix;
    }
    /** Item bounds assume the centered unit geometry used by our character resource packs. */
    public float topHeight(List<String> names,double seconds,boolean wrap,float headYaw,float headPitch) {
        Map<String,Matrix4f> rendered=new HashMap<>();
        var clip=names.isEmpty()?null:animations.get(names.getFirst());
        for(Part p:parts)if(motion(p,clip,seconds,wrap).visible())rendered.put(p.id(),matrix(p,names,seconds,wrap,headYaw,headPitch));
        return topHeight(rendered);
    }
    /** Uses the displayed matrices, including blend and visibility, rather than re-sampling the target pose. */
    public float topHeight(Map<String,Matrix4f> rendered) {
        float top=0;
        for(Part p:parts){
            var m=rendered.get(p.id());if(m==null)continue;
            if(p.entityType().equals("text")){top=Math.max(top,m.m31());continue;}
            float offset=p.entityType().equals("block")?0:-.5f;
            for(int n=0;n<8;n++){
                var v=new org.joml.Vector3f((n&1)+offset,(n>>1&1)+offset,(n>>2&1)+offset);
                top=Math.max(top,m.transformPosition(v).y);
            }
        }
        return top;
    }
}
