package io.github.mayusi.isitcompatible.apply

/** Visible state of an apply job — shown in a sheet on Game Detail. */
sealed class ApplyJobState {
    data class Working(val message: String) : ApplyJobState()

    /**
     * v0.5: structured success state. The confirmation sheet reads every
     * field below to render copy-path / open-folder / share affordances.
     *
     * @param instructions the full INSTRUCTIONS.md content rendered for display.
     * @param stagingTreeUri SAF tree URI for the staging root (string). Used by
     *        the "Open staging folder" action.
     * @param stagedFiles each file we wrote, in order, with a human label,
     *        the writable path (showable to the user as text), and the URI
     *        to copy / open.
     * @param gameId / emulatorId / presetId persisted so the "Log my result"
     *        button in the sheet can pre-fill the journal form (Chunk 5.3).
     */
    data class Done(
        val instructions: String,
        val stagingTreeUri: String,
        val stagedFiles: List<StagedFile>,
        val gameId: String,
        val emulatorId: String,
        val presetId: String?,
    ) : ApplyJobState()

    data class Error(val message: String) : ApplyJobState()
}

/** One file written by [PresetStager.stage]. */
data class StagedFile(
    /** "Config", "INSTRUCTIONS.md", "GPU driver", etc. */
    val label: String,
    /** Human-readable path shown to the user (e.g. `IsItCompatible/configs/.../skyrim-se.json`). */
    val displayPath: String,
    /** Content URI for the file — used by share + copy-path. */
    val contentUri: String,
)
