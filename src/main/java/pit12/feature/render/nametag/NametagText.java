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
package pit12.feature.render.nametag;

import java.util.Objects;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import pit12.shared.rendering.FontWidths;

final class NametagText {
    String heldItem;
    String leggings;
    int heldItemWidth;
    int leggingsWidth;
    String name;
    int nameWidth;
    String score;
    int scoreWidth;
    float x;
    float y;
    float scale;
    private String displayName;
    private String teamPrefix;
    private String teamSuffix;
    private String baseName;
    private String nameSuffix;
    private boolean dynamicName;
    private boolean nicked;
    private String realName;
    private String health;
    private String countdowns;
    private String suffix = "";
    private int scorePoints;
    private String scoreTitle;

    NametagText(String heldItem, String leggings, FontRenderer font) {
        updateEnchantments(heldItem, leggings, font);
    }

    void updateEnchantments(String heldItem, String leggings, FontRenderer font) {
        if (!Objects.equals(this.heldItem, heldItem)) {
            this.heldItem = heldItem;
            heldItemWidth = heldItem == null ? 0 : FontWidths.measure(font, heldItem);
        }
        if (!Objects.equals(this.leggings, leggings)) {
            this.leggings = leggings;
            leggingsWidth = leggings == null ? 0 : FontWidths.measure(font, leggings);
        }
    }

    String updateSuffix(boolean nicked, String realName, String health, String countdowns) {
        if (this.nicked == nicked && Objects.equals(this.realName, realName)
                && Objects.equals(this.health, health)
                && Objects.equals(this.countdowns, countdowns))
            return suffix;
        this.nicked = nicked;
        this.realName = realName;
        this.health = health;
        this.countdowns = countdowns;
        StringBuilder text = new StringBuilder();
        if (realName != null)
            text.append(" §c(§7").append(realName).append("§c)");
        else if (nicked)
            text.append(" §e§lN");
        if (health != null)
            text.append(" §c").append(health);
        suffix = text.append(countdowns).toString();
        return suffix;
    }

    void updateName(EntityPlayer player, String suffix, FontRenderer font) {
        String displayName = player.getDisplayNameString();
        Team team = player.getTeam();
        String prefix =
                team instanceof ScorePlayerTeam ? ((ScorePlayerTeam) team).getColorPrefix() : null;
        String suffixText =
                team instanceof ScorePlayerTeam ? ((ScorePlayerTeam) team).getColorSuffix() : null;
        boolean dynamicName = !player.getPrefixes().isEmpty() || !player.getSuffixes().isEmpty()
                || team != null && team.getClass() != ScorePlayerTeam.class;
        String baseName = this.baseName;
        // Extra Forge components and custom teams can change their formatting in place.
        if (baseName == null || !Objects.equals(this.displayName, displayName)
                || !Objects.equals(teamPrefix, prefix) || !Objects.equals(teamSuffix, suffixText)
                || this.dynamicName || dynamicName) {
            baseName = player.getDisplayName().getFormattedText();
            this.displayName = displayName;
            teamPrefix = prefix;
            teamSuffix = suffixText;
        }
        this.dynamicName = dynamicName;
        if (!Objects.equals(this.baseName, baseName) || !Objects.equals(nameSuffix, suffix)) {
            this.baseName = baseName;
            nameSuffix = suffix;
            name = suffix.isEmpty() ? baseName : baseName + suffix;
            nameWidth = FontWidths.measure(font, name);
        }
    }

    void updateScore(EntityPlayer player, Scoreboard scoreboard, ScoreObjective objective,
            FontRenderer font) {
        if (objective == null) {
            score = null;
            scoreWidth = 0;
            return;
        }
        int points = scoreboard.getValueFromObjective(player.getName(), objective).getScorePoints();
        String title = objective.getDisplayName();
        if (score == null || scorePoints != points || !Objects.equals(scoreTitle, title)) {
            scorePoints = points;
            scoreTitle = title;
            score = points + " " + title;
            scoreWidth = FontWidths.measure(font, score);
        }
    }
}
