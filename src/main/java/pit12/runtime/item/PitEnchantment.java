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
package pit12.runtime.item;

import java.util.HashMap;
import java.util.Map;

public enum PitEnchantment {
    Not_Gladiator("less_damage_nearby_players", "§9NotGlad"),
    Mirror("immune_true_damage", "§bMIR"),
    Instaboom("instaboom_tnt", "§cINSTA"),
    Eggs("eggs", "§7Eggs"),
    Paparazzi("paparazzi", "§6Papa"),
    Trash_Panda("trash_panda", "§2Trash Panda"),
    Gold_Bump("gold_per_kill", "§6Gbump"),
    Moctezuma("gold_strictly_kills", "§6MOCT"),
    Executioner("melee_execute", "§6EXE"),
    Gold_Boost("gold_boost", "§6Gboost"),
    Golden_Heart("absorption_on_kill", "§6GH"),
    Self_checkout("max_bounty_self_claim", "§6SCO"),
    Double_jump("double_jump", "§eDouble-jump"),
    Lodbrok("increase_armor_drops", "§7Lodbrok"),
    Beat_the_Spammers("melee_damage_vs_bows", "§eBTS"),
    Sweaty("streak_xp", "§bSW"),
    Lifesteal("melee_heal_on_hit", "§cLS"),
    XP_Boost("xp_boost", "§bXP Boost"),
    Sierra("gold_per_diamond_piece", "§7Sierra"),
    Pitpocket("pickpocket", "§6Pitpocket"),
    Somber("somber", "§dSomber"),
    Combo_Venom("venom", "§aVenom"),
    Misery("misery", "§dMisery"),
    Spite("spite", "§dSpite"),
    Combo_Heal("melee_combo_heal", "§cCH"),
    Combo_Swift("melee_combo_speed", "§eCS"),
    Critically_Rich("gold_per_crit", "§6CR"),
    Diamond_Stomp("melee_damage_vs_diamond", "§bDS"),
    Fancy_Raider("melee_damage_vs_leather", "§eFR"),
    Crush("melee_weakness", "§eCrush"),
    Bruiser("increased_blocking", "§9Bruiser"),
    Pants_Radar("pants_radar", "§7PR"),
    Shark("melee_damage_when_close_low_players", "§4Shark"),
    Gold_and_Boosted("melee_damage_when_absorption", "§eGaB"),
    Pain_Focus("melee_damage_when_low", "§4PF"),
    Grasshopper("melee_damage_when_on_grass", "§eGrasshopper"),
    Diamond_Allergy("less_damage_vs_diamond_weapons", "§9DA"),
    Excess("overheal_enchant", "§cExcess"),
    Ring_Armor("less_damage_from_arrows", "§9Ring"),
    Strike_Gold("gold_per_hit", "§6SG"),
    TNT("tnt", "§cTNT"),
    Protection("damage_reduction", "§9Prot"),
    Negotiator("contract_rewards", "§6NEGO"),
    David_and_Goliath("less_damage_vs_bounties", "§eDaG"),
    Fletching("bow_damage", "§eFLET"),
    What_doesn_t_kill_you("heal_on_shoot_self", "§eWDKY"),
    Respawn_Absorption("respawn_with_absorption", "§6ABS"),
    Steaks("steaks_on_kill", "§eSteaks"),
    Gamble("melee_gamble", "§eGAM"),
    Duelist("melee_strike_after_block", "§4DUEL"),
    Club_Rod("fishing_rod_enchant", "§bClub Rod"),
    Portable_Pond("water_bucket", "§bPortable Pond"),
    Pebble("increase_gold_pickup", "§6Pebble"),
    XP_Bump("xp_per_kill", "§bXP Bump"),
    Bullet_Time("blocking_cancels_projectiles", "§9BT"),
    Rogue("rogue", "§bRogue"),
    Peroxide("regen_when_hit", "§cPERO"),
    Billy("less_damage_when_high_bounty", "§6Billy"),
    Solitude("solitude", "§9SOLI"),
    Combo_Damage("melee_combo_damage", "§4CD"),
    Speedy_Kill("speed_on_kill", "§9Speedy Kill"),
    Combo_Stun("melee_stun", "§cSTUN"),
    Jumpspammer("jump_spammer", "§eJS"),
    Devil_Chicks("explosive_chickens", "§cDevil Chicks!"),
    Respawn_Resistance("respawn_with_resistance", "§9RespawnRes"),
    Gotta_go_fast("perma_speed", "§eGTGF"),
    Boo_boo("passive_health_regen", "§cBOO"),
    Wasp("bow_weakness_on_hit", "§4WASP"),
    Pin_down("pin_down", "§ePin"),
    King_Buster("melee_damage_vs_high_hp", "§eKB"),
    The_Punch("melee_launch", "§ePunch"),
    Counter_Offensive("speed_when_hit_few_times", "§9CO"),
    Phoenix("phoenix", "§cPhoenix"),
    Combo_XP("combo_xp", "§bCXP"),
    Hemorrhage("melee_bleed", "§4HEMO"),
    Punisher("melee_damage_vs_low_hp", "§ePUN"),
    Healer("melee_healer", "§cHealer"),
    Tough_Crew("tough_crew", "§bTough Crew"),
    Pullbow("pullbow", "§9PULL"),
    Telebow("telebow", "§eTELE"),
    Billionaire("melee_literally_p2w", "§4BILL"),
    Sniper("sniper", "§eSniper"),
    Volley("volley", "§eVolley"),
    Knockback("melee_knockback", "§9Knockback"),
    Royalty("royalty", "Royalty"),
    Stereo("stereo", "§bStereo"),
    Mind_Assault("mind_assault", "§dMind"),
    Berserker("melee_crit_midair", "§eBerserker"),
    Cricket("less_damage_on_grass", "§9Cricket"),
    Revitalize("regen_speed_when_low", "§cREV"),
    Bottomless_Quiver("gain_arrows_on_hit", "§7BQ"),
    Grandmaster("rod_true_damage", "§bGrandmaster"),
    First_Shot("first_shot", "§eFS"),
    Guts("melee_heal_on_kill", "§cGuts"),
    Revengeance("melee_avenge", "§eREVENG"),
    Faster_than_their_shadow("bow_combo_speed", "§9FTTS"),
    Spammer_and_Proud("bow_spammer", "§eSaP"),
    Parasite("parasite", "§cPARA"),
    Sprint_Drain("bow_slow", "§9DRAIN"),
    Bounty_Reaper("melee_damage_vs_bounties", "§6BR"),
    Counter_Janitor("resistance_on_kill", "§9CJ"),
    Gomraw_s_Heart("regen_when_ooc", "§7Gomraw's Heart"),
    Wolf_Pack("wolf_pack", "§eWolf Pack"),
    Hunt_the_Hunter("counter_bounty_hunter", "§6HtH"),
    Hearts("higher_max_hp", "§cHearts"),
    Rodback("fishing_rod_kb", "§bRodback"),
    Combo_Perun_s_Wrath("melee_lightning", "§4Perun"),
    Explosive("explosive_bow", "§cExplosive"),
    Arrow_Armory("damage_per_arrow", "§eAA"),
    Escape_Pod("escape_pod", "§cPod"),
    McSwimmer("less_damage_when_swimming", "§9Swimmer"),
    Mega_Longbow("instant_shot", "§aMLB"),
    Mixed_Combat("mixed_combat", "§eMixed Combat"),
    Chipping("arrow_true_damage", "§eCHIP"),
    Speedy_Hit("melee_speed_on_hit", "§9Speedy Hit"),
    Danger_Close("superspeed_when_low", "§9DC"),
    Sharp("plain_melee_damage", "§eSharp"),
    Assassin("sneak_teleport", "§eAssassin"),
    Last_Stand("resistance_when_low", "§9LS"),
    Singularity("singularity", "§9SING"),
    Electrolytes("refresh_speed_on_kill", "§7ELECTRO"),
    Purple_Gold("gold_break_obsidian", "§7Purple Gold"),
    Pit_MBA("pit_mba", "§6MBA"),
    Grim_Reaper("grim_reaper", "§dGrim Reaper"),
    Divine_Miracle("chance_dont_lose_life", "§bDIVINE"),
    Pit_Blob("the_blob", "§aBLOB"),
    Creative("wood_blocks", "§7Creative"),
    Prick("thorns", "§4Prick"),
    Worm("worm", "§2Worm"),
    Critically_Funky("power_against_crits", "§9CF"),
    Lucky_Shot("lucky_shot", "§4Lucky Shot"),
    Martyrdom("martyrdom", "§aMartyrdom"),
    Snowballs("snowballs", "§7Snowballs"),
    Push_comes_to_shove("punch_once_in_a_while", "§9PCTS"),
    True_Shot("bow_to_true_damage", "§4TRUE"),
    Hedge_Fund("hedge_fund", "§dHedge Fund"),
    Aegis("aegis", "§2Aegis"),
    Heartripper("heartripper", "§dHeartripper"),
    Hidden_Jewel("hidden_jewel", "§2Hidden Jewel"),
    Melee_Hidden_Jewel("melee_hidden_jewel", "§2Hidden Jewel"),
    Snowmen_Army("snowmen", "§7Snowmen Army"),
    Fractional_Reserve("fractional_reserve", "§6Fractional Reserve"),
    Needless_Suffering("needless_suffering", "§dNeedless Suffering"),
    Unite("fishers_unite", "§bUnite"),
    Robinhood("homing", "§4Robinhood"),
    Lycanthropy("lycanthropy", "§dLycanthropy"),
    Sanguisuge("sanguisuge", "§dSanguisuge"),
    Trophy("trophy", "§bTrophy"),
    Luck_of_the_Pond("luck_of_the_pond", "§bLuck of the Pond"),
    Nostalgia("nostalgia", "§dNostalgia"),
    Golden_Handcuffs("golden_handcuffs", "§dGolden Handcuffs"),
    Guardian("guardian", "§bGuardian"),
    Evil_Within("evil_within", "Evil Within"),
    Retro_Gravity_Microcosm("rgm", "§4RGM"),
    ACE_OF_SPADES("ace_of_spades", "§9Ace of Spades"),
    Brakes("brakes", "§9Brakes!"),
    Combo_Breaching_Charge("breaching_charge", "§4CBC"),
    Do_it_like_the_French("do_it_like_the_french", "§4French"),
    Heigh_ho("heigh_ho", "§4HH"),
    New_Deal("new_deal", "§6ND"),
    Really_Toxic("really_toxic", "§aReally Toxic"),
    SYBIL("sybil", "§eSYB"),
    Regularity("regularity", "§4REG"),
    Think_of_the_People("think_of_the_people", "§eTOTP"),
    GENTLEMEN_AGREEMENT("gentlemen_agreement", "Deal with the Devil");

    private static final Map<String, PitEnchantment> BY_KEY;
    static {
        Map<String, PitEnchantment> byKey = new HashMap<String, PitEnchantment>();
        for (PitEnchantment enchantment : values()) {
            byKey.put(enchantment.key, enchantment);
        }
        BY_KEY = byKey;
    }
    private final String key;
    private final String displayName;

    PitEnchantment(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PitEnchantment fromKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }
}
