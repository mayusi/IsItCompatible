package io.github.mayusi.isitcompatible.compatdb

import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity

/** Emulator id used for Windows games (GameNative). Matches GameNativeTemplate. */
const val GAMENATIVE_EMULATOR_ID = "gamenative"

/** Platform tag used in the DB for Windows/PC games. */
const val WINDOWS_PLATFORM = "WINDOWS"

/** Returns true when this game's platform is WINDOWS (case-insensitive). */
fun GameEntity.isWindowsPlatform(): Boolean =
    platform.equals(WINDOWS_PLATFORM, ignoreCase = true)

/**
 * Policy: Windows games are GameNative-only. Returns reports filtered to
 * gamenative if the game is a Windows title, otherwise returns the list unchanged.
 */
fun List<ReportEntity>.filterForWindowsGame(game: GameEntity): List<ReportEntity> =
    if (game.isWindowsPlatform())
        filter { it.emulatorId.equals(GAMENATIVE_EMULATOR_ID, ignoreCase = true) }
    else
        this
