package com.example.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay

class AppSkills(private val context: Context) {

    companion object {
        private const val TAG = "AppSkills"
    }

    suspend fun openWhatsAppChatAndSend(phone: String?, text: String, autoSend: Boolean): String {
        return try {
            val cleanPhone = phone?.replace(Regex("[^0-9]"), "") ?: ""
            val uri = if (cleanPhone.isNotEmpty()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(text)}")
            } else {
                Uri.parse("whatsapp://send?text=${Uri.encode(text)}")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)

            if (autoSend) {
                delay(2000)
                val accessibility = MayaAccessibilityService.instance
                if (accessibility != null) {
                    val clicked = accessibility.clickNodeByTextOrId("Send", "com.whatsapp:id/send")
                    return if (clicked) {
                        "WhatsApp opened and message sent to $cleanPhone."
                    } else {
                        "WhatsApp opened with prefilled message. Tap send to confirm."
                    }
                }
            }
            "WhatsApp opened with message prefilled."
        } catch (e: Exception) {
            Log.e(TAG, "Failed WhatsApp automation: ${e.message}")
            "WhatsApp is not installed or could not be opened."
        }
    }

    fun openTelegram(usernameOrPhone: String?, text: String): String {
        return try {
            val uri = if (!usernameOrPhone.isNullOrEmpty()) {
                val clean = usernameOrPhone.removePrefix("@")
                Uri.parse("tg://resolve?domain=$clean")
            } else {
                Uri.parse("tg://msg?text=${Uri.encode(text)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Telegram opened."
        } catch (e: Exception) {
            "Telegram is not installed or could not be opened."
        }
    }

    fun openInstagramDirect(username: String?): String {
        return try {
            val uri = if (!username.isNullOrEmpty()) {
                Uri.parse("http://instagram.com/_u/$username")
            } else {
                Uri.parse("https://instagram.com/direct/inbox/")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.instagram.android")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Instagram opened."
        } catch (e: Exception) {
            "Instagram is not installed."
        }
    }

    fun openYouTubeSearch(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Searching YouTube for '$query'."
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
            "Opened YouTube web search for '$query'."
        }
    }
}
