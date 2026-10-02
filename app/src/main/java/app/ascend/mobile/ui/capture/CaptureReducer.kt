package app.ascend.mobile.ui.capture

object CaptureReducer {
    fun reduce(
        state: CaptureUiState,
        action: CaptureAction,
    ): CaptureUiState =
        when (action) {
            is CaptureAction.SelectPreferredSource -> {
                if (state.step != CaptureStep.SourceSelection) {
                    state
                } else {
                    state.copy(
                        preferredSource = action.source,
                        step = CaptureStep.Tutorial(CaptureRole.FRONT),
                        errorMessage = null,
                    )
                }
            }

            is CaptureAction.ContinueTutorial -> {
                if (state.step == CaptureStep.Tutorial(action.role)) {
                    state.copy(
                        step = CaptureStep.Acquisition(action.role),
                        guidance = null,
                        errorMessage = null,
                    )
                } else {
                    state
                }
            }

            is CaptureAction.OpenCamera -> {
                if (state.step == CaptureStep.Acquisition(action.role)) {
                    state.copy(
                        step = CaptureStep.Camera(action.role),
                        guidance = defaultGuidance(action.role),
                        errorMessage = null,
                    )
                } else {
                    state
                }
            }

            is CaptureAction.MediaSelected -> {
                val allowed =
                    state.step == CaptureStep.Acquisition(action.role) ||
                        state.step == CaptureStep.Camera(action.role) ||
                        state.step == CaptureStep.Review(action.role)

                if (!allowed || action.uri.isBlank()) {
                    state
                } else {
                    state
                        .withMedia(
                            action.role,
                            CaptureMedia(
                                role = action.role,
                                source = action.source,
                                uri = action.uri,
                            ),
                        )
                        .copy(
                            step = CaptureStep.Review(action.role),
                            countdownSeconds = null,
                            guidance = null,
                            errorMessage = null,
                        )
                }
            }

            is CaptureAction.GalleryCancelled -> state

            is CaptureAction.ConfirmReview -> confirmReview(state, action.role)

            is CaptureAction.Retake -> {
                if (state.mediaFor(action.role) == null) {
                    state
                } else {
                    state
                        .withMedia(action.role, null)
                        .copy(
                            step = CaptureStep.Acquisition(action.role),
                            countdownSeconds = null,
                            guidance = null,
                            errorMessage = null,
                        )
                }
            }

            is CaptureAction.UpdateCrop -> {
                if (state.step != CaptureStep.Review(action.role)) {
                    state
                } else {
                    val media = state.mediaFor(action.role)
                    if (media == null) {
                        state
                    } else {
                        state.withMedia(
                            action.role,
                            media.copy(crop = action.crop),
                        )
                    }
                }
            }

            is CaptureAction.SelectProfileSide -> {
                if (state.profile?.confirmed == true) {
                    state
                } else {
                    state.copy(profileSide = action.side)
                }
            }

            is CaptureAction.SelectTimer -> state.copy(timerOption = action.option)

            is CaptureAction.SetGuidance -> state.copy(guidance = action.guidance)

            is CaptureAction.SetError -> state.copy(errorMessage = action.message)

            is CaptureAction.CountdownStarted -> {
                if (
                    state.step == CaptureStep.Camera(action.role) &&
                    action.seconds > 0
                ) {
                    state.copy(countdownSeconds = action.seconds)
                } else {
                    state
                }
            }

            is CaptureAction.CountdownTick -> {
                if (
                    state.step == CaptureStep.Camera(action.role) &&
                    state.countdownSeconds != null &&
                    action.seconds > 0
                ) {
                    state.copy(countdownSeconds = action.seconds)
                } else {
                    state
                }
            }

            is CaptureAction.CountdownFinished -> {
                if (state.step == CaptureStep.Camera(action.role)) {
                    state.copy(countdownSeconds = null)
                } else {
                    state
                }
            }

            CaptureAction.CountdownCancelled -> state.copy(countdownSeconds = null)
            CaptureAction.Back -> back(state)
        }

    private fun confirmReview(
        state: CaptureUiState,
        role: CaptureRole,
    ): CaptureUiState {
        if (state.step != CaptureStep.Review(role)) return state

        val media = state.mediaFor(role) ?: return state

        return when (role) {
            CaptureRole.FRONT -> {
                val confirmed = state.withMedia(role, media.copy(confirmed = true))
                if (confirmed.profile?.confirmed == true) {
                    confirmed.copy(step = CaptureStep.Ready)
                } else {
                    confirmed.copy(step = CaptureStep.Tutorial(CaptureRole.PROFILE))
                }
            }

            CaptureRole.PROFILE -> {
                if (state.front?.confirmed != true) return state
                state
                    .withMedia(role, media.copy(confirmed = true))
                    .copy(step = CaptureStep.Ready)
            }
        }
    }

    private fun back(state: CaptureUiState): CaptureUiState =
        when (val step = state.step) {
            CaptureStep.SourceSelection -> state

            is CaptureStep.Tutorial -> {
                when (step.role) {
                    CaptureRole.FRONT -> state.copy(
                        step = CaptureStep.SourceSelection,
                        errorMessage = null,
                    )

                    CaptureRole.PROFILE -> {
                        val front = state.front
                        if (front == null) {
                            state.copy(step = CaptureStep.Tutorial(CaptureRole.FRONT))
                        } else {
                            state
                                .withMedia(
                                    CaptureRole.FRONT,
                                    front.copy(confirmed = false),
                                )
                                .copy(step = CaptureStep.Review(CaptureRole.FRONT))
                        }
                    }
                }
            }

            is CaptureStep.Acquisition -> state.copy(
                step = CaptureStep.Tutorial(step.role),
                errorMessage = null,
            )

            is CaptureStep.Camera -> state.copy(
                step = CaptureStep.Acquisition(step.role),
                countdownSeconds = null,
                guidance = null,
                errorMessage = null,
            )

            is CaptureStep.Review -> state
                .withMedia(step.role, null)
                .copy(
                    step = CaptureStep.Acquisition(step.role),
                    errorMessage = null,
                )

            CaptureStep.Ready -> {
                val profile = state.profile
                if (profile == null) {
                    state
                } else {
                    state
                        .withMedia(
                            CaptureRole.PROFILE,
                            profile.copy(confirmed = false),
                        )
                        .copy(step = CaptureStep.Review(CaptureRole.PROFILE))
                }
            }
        }

    private fun CaptureUiState.withMedia(
        role: CaptureRole,
        media: CaptureMedia?,
    ): CaptureUiState =
        when (role) {
            CaptureRole.FRONT -> copy(front = media)
            CaptureRole.PROFILE -> copy(profile = media)
        }

    private fun defaultGuidance(role: CaptureRole): CaptureGuidanceMessage =
        when (role) {
            CaptureRole.FRONT -> CaptureGuidanceMessage.CENTER_FACE
            CaptureRole.PROFILE -> CaptureGuidanceMessage.TURN_TO_SIDE
        }
}
