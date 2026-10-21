package com.rof.pearlcannon.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/** 客户端帮助输出：用当前字体测量命令宽度，保证说明列按像素对齐。 */
public final class ClientHelp
{
    public record Row(String command, String description) {}

    private ClientHelp() {}

    public static void show(String title, String text)
    {
        List<Row> rows = new ArrayList<>();
        for (String line : text.split("\n", -1))
        {
            if (line.isEmpty()) continue;
            int tab = line.indexOf('\t');
            if (tab < 0) rows.add(new Row(line, ""));
            else rows.add(new Row(line.substring(0, tab), line.substring(tab + 1)));
        }
        show(title, rows);
    }

    public static void show(String title, List<Row> rows)
    {
        ClientMessages.send("d 珍珠炮 ", "g " + title);

        Font font = Minecraft.getInstance().font;
        int target = 0;
        for (Row row : rows)
        {
            target = Math.max(target, font.width(row.command()));
        }
        target += 12;
        int space = Math.max(1, font.width(" "));

        for (Row row : rows)
        {
            int padding = target - font.width(row.command());
            int spaces = Math.max(1, (padding + space - 1) / space);
            MutableComponent line = Component.literal("  " + row.command() + " ".repeat(spaces) + row.description())
                    .withStyle(ChatFormatting.GRAY);
            ClientMessages.send(line);
        }
    }
}
