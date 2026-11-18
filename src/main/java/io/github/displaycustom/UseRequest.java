package io.github.displaycustom;

final class UseRequest {
    /** null means the sender; supplying the optional argument always needs its permission. */
    static String target(String[] args,boolean playerSender,boolean canTarget){
        if(args.length<2||args.length>3)throw Messages.failure("error.use.syntax");
        if(args.length==3){
            if(!canTarget)throw Messages.failure("error.use.permission");
            if(!args[2].matches("[a-zA-Z0-9_]{1,16}"))throw Messages.failure("error.use.player-name");
            return args[2];
        }
        if(!playerSender)throw Messages.failure("error.use.console");
        return null;
    }
}
