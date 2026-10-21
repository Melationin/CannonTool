package com.rof.pearlcannon.client;

import com.rof.pearlcannon.util.Messages;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 客户端聊天输出：命令反馈走 FabricClientCommandSource，网络回包直接写进聊天栏。 */
public final class ClientMessages
{
    private ClientMessages() {}

    public static void send(FabricClientCommandSource source, Object... fields)
    {
        source.sendFeedback(Messages.c(fields));
    }

    public static void fail(FabricClientCommandSource source, Object... fields)
    {
        source.sendError(Messages.c(fields));
    }

    public static void send(Object... fields)
    {
        Component message = Messages.c(fields);
        //? >=26.2 {
        /*Minecraft.getInstance().gui.chatListener().handleSystemMessage(message, false);
        *///?} else {
        Minecraft.getInstance().getChatListener().handleSystemMessage(message, false);
        //?}
    }
}
