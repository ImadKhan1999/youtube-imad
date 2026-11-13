package com.imad.itube.data

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

/** A short, plain message for why a video couldn't be opened. */
fun playbackErrorMessage(e: Throwable): String = when (e) {
    is SignInConfirmNotBotException ->
        "YouTube is blocking video playback on this network and asking to confirm you're not a bot. " +
            "This happens a lot on VPNs. iTube already tried its backup methods. " +
            "Switch to another VPN server or turn the VPN off, or sign in to iTube, then try again."
    is AgeRestrictedContentException -> "This video is age-restricted. Sign in to iTube to watch it."
    is GeographicRestrictionException -> "This video isn't available in your country."
    is PaidContentException -> "This video is for members or requires payment."
    is ReCaptchaException -> "YouTube is asking to verify you're not a bot. Try again later, or switch VPN server."
    is ContentNotAvailableException -> e.message ?: "This video isn't available."
    is java.io.IOException -> "No connection. Check your network and try again."
    else -> e.message ?: "Something went wrong"
}
