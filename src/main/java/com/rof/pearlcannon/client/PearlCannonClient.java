package com.rof.pearlcannon.client;

import com.rof.pearlcannon.net.PearlCannonPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

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
            if (!ClientCannonConfigs.save(payload.name(), payload.json()))
            {
                ClientMessages.send("d 珍珠炮 ", "r 保存配置 " + payload.name() + " 失败");
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
                ClientMessages.send("d 珍珠炮 ", "l 已拉取 ", "w " + payload.count(), "l 个配置");
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(PearlCannonPayloads.ConfigNamesPayload.TYPE, (payload, context) ->
                ServerCannonNames.set(payload.names()));

        ClientPlayNetworking.registerGlobalReceiver(PearlCannonPayloads.PushResultPayload.TYPE, (payload, context) ->
        {
            if (payload.error() == null || payload.error().isEmpty())
            {
                ClientMessages.send("d 珍珠炮 ", "l 已发送配置 ", "w " + payload.name());
            }
            else
            {
                ClientMessages.send("d 珍珠炮 ", "r " + payload.error());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            Component display = PearlCannonCalc.hudDisplay();
            if (display == null || client.player == null) return;
            //? >=26.2 {
            /*client.gui.hud.setOverlayMessage(display, false);
            *///?} else {
            client.gui.setOverlayMessage(display, false);
            //?}
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            PearlCannonCalc.resetState();
            ServerCannonNames.clear();
        });
    }
}
