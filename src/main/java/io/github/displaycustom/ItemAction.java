package io.github.displaycustom;

final class ItemAction {
    private ItemAction() {}
    private static final java.util.Set<String> FAMILIES=java.util.Set.of(
        "sword","spear","axe","trident","mace","shield","pickaxe","fishing_rod","bow","elytra","crossbow","other");

    static boolean validSelector(String selector){
        return selector!=null&&(selector.equals("*")||selector.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
            ||selector.startsWith("#")&&FAMILIES.contains(selector.substring(1)));
    }

    static String select(Model model,String state,String material,String customId){
        Match match=bestMatch(model,state,material,customId);
        return match==null?null:match.name();
    }

    record Match(String name,int rank) {}

    static Match bestMatch(Model model,String state,String material,String customId){
        if(model.animations()==null||"AIR".equals(material))return null;
        String family="#"+family(material);
        String vanilla="minecraft:"+material.toLowerCase(java.util.Locale.ROOT);
        int best=0;
        String chosen=null;
        for(var entry:model.animations().entrySet()){
            var rule=entry.getValue().itemUse();
            if(rule==null||!rule.state().equals(state))continue;
            for(String selector:rule.items()){
                int rank=selector.equals(customId)?4:selector.equals(vanilla)?3:selector.equals(family)?2:selector.equals("*")?1:0;
                if(rank>best){best=rank;chosen=entry.getKey();}
            }
        }
        return chosen==null?null:new Match(chosen,best);
    }

    static String family(String material) {
        if(material.endsWith("_SWORD"))return "sword";
        if(material.endsWith("_SPEAR"))return "spear";
        if(material.endsWith("_PICKAXE"))return "pickaxe";
        if(material.endsWith("_AXE"))return "axe";
        return switch(material){
            case "TRIDENT","MACE","SHIELD","FISHING_ROD","BOW","ELYTRA","CROSSBOW" ->
                material.toLowerCase(java.util.Locale.ROOT);
            default -> "other";
        };
    }

    static Model.Animation mirrored(Model.Animation action) {
        var tracks=new java.util.LinkedHashMap<String,java.util.List<Model.Keyframe>>();
        action.tracks().forEach((id,frames)->{
            String opposite=id.startsWith("left_")?"right_"+id.substring(5):
                id.startsWith("right_")?"left_"+id.substring(6):id;
            tracks.put(opposite,frames.stream().map(frame->{
                float[] position=frame.position().clone(),rotation=frame.rotation().clone();
                position[0]=-position[0];
                rotation[1]=-rotation[1];rotation[2]=-rotation[2];
                return new Model.Keyframe(frame.time(),position,rotation,frame.scale(),frame.text(),frame.textStyle(),frame.visible(),frame.interpolation());
            }).toList());
        });
        return new Model.Animation(action.duration(),action.loop(),tracks,action.sounds(),action.interpolation(),action.tags(),action.itemUse(),action.parallel());
    }
}
