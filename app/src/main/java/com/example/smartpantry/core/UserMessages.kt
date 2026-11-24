package com.example.smartpantry.core


import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Technical details belong in logs, never in a snackbar. */
internal fun isConnectionFailure(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(8).any {
    it is java.net.SocketException || it is java.net.UnknownHostException ||
            it is java.net.SocketTimeoutException || it is javax.net.ssl.SSLException ||
            (it is IOException && it !is java.io.FileNotFoundException &&
                    listOf("connection", "network", "resolve host", "timeout", "timed out", "socket", "failed to connect").any { word -> it.message?.contains(word,true)==true }) ||
            it.javaClass.simpleName.contains("Timeout", true) ||
            it.javaClass.simpleName.contains("UnresolvedAddress", true)
}
internal fun userMessage(error: Throwable): String {
    if (error is CancellationException) throw error
    val detail = generateSequence(error) { it.cause }.take(8).mapNotNull { it.message }.joinToString(" ")
    val message = detail.lowercase()
    return when {
        generateSequence(error){it.cause}.any{it is java.io.FileNotFoundException} || "no content provider" in message -> "The selected photo cannot be opened on this device. Choose the photo again or remove it, then retry."
        isConnectionFailure(error) -> "No internet connection, or the server could not be reached. Check your connection and try again."
        "429" in message || "rate limit" in message || "quota" in message -> "Too many requests right now. Please wait a moment and try again."
        "jwt" in message || "401" in message || "sign in" in message -> "Your session has expired. Please sign in again."
        "403" in message || "permission denied" in message || "row-level security" in message -> "You do not have permission to change this item. Please sign in to the correct account."
        "pgrst202" in message || "does not exist" in message || "schema cache" in message -> "This feature is not ready on the server. Please contact the app administrator."
        "decimal places" in message || "whole quantity" in message || "quantity above" in message -> "Check the quantity. Counts use whole numbers; weights and volumes allow up to 3 decimal places, up to 999."
        "unit price" in message -> "Enter a non-negative unit price with up to 2 decimal places."
        "combined quantity" in message || "required quantity exceeds" in message -> "The combined quantity is too large for this list. Edit the item using another unit or split the quantity."
        "already stored" in message -> "This item is already in your pantry. Refresh the list to see its status."
        "no longer exists" in message || "404" in message -> "This item is no longer available. Refresh and try again."
        "ingredient is unavailable" in message -> "This ingredient is no longer available. Choose another ingredient and try again."
        "23505" in message || "duplicate key" in message -> "This item already exists. Refresh the list before trying again."
        error is IllegalArgumentException && detail.length <= 240 && !detail.contains("http",true) -> detail
        error is IllegalStateException && detail.length <= 240 && !detail.contains("http",true) && !detail.contains("exception",true) -> detail
        else -> "We could not complete this action. Your form is still here. Please try again."
    }
}