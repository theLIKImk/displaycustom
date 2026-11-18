package io.github.displaycustom;

import java.lang.reflect.*;
import java.util.List;
import org.bukkit.entity.Player;

/** Paper 26.3 Mojang-mapped bridge: override only the owner's invisibility bit. */
final class SelfBodyVisibility {
    private final Method entityData,getValue,send;
    private final Field connection;
    private final Object flagsAccessor,byteSerializer;
    private final Constructor<?> valueConstructor,packetConstructor;

    SelfBodyVisibility(){
        try{
            Class<?> entity=Class.forName("net.minecraft.world.entity.Entity");
            Field flags=entity.getDeclaredField("DATA_SHARED_FLAGS_ID");flags.setAccessible(true);flagsAccessor=flags.get(null);
            entityData=entity.getMethod("getEntityData");
            Class<?> accessor=Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            getValue=Class.forName("net.minecraft.network.syncher.SynchedEntityData").getMethod("get",accessor);
            byteSerializer=Class.forName("net.minecraft.network.syncher.EntityDataSerializers").getField("BYTE").get(null);
            valueConstructor=Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue").getConstructor(int.class,Class.forName("net.minecraft.network.syncher.EntityDataSerializer"),Object.class);
            packetConstructor=Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket").getConstructor(int.class,List.class);
            connection=Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            send=connection.getType().getMethod("send",Class.forName("net.minecraft.network.protocol.Packet"));
        }catch(ReflectiveOperationException e){throw new IllegalStateException(Messages.failure("error.visibility.init").initCause(e));}
    }
    static byte flags(byte actual,boolean hidden){return (byte)(hidden?actual|0x20:actual&~0x20);}
    void update(Player player,boolean hidden){send(player,hidden);}
    void restore(Player player){send(player,null);}
    private void send(Player player,Boolean hidden){
        try{
            Object handle=player.getClass().getMethod("getHandle").invoke(player);
            byte actual=(Byte)getValue.invoke(entityData.invoke(handle),flagsAccessor);
            Object value=valueConstructor.newInstance(0,byteSerializer,hidden==null?actual:flags(actual,hidden));
            send.invoke(connection.get(handle),packetConstructor.newInstance(player.getEntityId(),List.of(value)));
        }catch(ReflectiveOperationException e){throw new IllegalStateException(Messages.failure("error.visibility.update").initCause(e));}
    }
}
