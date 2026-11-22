#!/usr/bin/env bash
# Look up Minecraft 26.3 / Fabric API / permissions-api signatures with javap.
# Minecraft 26.x is unobfuscated, so class and method names are the real ones.
#
# Usage:
#   mcjavap.sh net.minecraft.world.level.block.FireBlock            # public members
#   mcjavap.sh -p net.minecraft.world.entity.Display | grep Interp  # include private members
#   mcjavap.sh -p -c net.minecraft.world.level.block.FireBlock      # bytecode (what a method calls)
#   mcjavap.sh --find Explosion                                     # find class names in the jars
set -euo pipefail

MC_VERSION="${MC_VERSION:-26.3}"
LOOM="$HOME/.gradle/caches/fabric-loom/$MC_VERSION"
M2="$HOME/.gradle/caches/modules-2/files-2.1"

if [[ ! -d "$LOOM" ]]; then
	echo "Loom cache for $MC_VERSION not found; run ./gradlew build first." >&2
	exit 1
fi

jars=("$LOOM/minecraft-client.jar")
while IFS= read -r j; do jars+=("$j"); done < <(
	find "$M2/net.fabricmc.fabric-api" "$M2/com.mojang" "$M2/me.lucko" "$M2/org.joml" "$M2/it.unimi.dsi" \
		-name '*.jar' -not -name '*sources*' 2>/dev/null)

if [[ "${1:-}" == "--find" ]]; then
	pattern="${2:?pattern required}"
	for j in "$LOOM/minecraft-common.jar" "$LOOM/minecraft-client-only.jar" "${jars[@]:1}"; do
		unzip -l "$j" 2>/dev/null | awk '{print $4}' | grep -E "\.class$" | grep -E "$pattern" | grep -v '\$[0-9]' || true
	done | sed 's/\.class$//; s#/#.#g' | sort -u
	exit 0
fi

classpath=$(IFS=:; echo "${jars[*]}")
exec javap -cp "$classpath" "$@"
