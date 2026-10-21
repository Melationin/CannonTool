package com.rof.pearlcannon.client;

import com.rof.pearlcannon.net.PearlCannonPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** 客户端入口：注册本地命令、接收服务端下发的配置并写入本地文件夹。 */
public class PearlCannonClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        PearlCannonPayloads.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                PearlCannonClientCommands.register(dispatcher));

        ClientPlayNetworking.registerGlobalReceiver(PearlCannonPayloads.ConfigPayload.TYPE, (payload, context) ->
        {
            if (ClientCannonConfigs.save(payload.name(), payload.json()))
            {
                ClientMessages.send("d 珍珠炮 ", "l 已保存本地配置 ", "w " + payload.name());
            }
            else
            {
                ClientMessages.send("d 珍珠炮 ", "r 配置 " + payload.name() + " 保存失败（名称或 json 无效）");
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(PearlCannonPayloads.PullResultPayload.TYPE, (payload, context) ->
        {
            if (payload.error() != null && !payload.error().isEmpty())
            {
                ClientMessages.send("d 珍珠炮 ", "r " + payload.error());
            }
            else
            {
                ClientMessages.send("d 珍珠炮 ", "l 已拉取 ", "w " + payload.count(), "l 个配置到本地");
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PearlCannonCalc.resetState());
    }
}
