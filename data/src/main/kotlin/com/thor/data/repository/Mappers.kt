package com.thor.data.repository

import com.thor.core.database.model.AppEntity
import com.thor.core.database.model.FolderEntity
import com.thor.core.database.model.GameEntity
import com.thor.core.database.model.PlacementEntity
import com.thor.core.database.model.PlatformEntity
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridPlacement
import com.thor.core.model.Platform
import com.thor.core.model.PlayStats

/**
 * Entity <-> domain mapping.
 *
 * The two layers are kept separate so that a storage change (splitting a JSON
 * column out into a table, say) does not ripple into every screen, and so the
 * domain model can carry derived data the database has no column for.
 */

fun AppEntity.toDomain(): AppEntry = AppEntry(
    id = id,
    title = title,
    sortTitle = sortTitle,
    packageName = packageName,
    activityName = activityName,
    userSerial = userSerial,
    versionName = versionName,
    installedAtEpochMs = installedAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    isEmulator = isEmulator,
    isSystemApp = isSystemApp,
    isFavorite = isFavorite,
    isHidden = isHidden,
    lastPlayedEpochMs = lastPlayedEpochMs,
    launchCount = launchCount,
    customIconUri = customIconUri,
)

fun GameEntity.toDomain(): GameEntry = GameEntry(
    id = id,
    title = title,
    sortTitle = sortTitle,
    platformId = platformId,
    contentUri = contentUri,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    metadata = metadata,
    stats = PlayStats(
        totalPlayMillis = totalPlayMillis,
        launchCount = launchCount,
        lastPlayedEpochMs = lastPlayedEpochMs,
        firstPlayedEpochMs = firstPlayedEpochMs,
        performanceProfile = performanceProfile,
    ),
    emulatorPackage = emulatorPackage,
    tags = tags.toSet(),
    isFavorite = isFavorite,
    isHidden = isHidden,
)

fun FolderEntity.toDomain(): FolderEntry = FolderEntry(
    id = id,
    title = title,
    sortTitle = sortTitle,
    description = description,
    accentArgb = accentArgb,
    iconKey = iconKey,
    artworkUri = artworkUri,
    childIds = childIds,
    smartQuery = smartQuery,
    isFavorite = isFavorite,
    isHidden = isHidden,
)

fun FolderEntry.toEntity(): FolderEntity = FolderEntity(
    id = id,
    title = title,
    sortTitle = sortTitle,
    description = description,
    accentArgb = accentArgb,
    iconKey = iconKey,
    artworkUri = artworkUri,
    childIds = childIds,
    smartQuery = smartQuery,
    isFavorite = isFavorite,
    isHidden = isHidden,
)

fun PlatformEntity.toDomain(): Platform = Platform(
    id = id,
    name = name,
    shortName = shortName,
    manufacturer = manufacturer,
    releaseYear = releaseYear,
    accentArgb = accentArgb,
    romExtensions = romExtensions.toSet(),
    providerIds = providerIds,
    emulatorPackages = emulatorPackages,
    isCustom = isCustom,
    isAdded = isAdded,
    sortIndex = sortIndex,
)

fun Platform.toEntity(): PlatformEntity = PlatformEntity(
    id = id,
    name = name,
    shortName = shortName,
    manufacturer = manufacturer,
    releaseYear = releaseYear,
    accentArgb = accentArgb,
    romExtensions = romExtensions.toList(),
    providerIds = providerIds,
    emulatorPackages = emulatorPackages,
    isCustom = isCustom,
    isAdded = isAdded,
    sortIndex = sortIndex,
)

fun PlacementEntity.toDomain(): GridPlacement = GridPlacement(
    entryId = entryId,
    pageIndex = pageIndex,
    row = row,
    column = column,
)

fun GridPlacement.toEntity(
    parentFolderId: String? = null,
    folderIndex: Int = 0,
    isDock: Boolean = false,
): PlacementEntity = PlacementEntity(
    entryId = entryId,
    pageIndex = pageIndex,
    row = row,
    column = column,
    parentFolderId = parentFolderId,
    folderIndex = folderIndex,
    isDock = isDock,
)
