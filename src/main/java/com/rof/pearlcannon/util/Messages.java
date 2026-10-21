package com.rof.pearlcannon.util;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * 简化的分段消息工具，沿用 Carpet 的写法：
 * <pre>
 * Messages.send(source, "d 珍珠炮 ", "g 配置 ", "w zzp1");
 * </pre>
 * 字段开头的字母是样式码（w 白、g 灰、y 黄、d 金、c 青、l 绿、r 红、f 深灰、i 斜体、b 粗体），
 * 后面必须跟一个空格再接文本；{@code !命令}、{@code &文本}、{@code ^提示} 分别作用在上一段上。
 */
public final class Messages
{
    private Messages() {}

    public static MutableComponent c(Object... fields)
    {
        MutableComponent message = Component.literal("");
        MutableComponent previous = null;
        for (Object field : fields)
        {
            if (field == null) continue;
            if (field instanceof MutableComponent component)
            {
                message.append(component);
                previous = component;
                continue;
            }

            String text = field.toString();
            MutableComponent current = parse(text, previous);
            if (current != previous) message.append(current);
            previous = current;
        }
        return message;
    }

    private static MutableComponent parse(String message, MutableComponent previous)
    {
        if (message.isEmpty()) return Component.literal("");
        if (Character.isWhitespace(message.charAt(0))) message = "w" + message;

        int limit = message.indexOf(' ');
        String desc = message;
        String content = "";
        if (limit >= 0)
        {
            desc = message.substring(0, limit);
            content = message.substring(limit + 1);
        }

        if (previous != null)
        {
            char code = desc.charAt(0);
            if (code == '!')
            {
                previous.setStyle(previous.getStyle().withClickEvent(new ClickEvent.RunCommand(message.substring(1))));
                return previous;
            }
            if (code == '&')
            {
                previous.setStyle(previous.getStyle().withClickEvent(new ClickEvent.CopyToClipboard(message.substring(1))));
                return previous;
            }
            if (code == '^')
            {
                previous.setStyle(previous.getStyle().withHoverEvent(new HoverEvent.ShowText(c(message.substring(1)))));
                return previous;
            }
        }

        MutableComponent component = Component.literal(content);
        component.setStyle(parseStyle(desc));
        return component;
    }

    public static Style parseStyle(String desc)
    {
        Style style = Style.EMPTY.withColor(ChatFormatting.WHITE);
        for (int index = 0; index < desc.length(); index++)
        {
            style = apply(style, desc.charAt(index));
        }
        return style;
    }

    private static Style apply(Style style, char code)
    {
        return switch (code)
        {
            case 'i' -> style.withItalic(true);
            case 's' -> style.applyFormat(ChatFormatting.STRIKETHROUGH);
            case 'u' -> style.applyFormat(ChatFormatting.UNDERLINE);
            case 'b' -> style.withBold(true);
            case 'o' -> style.applyFormat(ChatFormatting.OBFUSCATED);
            case 'w' -> style.withColor(ChatFormatting.WHITE);
            case 'y' -> style.withColor(ChatFormatting.YELLOW);
            case 'm' -> style.withColor(ChatFormatting.LIGHT_PURPLE);
            case 'r' -> style.withColor(ChatFormatting.RED);
            case 'c' -> style.withColor(ChatFormatting.AQUA);
            case 'l' -> style.withColor(ChatFormatting.GREEN);
            case 't' -> style.withColor(ChatFormatting.BLUE);
            case 'f' -> style.withColor(ChatFormatting.DARK_GRAY);
            case 'g' -> style.withColor(ChatFormatting.GRAY);
            case 'd' -> style.withColor(ChatFormatting.GOLD);
            case 'p' -> style.withColor(ChatFormatting.DARK_PURPLE);
            case 'n' -> style.withColor(ChatFormatting.DARK_RED);
            case 'q' -> style.withColor(ChatFormatting.DARK_AQUA);
            case 'e' -> style.withColor(ChatFormatting.DARK_GREEN);
            case 'v' -> style.withColor(ChatFormatting.DARK_BLUE);
            case 'k' -> style.withColor(ChatFormatting.BLACK);
            default -> style;
        };
    }

    public static void send(CommandSourceStack source, Object... fields)
    {
        source.sendSuccess(() -> c(fields), false);
    }

    public static void fail(CommandSourceStack source, Object... fields)
    {
        source.sendFailure(c(fields));
    }
}
