#!/bin/sh
# Asks the live server for each weapon's attack damage and speed, by putting it in Hal's main hand.
cd /c/Users/jayes/OneDrive/Documents/src/minecraft/minecraft || exit 1
for w in wooden_sword wooden_axe copper_sword copper_axe stone_sword stone_axe golden_sword golden_axe iron_sword iron_axe diamond_sword diamond_axe netherite_sword netherite_axe mace wooden_spear stone_spear copper_spear iron_spear golden_spear diamond_spear netherite_spear trident; do
  docker compose exec -T minecraft console "item replace entity Hal weapon.mainhand with minecraft:$w"
  sleep 0.3
  docker compose exec -T minecraft console "say WEAPON $w"
  docker compose exec -T minecraft console "attribute Hal minecraft:attack_damage get"
  docker compose exec -T minecraft console "attribute Hal minecraft:attack_speed get"
done
sleep 2
docker compose logs --since 3m minecraft 2>&1 | grep -oE "WEAPON [a-z_]+|attack_damage for Hal is [0-9.]+|Attack Damage[^0-9]*[0-9.]+|Attack Speed[^0-9]*[0-9.]+|for entity Hal is [0-9.]+|is [0-9.]+$" | paste - - - | head -30
