package io.github.displaycustom;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import io.github.displaycustom.api.DisplayCustomService;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.MainHand;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import net.kyori.adventure.text.Component;
import org.joml.Matrix4f;

public final class DisplayCustomPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final String DEFAULT_MODEL = "pelican_bicycle";
    private final Map<String, Model> models = new TreeMap<>();
    private final Map<String, Instance> instances = new LinkedHashMap<>();
    private final Map<UUID,String> worn = new HashMap<>();
    private final Map<UUID,DebugPanel> debugPanels = new HashMap<>();
    private boolean debugEnabled;
    private Path modelDir;
    private Messages messages;
    private PersistentModels storage;
    private SelfBodyVisibility selfBodyVisibility;
    private long tick;

    private record HurtPlayback(String name, Model.Animation clip, long start) {}

    private static final class Instance {
        final Model model;
        final List<Display> entities = new ArrayList<>();
        boolean playing;
        Model.Animation action;
        String actionName;
        EquipmentSlot actionHand;
        long actionStart;
        double actionSpeed=1;
        double actionPreviousSoundTime=-.000001;
        Double chargeSample;
        String actionState;
        long releaseGraceUntil;
        long removeAt=Long.MAX_VALUE;
        long startTick;
        List<String> animations=List.of();
        double previousSoundTime=-.000001;
        Location origin;
        double speed=1;
        UUID owner;
        UUID deathSubject;
        boolean wasInvisible;
        boolean showModelToOwner=false;
        final BoundEquipment equipment=new BoundEquipment();
        String activeItemSide;
        HurtPlayback hurt;
        Location previousLocation;
        float headYaw,headPitch;
        String state;
        long blendStart=-100;
        TextDisplay nametag;
        Boolean nametagSneaking;
        final Map<String,Matrix4f> matrices=new HashMap<>(),blendFrom=new HashMap<>();
        final Map<String,Model.TextValue> textValues=new HashMap<>();
        final Map<String,Model.MotionState> motionValues=new HashMap<>();
        Instance(Model model) { this.model = model; }
        void remove() { entities.forEach(Entity::remove);if(nametag!=null){nametag.remove();nametag=null;} }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        ModelLimits.reloadFrom(getConfig());
        modelDir = getDataFolder().toPath().resolve("models");
        try {
            reloadMessages();
            selfBodyVisibility=new SelfBodyVisibility();
            Files.createDirectories(modelDir);
            try (var manifest = getResource("retired-examples.sha256")) {
                if (manifest != null) {
                    RetiredExamples.archive(modelDir, manifest);
                }
            } catch (IOException e) {
                getLogger().warning(msg(null, "log.archive", e.getMessage()));
            }
            for (String name : List.of(DEFAULT_MODEL, "testdoll", "whale_girl")) {
                if (!Files.exists(modelDir.resolve(name + ".json"))) saveResource("models/" + name + ".json", false);
            }
            String pack = "resourcepacks/display-custom-colors-0.3.0.zip";
            if (!Files.exists(getDataFolder().toPath().resolve(pack))) saveResource(pack, false);
            loadModels();
            storage=new PersistentModels(getDataFolder().toPath().resolve("instances.json"));storage.load();
        } catch (IOException | IllegalStateException e) {
            getLogger().severe(messages==null?e.getMessage():msg(null, "log.data", messages.error(null,e)));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("dc")).setExecutor(this);
        Objects.requireNonNull(getCommand("dc")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::animate, 1, 1);
        getServer().getScheduler().runTaskTimer(this, this::renderDebugPanels, 10, 10);
        getServer().getScheduler().runTask(this,()->{restorePlaced();for(Player p:getServer().getOnlinePlayers())restorePlayer(p);});
        getServer().getServicesManager().register(DisplayCustomService.class,new ModelService(),this,org.bukkit.plugin.ServicePriority.Normal);
        getLogger().info(msg(null, "log.enabled", buildDescription(null), models.size(), DEFAULT_MODEL));
    }

    private String buildDescription(CommandSender sender){
        return msg(sender,"plugin.version",msg(sender,"plugin.name"),getPluginMeta().getVersion(),Model.SUPPORTED_VERSIONS);
    }

    private static Locale locale(CommandSender sender){return sender instanceof Player player?player.locale():null;}
    private String msg(CommandSender sender,String key,Object... values){return messages.text(locale(sender),key,values);}
    private void tell(CommandSender sender,String key,Object... values){sender.sendMessage(msg(sender,key,values));}
    private void reloadMessages() throws IOException {
        for(String locale:List.of("zh_cn","en_us")){
            String resource="lang/"+locale+".properties";
            if(!Files.exists(getDataFolder().toPath().resolve(resource)))saveResource(resource,false);
        }
        messages=new Messages(getDataFolder().toPath().resolve("lang"),
            getConfig().getString("language","zh_cn"),getConfig().getBoolean("use-player-locale",true));
    }

    private record ModelLoadError(String file,Exception cause) {}

    private List<ModelLoadError> loadModels() throws IOException {
        Map<String, Model> next = new TreeMap<>();
        List<ModelLoadError> errors = new ArrayList<>();
        try (var files = Files.list(modelDir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("[a-z0-9_-]{1,48}\\.json")).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    if (Files.size(file) > 2 * 1024 * 1024) throw Messages.failure("error.file.size");
                    Model m = Model.read(reader);
                    if (m == null) throw Messages.failure("error.file.empty");
                    m.validate();
                    String id = file.getFileName().toString().replaceFirst("\\.json$", "");
                    if (!id.equals(m.name())) throw Messages.failure("error.file.name", id);
                    for (var p : m.parts()) {
                        Material material = Material.matchMaterial(p.material());
                        if (material == null || !material.isBlock() || material.isAir()) throw Messages.failure("error.file.block", p.material());
                        if(p.entityType().equals("item")) {
                            Material item = Material.matchMaterial(p.carrier());
                            if(item == null || !item.isItem() || item.isAir())throw Messages.failure("error.file.item", p.carrier());
                        }
                    }
                    next.put(m.name(), m);
                } catch (Exception e) {
                    errors.add(new ModelLoadError(file.getFileName().toString(),e));
                }
            }
        }
        // Invalid edits keep the previous working definition available.
        models.putAll(next);
        models.keySet().removeIf(id -> !next.containsKey(id) && errors.stream().noneMatch(e -> e.file().equals(id+".json")));
        errors.forEach(e -> getLogger().warning(msg(null,"log.load-failed",e.file(),messages.error(null,e.cause()))));
        return errors;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        if(!allowed(sender,action)){tell(sender, "command.permission", action);return true;}
        try {
            if (args.length == 0) { help(sender); return true; }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "spawn" -> {
                    if (!(sender instanceof Player player)) throw Messages.failure("error.spawn.player");
                    Model model = models.get(args.length > 1 ? args[1] : DEFAULT_MODEL);
                    if (model == null) throw Messages.failure("error.model.available", models.keySet());
                    Location origin = SpawnPlacement.at(player.getLocation(),player.getBodyYaw());
                    String id = spawnPlaced(model,origin,PlayerAnimation.relativeYaw(player.getYaw(),origin.getYaw()),player.getPitch(),true);
                    tell(sender, "command.spawn.ok", model.name(), id, model.parts().size());
                    if (model.parts().stream().anyMatch(p -> p.itemModel() != null)) tell(sender, "command.spawn.resources");
                }
                case "itemtest" -> {
                    if (!(sender instanceof Player player)) throw Messages.failure("error.itemtest.player");
                    Model m = models.get(args.length > 1 ? args[1] : DEFAULT_MODEL);
                    if (m == null) throw Messages.failure("error.model.missing");
                    Model.Part p = m.parts().stream().filter(v -> v.entityType().equals("item")).findFirst().orElseThrow(() -> Messages.failure("error.itemtest.part"));
                    ItemStack ordinary = new ItemStack(Material.PAPER);
                    ItemStack custom = customItem(p);
                    custom.editMeta(meta -> meta.displayName(Component.text(msg(sender, "command.itemtest.label", m.name(), p.id()))));
                    player.getInventory().addItem(ordinary, custom).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    tell(sender, "command.itemtest.ok");
                }
                case "pack" -> {
                    tell(sender, "command.pack");
                }
                case "modelinfo" -> { if(args.length!=2)throw Messages.failure("error.modelinfo.syntax");showModelInfo(sender,requireModel(args[1])); }
                case "use" -> {
                    String target=UseRequest.target(args,sender instanceof Player,canUseOthers(sender));
                    Player player=target==null?(Player)sender:getServer().getPlayerExact(target);
                    if(player==null||!player.isOnline())throw Messages.failure("error.player.offline", target);
                    if(player.isDead()||player.getGameMode()==GameMode.SPECTATOR)throw Messages.failure("error.bind.player-state");
                    if(ModelLimits.exceeds(instances.size(),ModelLimits.maxInstances)&&!worn.containsKey(player.getUniqueId()))throw Messages.failure("error.instance.limit", ModelLimits.maxInstances);
                    Model model=requireModel(args[1]);
                    requireEntityBudget(model.parts().size());
                    Location origin=SpawnPlacement.at(player.getLocation(),player.getBodyYaw());
                    String id=spawnModel(model,origin,PlayerAnimation.relativeYaw(player.getYaw(),origin.getYaw()),player.getPitch());
                    String old=worn.get(player.getUniqueId());if(old!=null)removeInstance(old);
                    Instance instance=instances.get(id);instance.owner=player.getUniqueId();instance.wasInvisible=player.isInvisible();instance.previousLocation=player.getLocation();
                    worn.put(player.getUniqueId(),id);
                    try{
                        player.setInvisible(true);
                        instance.equipment.hide(player,getServer().getOnlinePlayers(),true);
                        attachNametag(instance,player);
                        applyOwnerVisibility(instance,player);
                        follow(instance,player);updateNametag(instance);update(instance,0,instance.playing);
                    }catch(RuntimeException error){removeInstance(id);throw error;}
                    storage.players.put(player.getUniqueId(),model.name());saveState();
                    tell(sender, "command.bind.ok", player.getName(), model.name());
                    if(!hasHead(model))tell(sender, "command.bind.no-head");
                }
                case "takeoff" -> {
                    if(!(sender instanceof Player player))throw Messages.failure("error.takeoff.player");
                    if(args.length!=1)throw Messages.failure("error.takeoff.syntax");
                    String id=worn.get(player.getUniqueId());if(id==null&&!storage.players.containsKey(player.getUniqueId()))throw Messages.failure("error.bind.absent");if(id!=null)removeInstance(id);storage.players.remove(player.getUniqueId());saveState();tell(sender, "command.takeoff.ok");
                }
                case "showmemodel" -> {
                    if(!(sender instanceof Player player))throw Messages.failure("error.self.player");
                    if(args.length!=1)throw Messages.failure("error.self.syntax");
                    String id=worn.get(player.getUniqueId());
                    if(id==null)throw Messages.failure("error.self.missing");
                    Instance instance=instances.get(id);
                    if(instance==null)throw Messages.failure("error.self.loading");
                    instance.showModelToOwner=!instance.showModelToOwner;
                    applyOwnerVisibility(instance,player);
                    tell(sender,instance.showModelToOwner?"command.self.show":"command.self.hide");
                }
                case "debug" -> {
                    if(args.length!=2||!args[1].equalsIgnoreCase("playerstatus"))throw Messages.failure("error.debug.syntax");
                    if(debugEnabled){
                        debugEnabled=false;
                        tell(sender, "command.debug.off");
                    }else{
                        debugEnabled=true;
                        tell(sender, "command.debug.on");
                    }
                }
                case "list" -> {
                    tell(sender, "command.list.models", String.join(", ", models.keySet()));
                    if (instances.isEmpty()) tell(sender, "command.list.empty");
                    instances.forEach((id, i) -> tell(sender, "command.list.entry", id, i.model.name(), i.owner!=null?msg(sender,"status.bound",i.state==null?msg(sender,"debug.init"):msg(sender,"state."+i.state)):msg(sender,i.playing?"status.playing":"status.still")));
                    storage.placed.forEach((id,p)->{if(!instances.containsKey(id))tell(sender, "command.list.pending", id, p.model());});
                }
                case "reload" -> {
                    reloadConfig();
                    reloadMessages();
                    ModelLimits.reloadFrom(getConfig());
                    var errors = loadModels();
                    restorePlaced();for(Player player:getServer().getOnlinePlayers())restorePlayer(player);
                    tell(sender, "command.reload.ok", buildDescription(sender), models.size());
                    errors.forEach(e -> tell(sender,"log.load-failed",e.file(),messages.error(locale(sender),e.cause())));
                }
                case "clear" -> {
                    int count = instances.size();
                    new ArrayList<>(instances.keySet()).forEach(this::removeInstance);
                    storage.placed.clear();storage.players.clear();saveState();
                    tell(sender, "command.clear.ok", count);
                }
                case "remove", "play", "stop" -> {
                    if (args.length < 2) throw Messages.failure("error.instance.id");
                    Instance instance = instances.get(args[1]);
                    if(instance==null&&args[0].equalsIgnoreCase("remove")&&storage.placed.remove(args[1])!=null){saveState();tell(sender, "command.remove.saved", args[1]);break;}
                    if (instance == null) throw Messages.failure("error.instance.missing");
                    if(instance.removeAt!=Long.MAX_VALUE&&!args[0].equalsIgnoreCase("remove"))throw Messages.failure("error.instance.death");
                    switch (args[0].toLowerCase(Locale.ROOT)) {
                        case "remove" -> {if(instance.owner!=null)storage.players.remove(instance.owner);storage.placed.remove(args[1]);removeInstance(args[1]);saveState();}
                        case "play" -> {
                            if(instance.owner!=null)throw Messages.failure("error.play.bound");
                            var request=PlaybackRequest.parse(args);
                            playPlaced(args[1],instance,request.animation(),request.speed());
                        }
                        case "stop" -> {
                            if(instance.owner!=null)throw Messages.failure("error.stop.bound");
                            stopPlaced(args[1],instance);
                        }
                    }
                    tell(sender, "command.complete", args[0], args[1]);
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException | IOException e) { tell(sender, "command.failed", messages.error(locale(sender),e)); }
        return true;
    }

    private void help(CommandSender s) {
        tell(s,"command.help",msg(s,"plugin.name"));
    }
    private static boolean allowed(CommandSender sender,String action){
        if(sender.hasPermission("displaycustom.admin"))return true;
        return switch(action){case "help","pack","modelinfo","list"->sender.hasPermission("displaycustom.info");case "use","takeoff","showmemodel"->sender.hasPermission("displaycustom.use");case "debug"->sender.hasPermission("displaycustom.admin");case "spawn","remove","play","stop","clear","reload","itemtest"->sender.hasPermission("displaycustom."+action);default->false;};
    }
    private static boolean canUseOthers(CommandSender sender){return sender.hasPermission("displaycustom.admin")||sender.hasPermission("displaycustom.use.others");}
    private PersistentModels.Placed record(String id,Instance i){Location l=i.origin;return new PersistentModels.Placed(id,i.model.name(),l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch(),i.headYaw,i.headPitch,i.animations.isEmpty()?null:i.animations.getFirst(),i.speed,i.playing);}
    private void beginBlend(Instance i){i.blendFrom.clear();i.matrices.forEach((id,matrix)->i.blendFrom.put(id,new Matrix4f(matrix)));i.blendStart=tick;}
    private void saveState(){try{storage.save();}catch(IOException e){getLogger().severe(msg(null, "log.save", e.getMessage()));throw Messages.failure("error.persist.save");}}
    private void restorePlaced(){
        for(var saved:storage.placed.values()){
            if(instances.containsKey(saved.id()))continue;
            World world=getServer().getWorld(saved.world());Model model=models.get(saved.model());
            if(world==null||model==null)continue;
            Location location=new Location(world,saved.x(),saved.y(),saved.z(),saved.yaw(),saved.pitch());
            if(!world.isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4))continue;
            try{
                String temporary=spawnModel(model,location,saved.headYaw(),saved.headPitch());Instance i=instances.remove(temporary);instances.put(saved.id(),i);
                if(saved.playing()&&saved.animation()!=null&&model.animations()!=null&&model.animations().containsKey(saved.animation())){i.animations=List.of(saved.animation());i.speed=saved.speed();i.playing=true;i.startTick=tick;}
            }catch(RuntimeException e){getLogger().warning(msg(null, "log.restore", saved.id(), messages.error(null,e)));}
        }
    }
    private void restorePlayer(Player player){
        String name=storage.players.get(player.getUniqueId());if(name==null||worn.containsKey(player.getUniqueId())||player.isDead()||player.getGameMode()==GameMode.SPECTATOR)return;
        Model model=models.get(name);if(model==null){getLogger().warning(msg(null, "log.player-model", name));return;}
        try{Location origin=SpawnPlacement.at(player.getLocation(),player.getBodyYaw());String id=spawnModel(model,origin,PlayerAnimation.relativeYaw(player.getYaw(),origin.getYaw()),player.getPitch());
            Instance i=instances.get(id);i.owner=player.getUniqueId();i.wasInvisible=player.isInvisible();i.previousLocation=player.getLocation();worn.put(i.owner,id);
            try{player.setInvisible(true);i.equipment.hide(player,getServer().getOnlinePlayers(),true);attachNametag(i,player);applyOwnerVisibility(i,player);follow(i,player);updateNametag(i);update(i,0,i.playing);}catch(RuntimeException e){removeInstance(id);throw e;}
        }catch(RuntimeException e){getLogger().warning(msg(null, "log.player-restore", messages.error(null,e)));}
    }
    private Model requireModel(String name){Model m=models.get(name);if(m==null)throw Messages.failure("error.model.named", name);return m;}
    private static boolean hasHead(Model m){return m.parts().stream().anyMatch(p->p.tags()!=null&&p.tags().contains("head"))||m.groups()!=null&&m.groups().stream().anyMatch(g->g.tags()!=null&&g.tags().contains("head"));}
    private String text(CommandSender sender,String value){return value==null||value.isBlank()?msg(sender,"info.blank"):value;}
    private void showModelInfo(CommandSender sender,Model m){
        tell(sender,"info.id",m.name(),m.version());
        var info=m.info()==null?new ModelInfo(null,null,null,null,null):m.info();
        tell(sender,"info.meta",text(sender,info.name()),text(sender,info.author()),text(sender,info.description()),text(sender,info.contact()),text(sender,info.copyright()));
        var groups=m.groups()==null?List.<Model.Group>of():m.groups();
        long bones=groups.stream().filter(g->"bone".equals(g.kind())).count();
        tell(sender,"info.parts",m.parts().size(),m.parts().stream().filter(p->p.entityType().equals("block")).count(),m.parts().stream().filter(p->p.entityType().equals("item")).count(),m.parts().stream().filter(p->p.entityType().equals("text")).count(),bones,groups.size()-bones);
        for(String role:List.of("body","head","arm","leg","foot")){
            var nodes=new ArrayList<String>();for(var g:groups)if(g.tags()!=null&&g.tags().contains(role))nodes.add(g.id());for(var p:m.parts())if(p.tags()!=null&&p.tags().contains(role))nodes.add(p.id());
            tell(sender,"info.role",role,nodes.isEmpty()?msg(sender,"info.unset"):String.join(", ",nodes));
        }
        if(m.animations()==null||m.animations().isEmpty())tell(sender,"info.none");
        else m.animations().forEach((name,a)->tell(sender,"info.clip",name,a.duration(),msg(sender,a.loop()?"info.loop":"info.once"),a.tags()==null?List.of():a.tags(),a.tracks().size(),a.tracks().values().stream().mapToInt(List::size).sum(),a.itemUse()==null?"":msg(sender,"info.item-rule",a.itemUse().state(),a.itemUse().items())));
        for(var part:m.parts())if(part.heldItem()!=null)tell(sender,"info.held",part.heldItem(),part.id());
        for(String state:List.of("stand","sit","walk","run","sneak","sneak_walk","climb","jump","fall","swim","fly","sleep"))tell(sender,"info.state",state,Objects.toString(PlayerAnimation.clip(m,state),msg(sender,"info.base")));
        for(var bone:groups)if("bone".equals(bone.kind())){
            long descendants=m.parts().stream().filter(p->under(groups,p.group(),bone.id())).count();
            long tracks=m.animations()==null?0:m.animations().values().stream().filter(a->a.tracks().containsKey(bone.id())).count();
            tell(sender,"info.bone",bone.id(),Objects.toString(bone.group(),msg(sender,"info.root")),bone.length()==null?1:bone.length(),descendants,tracks);
        }
    }
    private static boolean under(List<Model.Group> groups,String parent,String target){
        while(parent!=null){if(parent.equals(target))return true;String id=parent;parent=groups.stream().filter(g->g.id().equals(id)).findFirst().orElseThrow().group();}return false;
    }
    /** Creates the floating player-name tag above the model; height follows the animated geometry. */
    private void attachNametag(Instance i,Player player){
        if(i.nametag!=null&&i.nametag.isValid())return;
        i.nametag=i.origin.getWorld().spawn(i.origin,TextDisplay.class,d->{
            d.text(Component.text(player.getName()));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0x66000000));
            d.setPersistent(false);d.setInvulnerable(true);
            d.setViewRange(1);d.setTeleportDuration(1);
        });
    }
    /** Moves the nametag to the current model top after follow() updated the origin. */
    private void updateNametag(Instance i){
        if(i.nametag==null)return;
        if(!i.nametag.isValid()){i.nametag=null;return;}
        Player owner=getServer().getPlayer(i.owner);
        if(owner==null)return;
        boolean sneaking=owner.isSneaking();
        if(i.nametagSneaking==null||i.nametagSneaking!=sneaking||tick%10==0){
            for(Player viewer:getServer().getOnlinePlayers()){
                boolean show=!sneaking&&(!viewer.getUniqueId().equals(i.owner)||i.showModelToOwner);
                if(show)viewer.showEntity(this,i.nametag);else viewer.hideEntity(this,i.nametag);
            }
            i.nametagSneaking=sneaking;
        }
        float top=i.model.topHeight(i.matrices);
        var at=i.origin.clone().add(0,top+0.3,0);
        if(!i.nametag.teleport(at))i.nametag.teleport(i.origin);
    }
    private void removeInstance(String id){
        Instance i=instances.remove(id);if(i==null)return;i.remove();
        if(i.owner==null)return;worn.remove(i.owner,id);
        restoreWearer(i);
    }
    private void restoreWearer(Instance i){
        Player player=getServer().getPlayer(i.owner);if(player==null)return;
        player.setInvisible(i.wasInvisible||player.hasPotionEffect(PotionEffectType.INVISIBILITY));
        selfBodyVisibility.restore(player);
        i.equipment.restore(player,getServer().getOnlinePlayers());
    }
    /** Per-player self status panel: anchored to the player's body, visible to everyone except the owner. */
    private void renderDebugPanels(){
        if(!debugEnabled){
            if(debugPanels.isEmpty())return;
            new ArrayList<>(debugPanels.keySet()).forEach(this::removeDebugPanel);
            return;
        }
        for(Player self:getServer().getOnlinePlayers()){
            UUID uuid=self.getUniqueId();
            DebugPanel panel=debugPanels.get(uuid);
            if(panel==null||!panel.textDisplay.isValid()){
                if(panel!=null)panel.remove();
                panel=createDebugPanel(self);debugPanels.put(uuid,panel);
            }
            // Anchor at the player's body (feet + fixed height), left of body facing; not head height.
            var body=self.getLocation();
            var dir=flatDirection(body);
            var left=dir.clone().crossProduct(new org.bukkit.util.Vector(0,1,0)).normalize();
            var anchor=body.clone().add(left.multiply(2.2)).add(0,0.6,0);
            var face=anchor.clone().setDirection(dir);
            panel.textDisplay.teleport(face);
            updateDebugText(self, panel);
            // Hand items sit on their own column left of the panel: main hand above, off hand below, labeled.
            var items=anchor.clone().add(left.multiply(1.0)).add(0,0.8,0);
            updateHandItem(panel.mainHand,self.getInventory().getItemInMainHand(),items.clone().add(0,0.35,0));
            updateHandItem(panel.offHand,self.getInventory().getItemInOffHand(),items.clone().add(0,-0.75,0));
            panel.mainLabel.teleport(items.clone().add(0,1.05,0));
            panel.offLabel.teleport(items.clone().add(0,-0.05,0));
        }
        // Remove panels for players who left the server.
        for(UUID uuid:new ArrayList<>(debugPanels.keySet())){
            Player p=getServer().getPlayer(uuid);
            if(p==null||!p.isOnline())removeDebugPanel(uuid);
        }
    }
    /** Creates the panel entities and hides them from the owner only; everyone else can see. */
    private DebugPanel createDebugPanel(Player self){
        var body=self.getLocation();
        var dir=flatDirection(body);
        var left=dir.clone().crossProduct(new org.bukkit.util.Vector(0,1,0)).normalize();
        var anchor=body.clone().add(left.multiply(2.2)).add(0,0.6,0);
        TextDisplay text=self.getWorld().spawn(anchor,TextDisplay.class,d->{
            d.setBillboard(Display.Billboard.FIXED);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0x88101020));
            d.setShadowed(false);
            d.setAlignment(TextDisplay.TextAlignment.LEFT);
            d.setPersistent(false);d.setInvulnerable(true);
            d.setViewRange(1);d.setTeleportDuration(1);
        });
        ItemDisplay main=self.getWorld().spawn(anchor.clone().add(left.multiply(1.0)).add(0,1.15,0),ItemDisplay.class,d->{
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);d.setInvulnerable(true);
            d.setViewRange(1);d.setTeleportDuration(1);
        });
        ItemDisplay off=self.getWorld().spawn(anchor.clone().add(left.multiply(1.0)).add(0,0.05,0),ItemDisplay.class,d->{
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);d.setInvulnerable(true);
            d.setViewRange(1);d.setTeleportDuration(1);
        });
        TextDisplay mainLabel=self.getWorld().spawn(anchor.clone().add(left.multiply(1.0)).add(0,1.45,0),TextDisplay.class,d->{
            d.text(Component.text(msg(self,"debug.main"),net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0x66101020));
            d.setShadowed(true);
            d.setPersistent(false);d.setInvulnerable(true);
            d.setViewRange(1);d.setTeleportDuration(1);
        });
        TextDisplay offLabel=self.getWorld().spawn(anchor.clone().add(left.multiply(1.0)).add(0,0.35,0),TextDisplay.class,d->{
            d.text(Component.text(msg(self,"debug.off"),net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0x66101020));
            d.setShadowed(true);
            d.setPersistent(false);d.setInvulnerable(true);
            d.setViewRange(1);d.setTeleportDuration(1);
        });
        self.hideEntity(this,text);self.hideEntity(this,main);self.hideEntity(this,off);self.hideEntity(this,mainLabel);self.hideEntity(this,offLabel);
        return new DebugPanel(text,main,off,mainLabel,offLabel);
    }
    private void updateDebugText(Player player, DebugPanel panel){
        if(!panel.textDisplay.isValid())return;
        Component status=selfStatusText(player);
        if(!status.equals(panel.lastStatus)){
            panel.textDisplay.text(status);
            panel.lastStatus=status;
        }
    }

    private Component selfStatusText(Player self){
        var text=Component.text();
        text.append(Component.text(msg(self,"debug.title"),net.kyori.adventure.text.format.NamedTextColor.AQUA));
        String id=worn.get(self.getUniqueId());
        Instance i=id==null?null:instances.get(id);
        if(i==null&&self.isDead()){
            UUID subject=self.getUniqueId();
            i=instances.values().stream().filter(instance->subject.equals(instance.deathSubject)).findFirst().orElse(null);
        }
        String state=i==null?msg(self,"debug.none"):i.deathSubject!=null?msg(self,"debug.dead"):i.state==null?msg(self,"debug.init"):msg(self,"state."+i.state);
        text.append(Component.text(msg(self,"debug.state",state)));
        if(i==null){
            text.append(Component.text(msg(self,"debug.unbound")));
        }else{
            text.append(Component.text(msg(self,"debug.model",i.model.name())));
            text.append(Component.text(msg(self,"debug.entities",i.entities.size())));
            String base=(i.playing||i.deathSubject!=null)&&!i.animations.isEmpty()
                ?String.join(", ",i.animations):msg(self,"info.base");
            if(i.action!=null&&!i.action.runsInParallel()){
                text.append(Component.text(msg(self,"debug.exclusive",i.actionName)));
            }else{
                text.append(Component.text(msg(self,"debug.base",base)));
                if(i.action!=null)text.append(Component.text(msg(self,"debug.parallel",i.actionName)));
            }
            if(i.hurt!=null)text.append(Component.text(msg(self,"debug.hurt",i.hurt.name())));
        }
        return text.build();
    }
    private static org.bukkit.util.Vector flatDirection(org.bukkit.Location eye){
        var dir=eye.getDirection();dir.setY(0);
        if(dir.lengthSquared()<1e-6)dir=new org.bukkit.util.Vector(0,0,1);
        return dir.normalize();
    }
    /** Syncs one hand display: swaps the stack when it changed and follows the anchor. */
    private void updateHandItem(ItemDisplay display,ItemStack stack,org.bukkit.Location at){
        var current=display.getItemStack();
        boolean empty=stack==null||stack.getType().isAir();
        boolean same=empty?current==null:stack.isSimilar(current);
        if(!same)display.setItemStack(empty?null:stack);
        display.teleport(at);
    }
    private void removeDebugPanel(UUID uuid){
        DebugPanel panel=debugPanels.remove(uuid);
        if(panel!=null)panel.remove();
    }
    private static final class DebugPanel {
        final TextDisplay textDisplay;final ItemDisplay mainHand;final ItemDisplay offHand;
        final TextDisplay mainLabel;final TextDisplay offLabel;
        Component lastStatus;
        DebugPanel(TextDisplay text,ItemDisplay main,ItemDisplay off,TextDisplay mainLabel,TextDisplay offLabel){this.textDisplay=text;this.mainHand=main;this.offHand=off;this.mainLabel=mainLabel;this.offLabel=offLabel;}
        void remove(){textDisplay.remove();mainHand.remove();offHand.remove();mainLabel.remove();offLabel.remove();}
    }
    /** Refuses model creation when the server-wide display-entity budget would be exceeded. */
    private void requireEntityBudget(int additionalParts){
        int used=instances.values().stream().mapToInt(i->i.entities.size()).sum();
        if(ModelLimits.exceeds(used+additionalParts,ModelLimits.maxTotalEntities))throw Messages.failure("error.entity.limit", ModelLimits.maxTotalEntities, used, additionalParts);
    }
    /** The owner sees the vanilla body by default and can toggle the custom model. */
    private void applyOwnerVisibility(Instance i,Player player){
        selfBodyVisibility.update(player,i.showModelToOwner||i.wasInvisible||player.hasPotionEffect(PotionEffectType.INVISIBILITY));
        if(i.showModelToOwner)for(Display entity:i.entities)player.showEntity(this,entity);
        else for(Display entity:i.entities)player.hideEntity(this,entity);
        if(i.nametag!=null){if(i.showModelToOwner&&!player.isSneaking())player.showEntity(this,i.nametag);else player.hideEntity(this,i.nametag);}
    }
    /** Restores body visibility after unbinding; keeps an existing invisibility potion effect hidden. */
    private void follow(Instance i,Player player){
        Location now=player.getLocation();
        boolean same=i.previousLocation!=null&&now.getWorld().equals(i.previousLocation.getWorld());
        double dx=same?now.getX()-i.previousLocation.getX():0,dz=same?now.getZ()-i.previousLocation.getZ():0;
        double distance=dx*dx+dz*dz;
        boolean moving=distance>.000025&&distance<16; // excludes teleport-sized displacement
        var snapshot=new PlayerAnimation.State(player.isSleeping(),player.isInsideVehicle(),player.isFlying()||player.isGliding(),player.isSwimming(),player.isOnGround(),player.getVelocity().getY()>.02,player.isSneaking(),moving,player.isSprinting(),player.isClimbing());
        String state=PlayerAnimation.state(snapshot),clip=PlayerAnimation.clip(i.model,state);
        List<String> selected=clip==null?List.of():List.of(clip);
        if(!selected.equals(i.animations)){
            boolean bedTransition="sleep".equals(i.state)||"sleep".equals(state);
            if(bedTransition)i.blendFrom.clear();else beginBlend(i);
            i.blendStart=tick;i.animations=selected;i.startTick=tick;i.previousSoundTime=-.000001;
        }
        i.state=state;i.playing=clip!=null;i.speed=1;
        i.origin=SpawnPlacement.at(now,player.getBodyYaw());i.headYaw=PlayerAnimation.relativeYaw(now.getYaw(),i.origin.getYaw());i.headPitch=now.getPitch();
        if(snapshot.sleeping()){
            Location bedLocation=player.getBedLocation();
            if(bedLocation.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Bed bed){
                i.origin=SpawnPlacement.sleeping(bedLocation,bed.getFacing(),bed.getPart()==org.bukkit.block.data.type.Bed.Part.HEAD);
                i.headYaw=0;i.headPitch=0;
            }
        }
        for(Display entity:i.entities)if(!entity.teleport(i.origin))throw Messages.failure("error.follow");
        i.previousLocation=now;
    }
    @EventHandler public void onQuit(PlayerQuitEvent event){String id=worn.get(event.getPlayer().getUniqueId());if(id!=null)removeInstance(id);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPlayerDamage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof Player player)||event.getFinalDamage()<=0)return;
        Instance instance=wornBy(player);
        if(instance==null||instance.model.animations()==null)return;
        for(var entry:instance.model.animations().entrySet()){
            if(entry.getValue().isHurt()){
                instance.hurt=new HurtPlayback(entry.getKey(),entry.getValue(),tick);
                return;
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onDeath(PlayerDeathEvent event){
        Player player=event.getEntity();
        String id=worn.get(player.getUniqueId());
        if(id==null)return;
        Instance instance=instances.get(id);
        var death=clip(instance,"death");
        if(death==null||death.isHurt()){removeInstance(id);return;}
        worn.remove(instance.owner,id);
        restoreWearer(instance);
        instance.deathSubject=player.getUniqueId();
        instance.owner=null;
        instance.action=null;
        instance.hurt=null;
        instance.blendFrom.clear();
        if(instance.nametag!=null){instance.nametag.remove();instance.nametag=null;}
        for(Display display:instance.entities)player.showEntity(this,display);
        for(int n=0;n<instance.model.parts().size();n++)if(instance.model.parts().get(n).heldItem()!=null)
            ((ItemDisplay)instance.entities.get(n)).setItemStack(new ItemStack(Material.AIR));
        instance.animations=List.of("death");
        instance.startTick=tick;
        instance.speed=1;
        instance.playing=true;
        instance.previousSoundTime=-.000001;
        instance.headYaw=0;instance.headPitch=0;
        instance.removeAt=tick+Math.max(1,(long)Math.ceil(death.duration()*20.0))+2;
        update(instance,0,true);
    }

    private static Model.Animation clip(Instance instance,String name){
        return instance==null||name==null||instance.model.animations()==null?null:instance.model.animations().get(name);
    }
    private Instance wornBy(Player player){return instances.get(worn.get(player.getUniqueId()));}
    private void startAction(Instance instance,Player player,String name,EquipmentSlot hand,String state){
        var action=clip(instance,name);
        if(action==null||hand==null||"sleep".equals(instance.state))return;
        if(!"use".equals(state)&&state.equals(instance.actionState)&&name.equals(instance.actionName)&&hand==instance.actionHand)return;
        boolean left=(player.getMainHand()==MainHand.LEFT)==(hand==EquipmentSlot.HAND);
        instance.action=left?ItemAction.mirrored(action):action;
        instance.actionName=name;
        instance.actionHand=hand;
        instance.actionStart=tick;
        instance.actionState=state;
        instance.actionSpeed=1;
    }
    private static String actionFor(Instance instance,String state,ItemStack stack){
        var match=matchItemAction(instance,state,stack);
        return match==null?null:match.name();
    }
    private static ItemAction.Match matchItemAction(Instance instance,String state,ItemStack stack){
        if(instance==null||stack==null||stack.getType().isAir())return null;
        var meta=stack.hasItemMeta()?stack.getItemMeta():null;
        var key=meta==null?null:meta.getItemModel();
        return ItemAction.bestMatch(instance.model,state,stack.getType().name(),key==null?null:key.toString());
    }
    private void useItem(Player player,EquipmentSlot hand,ItemStack stack,boolean released){
        Instance instance=wornBy(player);
        String name=actionFor(instance,"use",stack);
        if(name==null||hand==null)return;
        startAction(instance,player,name,hand,"use");
        if(released)instance.releaseGraceUntil=tick+2;
    }
    private void updateAction(Instance instance,Player player){
        boolean sleeping="sleep".equals(instance.state);
        instance.chargeSample=null;
        ItemStack activeItem=player.hasActiveItem()?player.getActiveItem():null;
        String charge=actionFor(instance,"charge",activeItem);
        if(charge!=null){
            var weapon=activeItem.getData(DataComponentTypes.KINETIC_WEAPON);
            if(weapon!=null){
                var timing=KineticCharge.from(weapon);
                int elapsed=player.getActiveItemUsedTime();
                if(timing.expired(elapsed))charge=null;
                else{
                    var animation=clip(instance,charge);
                    if("kinetic".equals(animation.itemUse().timing()))instance.chargeSample=timing.sample(elapsed,animation.duration());
                }
            }
        }
        if(!sleeping){
            if(charge!=null&&tick>instance.releaseGraceUntil){
                startAction(instance,player,charge,player.getActiveItemHand(),"charge");
                return;
            }
            if(instance.action!=null&&"use".equals(instance.actionState)&&(tick-instance.actionStart)/20.0<instance.action.duration())return;
            var main=matchItemAction(instance,"hold",player.getInventory().getItemInMainHand());
            var off=matchItemAction(instance,"hold",player.getInventory().getItemInOffHand());
            if(off!=null&&(main==null||off.rank()>main.rank())){
                startAction(instance,player,off.name(),EquipmentSlot.OFF_HAND,"hold");return;
            }
            if(main!=null){
                startAction(instance,player,main.name(),EquipmentSlot.HAND,"hold");return;
            }
        }
        if(instance.action!=null){
            beginBlend(instance);
            instance.action=null;
            instance.actionState=null;
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onItemSwing(PlayerArmSwingEvent event){
        Player player=event.getPlayer();
        if(player.hasActiveItem())return;
        var stack=player.getInventory().getItem(event.getHand());
        var material=stack.getType();
        if(material==Material.FISHING_ROD||material==Material.BOW||material==Material.CROSSBOW||material==Material.SHIELD)return;
        useItem(player,event.getHand(),stack,false);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onToolInteract(PlayerInteractEvent event){
        if(!event.getAction().isRightClick()||event.useItemInHand()==Event.Result.DENY)return;
        Material material=event.getMaterial();
        if(material==Material.BOW||material==Material.CROSSBOW||material==Material.TRIDENT||
            material==Material.FISHING_ROD||material==Material.SHIELD||material==Material.FIREWORK_ROCKET&&event.getPlayer().isGliding())return;
        useItem(event.getPlayer(),event.getHand(),event.getItem(),false);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onFishing(PlayerFishEvent event){
        if(event.getState()==PlayerFishEvent.State.FISHING){
            Player player=event.getPlayer();
            if(event.getHand()!=null)useItem(player,event.getHand(),player.getInventory().getItem(event.getHand()),false);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onShot(EntityShootBowEvent event){
        if(event.getEntity() instanceof Player player&&event.getBow()!=null){
            useItem(player,event.getHand(),event.getBow(),true);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onFlightBoost(PlayerElytraBoostEvent event){
        var wings=event.getPlayer().getInventory().getChestplate();
        useItem(event.getPlayer(),event.getHand(),wings,false);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onTridentThrow(ProjectileLaunchEvent event){
        if(event.getEntity() instanceof Trident trident&&trident.getShooter() instanceof Player player)releaseTrident(player,trident.getItem());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onRiptide(PlayerRiptideEvent event){
        releaseTrident(event.getPlayer(),event.getItem());
    }
    private void releaseTrident(Player player,ItemStack stack){
        Instance instance=wornBy(player);
        if(instance==null)return;
        EquipmentSlot hand=player.hasActiveItem()?player.getActiveItemHand():instance.actionHand;
        if(hand==null)hand=player.getInventory().getItemInMainHand().getType()==Material.TRIDENT?EquipmentSlot.HAND:EquipmentSlot.OFF_HAND;
        useItem(player,hand,stack,true);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event){
        getServer().getScheduler().runTask(this,()->restorePlayer(event.getPlayer()));
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event){getServer().getScheduler().runTask(this,()->restorePlayer(event.getPlayer()));}
    @EventHandler public void onChunkLoad(ChunkLoadEvent event){getServer().getScheduler().runTask(this,this::restorePlaced);}
    @EventHandler public void onWorldLoad(WorldLoadEvent event){getServer().getScheduler().runTask(this,this::restorePlaced);}
    private ItemStack customItem(Model.Part part) {
        ItemStack item = new ItemStack(Objects.requireNonNull(Material.matchMaterial(part.carrier())));
        if(part.itemModel()!=null)item.editMeta(meta -> meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString(part.itemModel()))));
        return item;
    }
    private String spawnPlaced(Model model, Location origin, float headYaw, float headPitch, boolean persistent) {
        if (ModelLimits.exceeds(instances.size(),ModelLimits.maxInstances))
            throw Messages.failure("error.instance.limit",ModelLimits.maxInstances);
        requireEntityBudget(model.parts().size());
        String id=spawnModel(model,origin,headYaw,headPitch);
        if(persistent){storage.placed.put(id,record(id,instances.get(id)));saveState();}
        return id;
    }

    private void playPlaced(String id,Instance instance,String animation,double speed) {
        if(!Double.isFinite(speed)||speed<.05||speed>20)throw Messages.failure("error.play.speed");
        var clips=instance.model.animations();
        var selected=clips==null||animation==null?null:clips.get(animation);
        if(selected==null)throw Messages.failure("error.animation.missing",instance.model.name());
        if(selected.runsInParallel()&&instance.playing){
            instance.action=selected;instance.actionName=animation;instance.actionState="manual";
            instance.actionStart=tick;instance.actionSpeed=speed;instance.actionPreviousSoundTime=-.000001;
            return;
        }
        beginBlend(instance);
        instance.action=null;instance.actionState=null;instance.animations=List.of(animation);
        instance.speed=speed;instance.playing=true;instance.startTick=tick;instance.previousSoundTime=-.000001;
        persistPlacedIfSaved(id,instance);
    }

    private void stopPlaced(String id,Instance instance) {
        instance.playing=false;instance.action=null;instance.actionState=null;instance.blendFrom.clear();
        update(instance,0,false);
        persistPlacedIfSaved(id,instance);
    }

    private void persistPlacedIfSaved(String id,Instance instance){
        if(storage.placed.containsKey(id)){storage.placed.put(id,record(id,instance));saveState();}
    }

    private final class ModelService implements DisplayCustomService {
        private void requirePrimaryThread(){
            if(!isEnabled()||!Bukkit.isPrimaryThread())throw new IllegalStateException("DisplayCustom API requires the enabled plugin on the primary server thread.");
        }

        @Override public Set<String> modelIds(){
            requirePrimaryThread();
            return Set.copyOf(models.keySet());
        }

        @Override public Set<String> animationNames(String modelId){
            requirePrimaryThread();
            var clips=requireModel(Objects.requireNonNull(modelId,"modelId")).animations();
            return clips==null?Set.of():Set.copyOf(clips.keySet());
        }

        @Override public String spawn(String modelId, Location location, boolean persistent){
            requirePrimaryThread();
            if(location==null||!location.isWorldLoaded()||!Double.isFinite(location.getX())
                ||!Double.isFinite(location.getY())||!Double.isFinite(location.getZ())||!Float.isFinite(location.getYaw()))
                throw Messages.failure("error.api.location");
            var origin=SpawnPlacement.at(location);
            return spawnPlaced(requireModel(Objects.requireNonNull(modelId,"modelId")),origin,0,0,persistent);
        }

        private Instance placed(String id){
            Instance instance=instances.get(id);
            if(instance==null||instance.owner!=null)throw Messages.failure("error.instance.missing");
            if(instance.removeAt!=Long.MAX_VALUE)throw Messages.failure("error.instance.death");
            return instance;
        }

        @Override public void play(String instanceId,String animationName,double speed){
            requirePrimaryThread();
            playPlaced(instanceId,placed(instanceId),animationName,speed);
        }

        @Override public void stop(String instanceId){
            requirePrimaryThread();
            stopPlaced(instanceId,placed(instanceId));
        }

        @Override public boolean remove(String instanceId){
            requirePrimaryThread();
            Instance instance=instances.get(instanceId);
            if(instance!=null&&(instance.owner!=null||instance.deathSubject!=null))return false;
            boolean saved=storage.placed.remove(instanceId)!=null;
            if(instance!=null)removeInstance(instanceId);
            if(saved)saveState();
            return saved||instance!=null;
        }
    }

    private String spawnModel(Model model, Location origin, float headYaw,float headPitch) {
        String id;
        do { id = UUID.randomUUID().toString().substring(0, 8); } while (instances.containsKey(id)||storage!=null&&storage.placed.containsKey(id));
        Instance instance = new Instance(model);
        instance.startTick = tick; instance.origin=origin.clone();instance.headYaw=headYaw;instance.headPitch=headPitch;
        try {
            for (var part : model.parts()) {
                Display entity;
                if (part.entityType().equals("block")) {
                    entity = origin.getWorld().spawn(origin, BlockDisplay.class, d -> {
                        d.setBlock(Objects.requireNonNull(Material.matchMaterial(part.material())).createBlockData());
                        configureDisplay(d, part);
                    });
                } else if(part.entityType().equals("item")) {
                    ItemStack item = customItem(part);
                    entity = origin.getWorld().spawn(origin, ItemDisplay.class, d -> {
                        d.setItemDisplayTransform(part.heldItem()==null?ItemDisplay.ItemDisplayTransform.NONE:HeldItemVisuals.context(part.heldItem().equals("left")));
                        d.setItemStack(part.heldItem()==null?item:new ItemStack(Material.AIR));
                        configureDisplay(d, part);
                    });
                } else {
                    entity = origin.getWorld().spawn(origin, TextDisplay.class, d -> {
                        TextDisplays.configure(d,part);
                        configureDisplay(d, part);
                    });
                }
                instance.entities.add(entity);
            }
            update(instance,0,false);
            instances.put(id, instance);
        } catch (RuntimeException e) { instance.remove(); throw e; }
        return id;
    }
    private void configureDisplay(Display display, Model.Part part) {
        display.setPersistent(false); display.setInvulnerable(true);
        display.setTransformationMatrix(Model.matrix(part, 0, false));
        display.setInterpolationDuration(1); display.setViewRange(1);
        display.setTeleportDuration(1);
    }
    private void animate() {
        tick++;
        for(var entry:new ArrayList<>(instances.entrySet())) {
            Instance i = entry.getValue();
            if(tick>=i.removeAt){removeInstance(entry.getKey());continue;}
            try {
            if(i.owner!=null){
                Player player=getServer().getPlayer(i.owner);
                if(player==null||!player.isOnline()||player.isDead()||player.getGameMode()==GameMode.SPECTATOR){removeInstance(entry.getKey());continue;}
                if(i.entities.stream().anyMatch(e->!e.isValid())){
                    // Displays can unload during a world change. Recreate without altering visibility ownership.
                    var origin=SpawnPlacement.at(player.getLocation(),player.getBodyYaw());
                    String replacement=spawnModel(i.model,origin,PlayerAnimation.relativeYaw(player.getYaw(),origin.getYaw()),player.getPitch());
                    Instance next=instances.get(replacement);next.owner=i.owner;next.wasInvisible=i.wasInvisible;next.showModelToOwner=i.showModelToOwner;next.previousLocation=player.getLocation();
                    next.hurt=i.hurt;
                    i.remove();instances.remove(entry.getKey());worn.put(next.owner,replacement);i=next;
                    attachNametag(i,player);
                    applyOwnerVisibility(i,player);
                }
                follow(i,player);
                updateAction(i,player);
                syncHeldItems(i,player);
                selfBodyVisibility.update(player,i.showModelToOwner||i.wasInvisible||player.hasPotionEffect(PotionEffectType.INVISIBILITY));
                i.equipment.hide(player,getServer().getOnlinePlayers(),tick%10==0);
            }else if(i.entities.stream().anyMatch(e -> !e.isValid())){removeInstance(entry.getKey());continue;}
            if("manual".equals(i.actionState)&&i.action!=null){
                double elapsed=(tick-i.actionStart)/20.0*i.actionSpeed;
                for(var trigger:i.model.soundsBetween(List.of(i.actionName),i.actionPreviousSoundTime,elapsed)){
                    var event=trigger.event();
                    i.origin.getWorld().playSound(i.origin,event.sound(),SoundCategory.RECORDS,event.volume(),event.pitch());
                }
                i.actionPreviousSoundTime=elapsed;
                if(!i.action.loop()&&elapsed>=i.action.duration()){beginBlend(i);i.action=null;i.actionState=null;}
            }
            if(i.hurt!=null){
                double elapsed=(tick-i.hurt.start())/20.0;
                double previous=elapsed<=.05?-.000001:elapsed-.05;
                for(var trigger:i.model.soundsBetween(List.of(i.hurt.name()),previous,elapsed)){
                    var event=trigger.event();
                    i.origin.getWorld().playSound(i.origin,event.sound(),SoundCategory.RECORDS,event.volume(),event.pitch());
                }
                if(elapsed>=i.hurt.clip().duration())i.hurt=null;
            }
            if (i.playing||i.action!=null||i.hurt!=null||!i.blendFrom.isEmpty()&&tick-i.blendStart<4) {
                double seconds=(tick-i.startTick)/20.0*i.speed;
                var baseSounds=i.playing&&(i.action==null||i.action.runsInParallel())
                    ?i.model.soundsBetween(i.animations,i.previousSoundTime,seconds):List.<Model.Trigger>of();
                for(var trigger:baseSounds){
                    var event=trigger.event();
                    i.origin.getWorld().playSound(i.origin,event.sound(),SoundCategory.RECORDS,event.volume(),event.pitch());
                }
                i.previousSoundTime=seconds;
                update(i,seconds,true);
                if(i.owner==null&&!i.animations.isEmpty()){
                    boolean finished=true;for(String name:i.animations){var clip=i.model.animations().get(name);if(clip.loop()||seconds<clip.duration())finished=false;}
                    if(finished)i.playing=false;
                }
            }else if(i.owner!=null)update(i,0,false);
            }catch(RuntimeException error){
                String id=i.owner==null?entry.getKey():worn.getOrDefault(i.owner,entry.getKey());removeInstance(id);getLogger().warning(msg(null, "log.instance-cleanup", id, messages.error(null,error)));
            }
        }
        if(debugEnabled)for(var entry:debugPanels.entrySet()){
            Player player=getServer().getPlayer(entry.getKey());
            if(player!=null&&player.isOnline())updateDebugText(player,entry.getValue());
        }
    }
    private void update(Instance i, double seconds, boolean animated) {
        var base=animated?i.animations:List.<String>of();
        var active=base.isEmpty()?null:i.model.animations().get(base.getFirst());
        double actionTime=i.chargeSample!=null&&"charge".equals(i.actionState)
            ?i.chargeSample:(tick-i.actionStart)/20.0*i.actionSpeed;
        if("use".equals(i.actionState)&&i.action!=null&&i.action.loop())actionTime=Math.min(actionTime,Math.nextDown((double)i.action.duration()));
        var action=Model.Layer.of(i.action,actionTime);
        var hurt=i.hurt==null?null:new Model.Layer(i.hurt.clip(),(tick-i.hurt.start())/20.0);
        for (int n = 0; n < i.entities.size(); n++) {
            var entity = i.entities.get(n);
            var part = i.model.parts().get(n);
            var state=i.model.motion(part,active,seconds,true,action,hurt);
            var previous=i.motionValues.get(part.id());
            entity.setInterpolationDuration(state.snapFrom(previous)?0:1);
            i.motionValues.put(part.id(),state);
            var matrix=i.model.matrix(part,base,seconds,true,i.headYaw,i.headPitch,action,hurt);
            Matrix4f from=i.blendFrom.get(part.id());
            if(from!=null&&tick-i.blendStart<4&&!state.snapFrom(previous)&&!i.model.affectedBy(part,i.action)
                &&(hurt==null||!i.model.affectedBy(part,hurt.clip())))
                matrix=PoseBlend.blend(from,matrix,(tick-i.blendStart+1)/4f);
            boolean emptyHand=part.heldItem()!=null&&((ItemDisplay)entity).getItemStack().getType().isAir();
            if(state.visible()&&!emptyHand)i.matrices.put(part.id(),new Matrix4f(matrix));else i.matrices.remove(part.id());
            if(!state.visible())matrix.scale(0);
            else if(!emptyHand&&part.heldItem()!=null&&part.heldItem().equals(i.activeItemSide)){
                ItemStack stack=((ItemDisplay)entity).getItemStack();
                if(!stack.isDataOverridden(DataComponentTypes.ITEM_MODEL))
                    HeldItemVisuals.applyUsePose(matrix,stack.getType(),part.heldItem().equals("left"));
            }
            entity.setTransformationMatrix(matrix);
            if(entity instanceof TextDisplay text){
                var value=i.model.layeredText(part,active,seconds,action,hurt);
                if(!value.equals(i.textValues.get(part.id()))){TextDisplays.configure(text,value);i.textValues.put(part.id(),value);}
            }
            entity.setInterpolationDelay(0);
        }
        if(i.owner!=null)updateNametag(i);
    }
    private void syncHeldItems(Instance instance,Player player){
        boolean leftMain=player.getMainHand()==MainHand.LEFT;
        instance.activeItemSide=player.hasActiveItem()
            ? ((player.getActiveItemHand()==EquipmentSlot.HAND)==leftMain?"left":"right") : null;
        for(int index=0;index<instance.model.parts().size();index++){
            var part=instance.model.parts().get(index);
            if(part.heldItem()==null)continue;
            boolean main=part.heldItem().equals("left")==leftMain;
            ItemStack stack=player.getInventory().getItem(main?EquipmentSlot.HAND:EquipmentSlot.OFF_HAND);
            ItemDisplay display=(ItemDisplay)instance.entities.get(index);
            if(!display.getItemStack().equals(stack))display.setItemStack(stack.clone());
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for(var entry:new ArrayList<>(instances.entrySet())) {
            Instance i = entry.getValue();if(i.owner!=null)continue;
            if (i.entities.stream().anyMatch(e -> e.getWorld().equals(event.getWorld()) && e.getLocation().getBlockX() >> 4 == event.getChunk().getX() && e.getLocation().getBlockZ() >> 4 == event.getChunk().getZ())) {
                removeInstance(entry.getKey());
            }
        }
    }
    @Override public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        new ArrayList<>(instances.keySet()).forEach(this::removeInstance);
        debugEnabled=false;debugPanels.values().forEach(DebugPanel::remove);debugPanels.clear();
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        Collection<String> choices = a.length == 1 ? List.of("spawn", "itemtest", "pack", "modelinfo", "use", "takeoff", "showmemodel", "debug", "list", "reload", "play", "stop", "remove", "clear")
            : a.length == 2 ? (a[0].equalsIgnoreCase("debug") ? List.of("playerstatus") : List.of("spawn", "itemtest", "modelinfo", "use").contains(a[0].toLowerCase(Locale.ROOT)) ? models.keySet() : List.of("play", "stop", "remove").contains(a[0].toLowerCase(Locale.ROOT)) ? instances.keySet() : List.of()) : List.of();
        if(a.length == 3 && a[0].equalsIgnoreCase("play")) {
            var instance = instances.get(a[1]);
            if(instance != null && instance.model.animations() != null)choices = instance.model.animations().keySet();
        }
        if(a.length==4 && a[0].equalsIgnoreCase("play"))choices=List.of("0.5","1","2");
        if(a.length==3&&a[0].equalsIgnoreCase("use")&&canUseOthers(s))choices=getServer().getOnlinePlayers().stream().filter(p->!(s instanceof Player viewer)||viewer.canSee(p)).map(Player::getName).toList();
        String prefix = a.length > 0 ? a[a.length - 1].toLowerCase(Locale.ROOT) : "";
        if(a.length>1&&!allowed(s,a[0].toLowerCase(Locale.ROOT)))return List.of();
        return choices.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).filter(v->a.length!=1||allowed(s,v)).toList();
    }
}
