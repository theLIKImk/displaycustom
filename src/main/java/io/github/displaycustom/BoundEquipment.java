package io.github.displaycustom;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Viewer-only equipment overrides; never changes inventory or the player's hitbox. */
final class BoundEquipment {
    private static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND,
        EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    private final Map<UUID,Map<EquipmentSlot,ItemStack>> sent=new HashMap<>();

    void hide(Player owner,Collection<? extends Player> viewers,boolean refresh){
        Map<EquipmentSlot,ItemStack> actual=current(owner);
        Set<UUID> visible=new HashSet<>();
        for(Player viewer:viewers){
            if(viewer.getUniqueId().equals(owner.getUniqueId())||!viewer.getWorld().equals(owner.getWorld())||!viewer.canSee(owner))continue;
            visible.add(viewer.getUniqueId());
            if(refresh||!actual.equals(sent.get(viewer.getUniqueId()))){
                Map<EquipmentSlot,ItemStack> empty=new EnumMap<>(EquipmentSlot.class);
                for(var slot:SLOTS)empty.put(slot,new ItemStack(Material.AIR));
                ItemStack chest=actual.get(EquipmentSlot.CHEST);
                // The vanilla player renderer supplies both folded and gliding wing poses.
                if(chest.getType()==Material.ELYTRA)empty.put(EquipmentSlot.CHEST,chest);
                viewer.sendEquipmentChange(owner,empty);sent.put(viewer.getUniqueId(),actual);
            }
        }
        sent.keySet().retainAll(visible);
    }
    void restore(Player owner,Collection<? extends Player> viewers){
        var actual=current(owner);
        for(Player viewer:viewers)if(!viewer.getUniqueId().equals(owner.getUniqueId())&&viewer.getWorld().equals(owner.getWorld())&&viewer.canSee(owner))viewer.sendEquipmentChange(owner,actual);
        sent.clear();
    }
    private static Map<EquipmentSlot,ItemStack> current(Player owner){
        Map<EquipmentSlot,ItemStack> values=new EnumMap<>(EquipmentSlot.class);
        for(var slot:SLOTS)values.put(slot,owner.getInventory().getItem(slot).clone());
        return values;
    }
}
