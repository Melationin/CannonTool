package com.rof.pearlcannon;

import com.rof.pearlcannon.calibration.PearlCalibration;
import com.rof.pearlcannon.calc.ServerPearlCalc;
import com.rof.pearlcannon.command.PearlCannonConfigCommands;
import com.rof.pearlcannon.config.PearlCannonConfigs;
import com.rof.pearlcannon.net.PearlCannonPayloads;
import com.rof.pearlcannon.net.ServerConfigShare;
import com.rof.pearlcannon.trail.ServerProjectileTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PearlCannon implements ModInitializer
{
    public static final String MOD_ID = "cannontool";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize()
    {
        PearlCannonPayloads.register();
        ServerConfigShare.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PearlCannonConfigCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(PearlCalibration::onServerTick);

        if (FabricLoader.getInstance().isModLoaded("ryansserverrenderingkit"))
        {
            ServerProjectileTracker.init();
        }

        ServerPearlCalc.init();

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
        {
            PearlCannonConfigs.onServerStarted(server);
            PearlCalibration.resetState();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(PearlCannonConfigs::onServerStopping);
    }
}
