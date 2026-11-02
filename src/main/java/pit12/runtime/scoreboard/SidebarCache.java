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
package pit12.runtime.scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.DemandLifecycle;

/** Use this API on the client thread. */
public final class SidebarCache extends DemandLifecycle {
    private final Minecraft minecraft;
    private final ClientSession session;
    private final Runnable sessionChanged = this::onSessionChanged;
    private final Map<String, Row> rows = new HashMap<>();
    private final TreeSet<Row> ordered =
            new TreeSet<>(Comparator.comparingInt((Row row) -> row.line.score()).reversed()
                    .thenComparing(row -> row.line.entry(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(row -> row.line.entry()));
    private final Listeners<SidebarListener> listeners = new Listeners<>();
    private List<SidebarLine> lines = Collections.emptyList();
    private NetHandlerPlayClient boundHandler;
    private Scoreboard scoreboard;
    private ScoreObjective objective;

    public SidebarCache(Minecraft minecraft, ClientSession session) {
        super(session::checkThread);
        this.minecraft = minecraft;
        this.session = session;
    }

    @Override
    protected void activate() {
        session.addListener(sessionChanged);
        onSessionChanged();
    }

    @Override
    protected void deactivate() {
        session.removeListener(sessionChanged);
        bind(null);
        scoreboard = null;
        objective = null;
        rows.clear();
        ordered.clear();
        lines = Collections.emptyList();
        listeners.clear();
    }

    /** Display order, no title. Reading while idle takes a fresh snapshot. Later changes won't affect it. */
    public List<SidebarLine> lines() {
        session.checkThread();
        if (!isActive()) {
            retainOwner(this);
            try {
                return lines;
            } finally {
                releaseOwner(this);
            }
        }
        return lines;
    }

    public void addListener(SidebarListener listener) {
        session.checkThread();
        if (listeners.isEmpty())
            retainOwner(listeners);
        listeners.add(listener);
    }

    public void removeListener(SidebarListener listener) {
        session.checkThread();
        listeners.remove(listener);
        if (listeners.isEmpty())
            releaseOwner(listeners);
    }

    private void onSessionChanged() {
        bind(session.connection());
        WorldClient world = session.world();
        Scoreboard current = world == null ? null : world.getScoreboard();
        if (scoreboard != current) {
            scoreboard = current;
            objective = null;
            rows.clear();
            ordered.clear();
        }
        refreshObjective();
        publish();
    }

    private void bind(NetHandlerPlayClient handler) {
        if (boundHandler == handler) {
            return;
        }
        if (boundHandler instanceof SidebarPacketBinding) {
            ((SidebarPacketBinding) boundHandler).bindSidebarObserver(null);
        }
        boundHandler = handler;
        if (handler instanceof SidebarPacketBinding) {
            ((SidebarPacketBinding) handler).bindSidebarObserver(packet -> {
                session.refresh();
                if (isActive() && boundHandler == handler && session.connection() == handler) {
                    onPacket(packet);
                }
            });
        }
    }

    private void onPacket(Packet<?> packet) {
        if (packet instanceof S3CPacketUpdateScore) {
            if (objective != null && updateScore((S3CPacketUpdateScore) packet)) {
                publish();
            }
        } else if (refreshObjective()) {
            publish();
        } else if (objective != null && packet instanceof S3EPacketTeams
                && updateTeam((S3EPacketTeams) packet)) {
            publish();
        }
    }

    private boolean refreshObjective() {
        ScoreObjective current = null;
        if (scoreboard != null) {
            if (minecraft.thePlayer != null) {
                ScorePlayerTeam team = scoreboard.getPlayersTeam(minecraft.thePlayer.getName());
                if (team != null) {
                    int color = team.getChatFormat().getColorIndex();
                    if (color >= 0) {
                        current = scoreboard.getObjectiveInDisplaySlot(3 + color);
                    }
                }
            }
            if (current == null) {
                current = scoreboard.getObjectiveInDisplaySlot(1);
            }
        }
        if (objective == current) {
            return false;
        }
        objective = current;
        rows.clear();
        ordered.clear();
        if (current != null) {
            for (Score score : scoreboard.getSortedScores(current)) {
                observe(score.getPlayerName(), score.getScorePoints());
            }
        }
        return true;
    }

    private boolean updateScore(S3CPacketUpdateScore packet) {
        String name = packet.getObjectiveName();
        if (!name.equals(objective.getName()) && !(name.isEmpty()
                && packet.getScoreAction() == S3CPacketUpdateScore.Action.REMOVE)) {
            return false;
        }
        if (packet.getScoreAction() == S3CPacketUpdateScore.Action.CHANGE) {
            return observe(packet.getPlayerName(), packet.getScoreValue());
        }
        Row removed = rows.remove(packet.getPlayerName());
        if (removed == null) {
            return false;
        }
        ordered.remove(removed);
        return true;
    }

    private boolean observe(String entry, int score) {
        if (entry == null || entry.startsWith("#")) {
            return false;
        }
        Row row = rows.get(entry);
        if (row != null) {
            if (row.line.score() == score) {
                return false;
            }
            ordered.remove(row);
            row.line = new SidebarLine(row.line, score);
        } else {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(entry);
            row = new Row(
                    new SidebarLine(entry, ScorePlayerTeam.formatPlayerName(team, entry), score),
                    team == null ? null : team.getRegisteredName());
            rows.put(entry, row);
        }
        ordered.add(row);
        return true;
    }

    private boolean updateTeam(S3EPacketTeams packet) {
        boolean changed = false;
        int action = packet.getAction();
        if (action == 1 || action == 2) {
            for (Row row : rows.values()) {
                if (packet.getName().equals(row.team)) {
                    changed |= updateTeam(row);
                }
            }
        } else if (action == 0 || action == 3 || action == 4) {
            for (String entry : packet.getPlayers()) {
                Row row = rows.get(entry);
                if (row != null) {
                    changed |= updateTeam(row);
                }
            }
        }
        return changed;
    }

    private boolean updateTeam(Row row) {
        String entry = row.line.entry();
        ScorePlayerTeam team = scoreboard.getPlayersTeam(entry);
        row.team = team == null ? null : team.getRegisteredName();
        String text = ScorePlayerTeam.formatPlayerName(team, entry);
        if (row.line.text().equals(text)) {
            return false;
        }
        row.line = new SidebarLine(entry, text, row.line.score());
        return true;
    }

    private void publish() {
        List<SidebarLine> current = new ArrayList<>(Math.min(ordered.size(), 15));
        for (Row row : ordered) {
            current.add(row.line);
            if (current.size() == 15) {
                break;
            }
        }
        List<SidebarLine> previous = lines;
        boolean changed = previous.size() != current.size();
        for (int index = 0; !changed && index < current.size(); index++) {
            changed = !sameLine(previous.get(index), current.get(index));
        }
        if (!changed) {
            return;
        }
        lines = Collections.unmodifiableList(current);
        if (!listeners.isEmpty()) {
            listeners.notify(listener -> {
                for (int index = 0, size = previous.size(); index < size; index++) {
                    SidebarLine before = previous.get(index);
                    SidebarLine after = findLine(current, before.entry());
                    if (!sameLine(before, after)) {
                        listener.onLineChanged(before, after);
                    }
                }
                for (int index = 0, size = current.size(); index < size; index++) {
                    SidebarLine after = current.get(index);
                    if (findLine(previous, after.entry()) == null) {
                        listener.onLineChanged(null, after);
                    }
                }
            });
        }
    }

    private static SidebarLine findLine(List<SidebarLine> lines, String entry) {
        for (int index = 0, size = lines.size(); index < size; index++) {
            SidebarLine line = lines.get(index);
            if (line.entry().equals(entry)) {
                return line;
            }
        }
        return null;
    }

    // noinspection BooleanMethodIsAlwaysInverted
    private static boolean sameLine(SidebarLine previous, SidebarLine current) {
        return previous == current || previous != null && current != null
                && previous.score() == current.score() && previous.entry().equals(current.entry())
                && previous.text().equals(current.text());
    }

    private static final class Row {
        private SidebarLine line;
        private String team;

        private Row(SidebarLine line, String team) {
            this.line = line;
            this.team = team;
        }
    }
}
