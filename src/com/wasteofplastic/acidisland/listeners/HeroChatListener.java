/*******************************************************************************
 * This file is part of ASkyBlock.
 *
 *     ASkyBlock is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ASkyBlock is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ASkyBlock.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package com.wasteofplastic.acidisland.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.dthielke.herochat.ChannelChatEvent;
import com.wasteofplastic.acidisland.ASkyBlock;

public class HeroChatListener implements Listener {
    private ASkyBlock plugin;
    /**
     * @param plugin
     */
    public HeroChatListener(ASkyBlock plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("Herochat registered");
    }

    /**
     * Handle Herochat events if they exist
     * @param event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerHeroChat(final ChannelChatEvent event) {
        try {
            int level = plugin.getChatListener().getPlayerLevel(event.getSender().getPlayer().getUniqueId());
            event.setFormat(event.getFormat().replace("{ISLAND_LEVEL}", String.valueOf(level)));
        } catch (Exception e) {
            // Do nothing
        }
    }

}
