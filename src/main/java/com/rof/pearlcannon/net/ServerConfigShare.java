package com.rof.pearlcannon.net;

import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.config.PearlCannonConfigs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
            player.sendSystemMessage(prefix("客户端没有安装珍珠炮，无法接收配置", ChatFormatting.RED));
            return;
        }

        if (names.isEmpty())
        {
            ServerPlayNetworking.send(player, new PearlCannonPayloads.PullResultPayload(0,
                    name.isEmpty() ? "服务端还没有任何配置" : "配置 " + name + " 不存在"));
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
        return Component.literal("[珍珠炮] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(color));
    }
}
