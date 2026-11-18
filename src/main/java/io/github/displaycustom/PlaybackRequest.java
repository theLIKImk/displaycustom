package io.github.displaycustom;

public record PlaybackRequest(String id,String animation,double speed) {
    public static PlaybackRequest parse(String[] args){
        if(args.length!=4)throw Messages.failure("error.play.syntax");
        if(!args[2].matches("[a-z][a-z0-9_-]{0,47}"))throw Messages.failure("error.play.single");
        double speed;try{speed=Double.parseDouble(args[3]);}catch(NumberFormatException e){throw Messages.failure("error.play.speed");}
        if(!Double.isFinite(speed)||speed<.05||speed>20)throw Messages.failure("error.play.speed");
        return new PlaybackRequest(args[1],args[2],speed);
    }
    public double seconds(long elapsedTicks){return elapsedTicks/20.0*speed;}
}
