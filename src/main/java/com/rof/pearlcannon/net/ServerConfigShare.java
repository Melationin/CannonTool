package com.rof.pearlcannon.net;

import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.config.PearlCannonConfigs;
import com.rof.pearlcannon.util.CommandAccess;
import com.rof.pearlcannon.util.ConfigNames;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端响应客户端的配置拉取请求。
 * <p>不依赖客户端是否 OP：配置本身就是这台炮的标定数据，任何玩家都可以同步到本地用于计算。</p>
 */
public final class ServerConfigShare
{
    private ServerConfigShare() {}

    public static void register()
    {
        ServerPlayNetworking.registerGlobalReceiver(PearlCannonPayloads.PullRequestPayload.TYPE,
                (payload, context) -> share(context.player(), payload.name()));
        ServerPlayNetworking.registerGlobalReceiver(PearlCannonPayloads.PushConfigPayload.TYPE,
                (payload, context) -> push(context.player(), payload));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendNames(handler.player));
    }

    private static void push(ServerPlayer player, PearlCannonPayloads.PushConfigPayload payload)
    {
        if (!CommandAccess.canUse(player.createCommandSourceStack()))
        {
            ServerPlayNetworking.send(player, new PearlCannonPayloads.PushResultPayload(payload.name(), "需要 OP"));
            return;
        }
        if (!ConfigNames.isValid(payload.name()))
        {
            ServerPlayNetworking.send(player, new PearlCannonPayloads.PushResultPayload(payload.name(), "配置名无效"));
            return;
        }
        if (!PearlCannonConfigs.put(payload.name(), payload.json()))
        {
            ServerPlayNetworking.send(player, new PearlCannonPayloads.PushResultPayload(payload.name(), "配置内容无效"));
            return;
        }

        ServerPlayNetworking.send(player, new PearlCannonPayloads.PushResultPayload(payload.name(), ""));
        broadcastNames(player.level().getServer());
    }

    /** 把当前配置名发给一个客户端，用于 /cannonClient pull 的补全。 */
    public static void sendNames(ServerPlayer player)
    {
        if (!ServerPlayNetworking.canSend(player, PearlCannonPayloads.ConfigNamesPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new PearlCannonPayloads.ConfigNamesPayload(
                String.join("\n", PearlCannonConfigs.names())));
    }

    public static void broadcastNames(MinecraftServer server)
    {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            sendNames(player);
        }
    }

    private static void share(ServerPlayer player, String requested)
    {
        String name = requested == null ? "" : requested.strip();

        List<String> names = new ArrayList<>();
        if (name.isEmpty())
        {
            names.addAll(PearlCannonConfigs.names());
        }
        else if (PearlCannonConfigs.get(name) != null)
        {
            names.add(name);
        }

        if (!ServerPlayNetworking.canSend(player, PearlCannonPayloads.ConfigPayload.TYPE))
        {
            player.sendSystemMessage(prefix("客户端未安装 CannonTool", ChatFormatting.RED));
            return;
        }

        if (names.isEmpty())
        {
            ServerPlayNetworking.send(player, new PearlCannonPayloads.PullResultPayload(0,
                    name.isEmpty() ? "没有配置" : "配置 " + name + " 不存在"));
            return;
        }

        for (String configName : names)
        {
            PearlCannonConfig config = PearlCannonConfigs.get(configName);
            if (config == null) continue;
            ServerPlayNetworking.send(player, new PearlCannonPayloads.ConfigPayload(configName,
                    PearlCannonConfigs.toJson(config)));
        }
        ServerPlayNetworking.send(player, new PearlCannonPayloads.PullResultPayload(names.size(), ""));
    }

    private static Component prefix(String text, ChatFormatting color)
    {
        return Component.literal("[CannonTool] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(color));
    }
}
