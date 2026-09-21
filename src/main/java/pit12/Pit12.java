/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import pit12.bootstrap.ClientBootstrap;

@Mod(modid = Pit12.MOD_ID, version = Pit12.VERSION, useMetadata = true,
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*",
        clientSideOnly = true)
public final class Pit12 {
    public static final String MOD_ID = "pit12";
    public static final String VERSION = "0.1";
    private ClientBootstrap bootstrap;

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        bootstrap = new ClientBootstrap();
        bootstrap.start();
    }
}
