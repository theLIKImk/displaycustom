package io.github.displaycustom;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
public final class SpawnPlacement {
    private SpawnPlacement() {}
    public static Location at(Location player){return at(player,player.getYaw());}
    public static Location at(Location player,float bodyYaw){Location result=player.clone();result.setYaw(bodyYaw);result.setPitch(0);return result;}
    /** Foot-block centre at mattress height; local -Z points towards the head. */
    public static Location sleeping(Location bed,BlockFace facing,boolean isHead){
        int dx=facing.getModX(),dz=facing.getModZ();
        if(Math.abs(dx)+Math.abs(dz)!=1)throw Messages.failure("error.bed.cardinal");
        Location result=bed.clone();result.setX(bed.getBlockX()+.5-(isHead?dx:0));result.setZ(bed.getBlockZ()+.5-(isHead?dz:0));
        result.setY(bed.getBlockY()+.5625);result.setPitch(0);
        result.setYaw(switch(facing){case NORTH->0;case EAST->90;case SOUTH->180;case WEST->-90;default->throw Messages.failure("error.bed.direction");});
        return result;
    }
}
