package com.unciv.logic.civilization.managers

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

object ImprovementFunctions {

    /** Generates a sequence of reasons that prevent building given [improvement].
     *  If the sequence is empty, improvement can be built immediately.
     */
    @Readonly
    fun getImprovementBuildingProblems(improvement: TileImprovement, gameContext: GameContext, tile: Tile? = null): Sequence<ImprovementBuildingProblem> = sequence {
        if (gameContext.civInfo != null) {
            val civInfo: Civilization = gameContext.civInfo

            if (improvement.uniqueTo != null && !civInfo.matchesFilter(improvement.uniqueTo!!))
                yield(ImprovementBuildingProblem.WrongCiv)
            if (civInfo.cache.uniqueImprovements.any { it.replaces == improvement.name })
                yield(ImprovementBuildingProblem.Replaced)
            if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!))
                yield(ImprovementBuildingProblem.MissingTech)
            if (improvement.getMatchingUniques(UniqueType.Unbuildable, GameContext.IgnoreConditionals)
                    .any { it.modifiers.isEmpty() })
                yield(ImprovementBuildingProblem.Unbuildable)
            else if (improvement.hasUnique(UniqueType.Unbuildable, gameContext))
                yield(ImprovementBuildingProblem.ConditionallyUnbuildable)

            if (improvement.hasUnique(UniqueType.Unavailable, gameContext))
                yield(ImprovementBuildingProblem.ConditionallyUnbuildable)

            if (improvement.getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)
                    .any { !it.conditionalsApply(gameContext) })
                yield(ImprovementBuildingProblem.UnmetConditional)

            if (improvement.getMatchingUniques(UniqueType.ObsoleteWith, gameContext)
                    .any { civInfo.tech.isResearched(it.params[0]) })
                yield(ImprovementBuildingProblem.Obsolete)

            if (improvement.getMatchingUniques(UniqueType.ConsumesResources, gameContext)
                    .any { civInfo.getResourceAmount(it.params[1]) < it.params[0].toInt() })
                yield(ImprovementBuildingProblem.MissingResources)

            if (improvement.getMatchingUniques(UniqueType.CostsResources)
                    .any { civInfo.getResourceAmount(it.params[1]) < it.params[0].toInt() *
                            (if (it.isModifiedByGameSpeed()) civInfo.gameInfo.speed.modifier else 1f) })
                yield(ImprovementBuildingProblem.MissingResources)

            val builder = gameContext.unit
            if (tile != null && builder != null) {
                for (unique in improvement.getMatchingUniques(UniqueType.ConsumesUnitsWhenBuilt, gameContext)) {
                    val required = unique.params[0].toInt()
                    val filter = unique.params[1]
                    if (countCooperatingUnitsForConsumesWhenBuilt(tile, civInfo, filter) < required)
                        yield(ImprovementBuildingProblem.NotEnoughAdjacentUnits)
                }
            }

            if (tile != null) {
                if (tile.getOwner() != civInfo
                    && !improvement.hasUnique(UniqueType.CanBuildOutsideBorders, gameContext)
                    && (!improvement.hasUnique(UniqueType.CanBuildJustOutsideBorders, gameContext)
                            || tile.neighbors.none { it.getOwner() == civInfo })
                ) 
                    yield(ImprovementBuildingProblem.OutsideBorders)
                
                val knownFeatureRemovals = tile.ruleset.nonRoadTileRemovals
                    .filter { rulesetImprovement ->
                        rulesetImprovement.techRequired == null || civInfo.tech.isResearched(rulesetImprovement.techRequired!!)
                    }
        
                if (!tile.improvementFunctions.canImprovementBeBuiltHere(improvement, civInfo.canSeeResource(tile.tileResource), knownFeatureRemovals, gameContext))
                // There are way too many conditions in that functions, besides, they are not interesting
                // at least for the current usecases. Improve if really needed.
                    yield(ImprovementBuildingProblem.Other)
            }
        }
        else {
                yield(ImprovementBuildingProblem.WrongCiv)
        }
    }

    @Readonly
    fun countCooperatingUnitsForConsumesWhenBuilt(tile: Tile, civ: Civilization, filter: String): Int =
        (sequenceOf(tile) + tile.neighbors.asSequence())
            .flatMap { it.getUnits() }
            .filter { it.civ == civ && it.matchesFilter(filter) }
            .distinctBy { it.id }
            .count()

    fun consumeUnitsWhenImprovementBuilt(
        improvement: TileImprovement,
        buildTile: Tile,
        actingUnit: MapUnit,
        actingUnitConsumedByActionModifier: Boolean
    ) {
        val unique = improvement.getMatchingUniques(UniqueType.ConsumesUnitsWhenBuilt, GameContext.IgnoreConditionals)
            .firstOrNull() ?: return
        val amount = unique.params[0].toInt()
        val filter = unique.params[1]
        val civ = actingUnit.civ
        val candidates = (sequenceOf(buildTile) + buildTile.neighbors.asSequence())
            .flatMap { it.getUnits() }
            .filter { it.civ == civ && it.matchesFilter(filter) }
            .distinctBy { it.id }
            .toMutableList()
        if (actingUnit !in candidates) return
        val toDestroy = mutableListOf<MapUnit>()
        if (actingUnitConsumedByActionModifier) {
            candidates.remove(actingUnit)
            repeat(amount - 1) {
                if (candidates.isEmpty()) return
                toDestroy.add(candidates.removeAt(0))
            }
        } else {
            toDestroy.add(actingUnit)
            candidates.remove(actingUnit)
            while (toDestroy.size < amount && candidates.isNotEmpty()) {
                toDestroy.add(candidates.removeAt(0))
            }
            if (toDestroy.size < amount) return
        }
        for (u in toDestroy.filter { it != actingUnit }) {
            if (!u.isDestroyed) u.destroy()
        }
        if (actingUnit in toDestroy && !actingUnit.isDestroyed) actingUnit.destroy()
    }
}
