package io.github.displaycustom;

import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class TextDisplays {
    private TextDisplays() {}
    public static void configure(TextDisplay display,Model.Part part){
        configure(display,new Model.TextValue(part.text(),part.appearance()));
    }
    public static void configure(TextDisplay display,Model.TextValue value){
        TextStyle style=value.style();
        display.text(Component.text(value.text(),TextColor.fromHexString(style.color()))
            .decoration(TextDecoration.BOLD,style.bold()).decoration(TextDecoration.ITALIC,style.italic()));
        display.setTextOpacity(style.opacityByte());
        display.setLineWidth(style.lineWidth());
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(style.backgroundArgb()));
        display.setShadowed(style.shadow());
        display.setSeeThrough(style.seeThrough());
        display.setAlignment(TextDisplay.TextAlignment.valueOf(style.alignment().toUpperCase(Locale.ROOT)));
        display.setBillboard(Display.Billboard.FIXED);
    }
}
