package com.rof.pearlcannon.util;

import net.minecraft.commands.CommandSourceStack;

/** 命令权限判断（26.1 起改用 PermissionSet）。 */
public final class CommandAccess
{
    private CommandAccess() {}

    public static boolean canUse(CommandSourceStack source)
    {
        //? >=1.21.11 {
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR);
        //?} else {
        /*return source.hasPermission(2);
        *///?}
    }
}
