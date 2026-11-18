package io.github.displaycustom;

public final class Palette {
    private Palette() {}
    public static String key(String hex) {
        if(hex==null || !hex.matches("#[0-9a-fA-F]{6}"))throw Messages.failure("error.model.legacy-color");
        StringBuilder key=new StringBuilder("display_custom:colors/");
        for(int i=1;i<7;i+=2)key.append(String.format("%02x",Math.round(Integer.parseInt(hex.substring(i,i+2),16)/17f)*17));
        return key.toString();
    }
    public static boolean validReference(String key) {
        String prefix="display_custom:colors/";
        if(!key.startsWith(prefix))return true;
        String code=key.substring(prefix.length());
        if(java.util.Set.of("white","black","light_gray","gray","brown","lime","magenta","light_blue","red","orange","yellow","green","cyan","blue","purple","pink").contains(code))return true;
        return code.matches("[0-9a-f]{6}") && code.charAt(0)==code.charAt(1) && code.charAt(2)==code.charAt(3) && code.charAt(4)==code.charAt(5);
    }
}
