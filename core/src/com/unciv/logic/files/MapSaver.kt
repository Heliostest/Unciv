package com.unciv.logic.files

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.screens.savescreens.Gzip

object MapSaver {

    const val mapsFolder = "maps"
    var saveZipped = true

    private fun getMap(mapName: String) = UncivGame.Current.files.getLocalFile("$mapsFolder/$mapName")

    fun mapFromSavedString(mapString: String): TileMap {
        val unzippedJson = try {
            Gzip.unzip(mapString.trim())
        } catch (_: Exception) {
            mapString
        }
        return mapFromJson(unzippedJson)
    }
    fun mapToSavedString(tileMap: TileMap): String {
        tileMap.assignContinents(TileMap.AssignContinentsMode.Reassign)
        val mapJson = json().toJson(tileMap)
        return if (saveZipped) Gzip.zip(mapJson) else mapJson
    }

    fun saveMap(mapName: String, tileMap: TileMap) {
        getMap(mapName).writeString(mapToSavedString(tileMap), false, Charsets.UTF_8.name())
    }

    fun loadMap(mapFile: FileHandle): TileMap {
        return mapFromSavedString(mapFile.readString(Charsets.UTF_8.name()))
    }

    fun getMaps(): Array<FileHandle> = UncivGame.Current.files.getLocalFile(mapsFolder).list()

    /** Same search order as [com.unciv.ui.screens.newgamescreen.MapFileSelectTable] (user maps, then per-mod maps). */
    fun findMapFileByName(mapFileName: String): FileHandle? {
        val baseDir = UncivGame.Current.files.getLocalFile(mapsFolder)
        if (baseDir.exists()) {
            baseDir.list().asSequence().firstOrNull { it.name() == mapFileName }?.let { return it }
        }
        for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
            val modMaps = modFolder.child(mapsFolder)
            if (modMaps.exists())
                modMaps.list().asSequence().firstOrNull { it.name() == mapFileName }?.let { return it }
        }
        return null
    }

    private fun mapFromJson(json: String): TileMap = json().fromJson(TileMap::class.java, json)

    fun loadMapParameters(mapFile: FileHandle): MapParameters {
        return loadMapPreview(mapFile).mapParameters
    }

    fun loadMapPreview(mapFile: FileHandle): TileMap.Preview {
        return mapPreviewFromSavedString(mapFile.readString())
    }

    private fun mapPreviewFromSavedString(mapString: String): TileMap.Preview {
        val unzippedJson = try {
            Gzip.unzip(mapString.trim())
        } catch (_: Exception) {
            mapString
        }
        return json().fromJson(TileMap.Preview::class.java, unzippedJson)
    }
}
