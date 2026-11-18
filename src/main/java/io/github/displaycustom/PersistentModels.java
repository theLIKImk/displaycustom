package io.github.displaycustom;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Desired state survives unloaded chunks, missing model files and offline players. */
final class PersistentModels {
    record Placed(String id,String model,UUID world,double x,double y,double z,float yaw,float pitch,float headYaw,float headPitch,String animation,double speed,boolean playing) {}
    record Data(int version,Map<String,Placed> placed,Map<UUID,String> players) {}
    final Map<String,Placed> placed=new LinkedHashMap<>();
    final Map<UUID,String> players=new LinkedHashMap<>();
    private final Path path;
    PersistentModels(Path path){this.path=path;}
    void load() throws IOException {
        if(!Files.exists(path))return;
        try(Reader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            Data data=new Gson().fromJson(r,Data.class);
            if(data==null||data.version()!=1||data.placed()==null||data.players()==null)throw new IOException(Messages.failure("error.persist.format"));
            for(var e:data.placed().entrySet()){
                var p=e.getValue();
                if(p==null||!e.getKey().matches("[a-f0-9]{8}")||!e.getKey().equals(p.id())||p.world()==null||p.model()==null||!p.model().matches("[a-z0-9_-]{1,48}")||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())||!Float.isFinite(p.yaw())||!Float.isFinite(p.pitch())||!Float.isFinite(p.headYaw())||!Float.isFinite(p.headPitch())||!Double.isFinite(p.speed())||p.speed()<.05||p.speed()>20)throw new IOException(Messages.failure("error.persist.instance",e.getKey()));
            }
            for(var e:data.players().entrySet())if(e.getKey()==null||e.getValue()==null||!e.getValue().matches("[a-z0-9_-]{1,48}"))throw new IOException(Messages.failure("error.persist.binding"));
            placed.putAll(data.placed());players.putAll(data.players());
        }catch(JsonParseException|IllegalArgumentException e){throw new IOException(Messages.failure("error.persist.read").initCause(e));}
    }
    void save() throws IOException {
        Files.createDirectories(path.getParent());Path tmp=Files.createTempFile(path.getParent(),"models-", ".tmp");
        try {
            try(Writer w=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){new GsonBuilder().setPrettyPrinting().create().toJson(new Data(1,placed,players),w);}
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}
