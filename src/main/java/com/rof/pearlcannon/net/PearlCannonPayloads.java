package com.rof.pearlcannon.net;

import com.rof.pearlcannon.PearlCannon;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * 客户端与服务端之间同步珍珠炮配置的 payload。
 * <p>客户端只发拉取请求；服务端把配置 json 原样下发，客户端存到 config/pearlcannon/。</p>
 */
public final class PearlCannonPayloads
{
    /** 客户端 -> 服务端：请求配置，name 为空表示全部。 */
    public record PullRequestPayload(String name) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<PullRequestPayload> TYPE = payloadType("pull_request");

        public static final StreamCodec<RegistryFriendlyByteBuf, PullRequestPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PullRequestPayload::name,
                PullRequestPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** 服务端 -> 客户端：一个配置的 json。 */
    public record ConfigPayload(String name, String json) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ConfigPayload> TYPE = payloadType("config");

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ConfigPayload::name,
                ByteBufCodecs.STRING_UTF8, ConfigPayload::json,
                ConfigPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** 服务端 -> 客户端：一次拉取的结束标记，error 非空表示失败原因。 */
    public record PullResultPayload(int count, String error) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<PullResultPayload> TYPE = payloadType("pull_result");

        public static final StreamCodec<RegistryFriendlyByteBuf, PullResultPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PullResultPayload::count,
                ByteBufCodecs.STRING_UTF8, PullResultPayload::error,
                PullResultPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** 服务端 -> 客户端：当前服务端配置名，按行拼接，用于 pull 补全。 */
    public record ConfigNamesPayload(String names) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ConfigNamesPayload> TYPE = payloadType("config_names");

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigNamesPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ConfigNamesPayload::names,
                ConfigNamesPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** 客户端 -> 服务端：推送本地配置。 */
    public record PushConfigPayload(String name, String json) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<PushConfigPayload> TYPE = payloadType("push_config");

        public static final StreamCodec<RegistryFriendlyByteBuf, PushConfigPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PushConfigPayload::name,
                ByteBufCodecs.STRING_UTF8, PushConfigPayload::json,
                PushConfigPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** 服务端 -> 客户端：推送结果，error 为空表示成功。 */
    public record PushResultPayload(String name, String error) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<PushResultPayload> TYPE = payloadType("push_result");

        public static final StreamCodec<RegistryFriendlyByteBuf, PushResultPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PushResultPayload::name,
                ByteBufCodecs.STRING_UTF8, PushResultPayload::error,
                PushResultPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private PearlCannonPayloads() {}

    private static boolean registered;

    /** 主入口与客户端入口都可能调用；payload 类型只能注册一次。 */
    public static void register()
    {
        if (registered) return;
        registered = true;
        //? >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(PullRequestPayload.TYPE, PullRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PushConfigPayload.TYPE, PushConfigPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PullResultPayload.TYPE, PullResultPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigNamesPayload.TYPE, ConfigNamesPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PushResultPayload.TYPE, PushResultPayload.CODEC);
        //?} else {
        /*PayloadTypeRegistry.playC2S().register(PullRequestPayload.TYPE, PullRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PushConfigPayload.TYPE, PushConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PullResultPayload.TYPE, PullResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigNamesPayload.TYPE, ConfigNamesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PushResultPayload.TYPE, PushResultPayload.CODEC);
        *///?}
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path)
    {
        //? >=1.21.11 {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PearlCannon.MOD_ID, path));
        //?} else {
        /*return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PearlCannon.MOD_ID, path));
        *///?}
    }
}
