package com.example.tools

import org.json.JSONArray
import org.json.JSONObject

object ToolDefinitions {

    fun getToolsJsonArray(): JSONArray {
        val toolsArray = JSONArray()
        val toolObj = JSONObject()
        val decls = JSONArray()

        decls.put(createDecl(
            "google_search",
            "Searches the web for real-time information, news, weather, or facts.",
            listOf(Param("query", "STRING", "The search query to look up on the web", true))
        ))

        decls.put(createDecl(
            "save_memory",
            "Saves an important personal fact or preference about the user into local memory.",
            listOf(
                Param("fact", "STRING", "The fact or preference to remember", true),
                Param("category", "STRING", "Category: Personal, Work, Preferences, Habits, Health", false)
            )
        ))

        decls.put(createDecl(
            "recall_memory",
            "Searches the user's local memory for saved facts, preferences, and personal details.",
            listOf(Param("query", "STRING", "Keywords to search memories for", true))
        ))

        decls.put(createDecl(
            "forget_memory",
            "Removes a specific memory or fact from local memory.",
            listOf(Param("query", "STRING", "Keywords or phrase of the memory to forget", true))
        ))

        decls.put(createDecl(
            "set_reminder",
            "Sets an exact local reminder with a notification alert.",
            listOf(
                Param("title", "STRING", "Reminder title or description", true),
                Param("time_in_minutes", "INTEGER", "Time from now in minutes (e.g. 15, 60)", true)
            )
        ))

        decls.put(createDecl(
            "list_reminders",
            "Lists all active upcoming reminders.",
            emptyList()
        ))

        decls.put(createDecl(
            "cancel_reminder",
            "Cancels an existing reminder by its ID.",
            listOf(Param("reminder_id", "INTEGER", "ID of the reminder to cancel", true))
        ))

        decls.put(createDecl(
            "set_alarm",
            "Sets a clock alarm on the phone.",
            listOf(
                Param("hour", "INTEGER", "Hour in 24-hour format (0-23)", true),
                Param("minutes", "INTEGER", "Minutes (0-59)", true),
                Param("message", "STRING", "Label for the alarm", false)
            )
        ))

        decls.put(createDecl(
            "set_timer",
            "Sets a countdown timer on the phone.",
            listOf(
                Param("seconds", "INTEGER", "Duration in seconds", true),
                Param("message", "STRING", "Label for the timer", false)
            )
        ))

        decls.put(createDecl(
            "open_app",
            "Opens an installed application on the phone (e.g. WhatsApp, YouTube, Settings, Camera).",
            listOf(Param("app_name", "STRING", "Name of the app to launch", true))
        ))

        decls.put(createDecl(
            "call_contact",
            "Places a phone call to a contact or phone number.",
            listOf(
                Param("name", "STRING", "Name of the contact to call", false),
                Param("phone_number", "STRING", "Phone number to call if known", false)
            )
        ))

        decls.put(createDecl(
            "send_sms",
            "Sends an SMS text message to a contact or phone number.",
            listOf(
                Param("recipient", "STRING", "Contact name or phone number", true),
                Param("message", "STRING", "The text message to send", true)
            )
        ))

        decls.put(createDecl(
            "open_url",
            "Opens a web URL in the default browser.",
            listOf(Param("url", "STRING", "The web URL to open", true))
        ))

        decls.put(createDecl(
            "search_contacts",
            "Searches contacts on the phone by name.",
            listOf(Param("query", "STRING", "Contact name to search for", true))
        ))

        decls.put(createDecl(
            "media_control",
            "Controls media playback: play, pause, next, previous.",
            listOf(Param("action", "STRING", "Action: play, pause, next, previous", true))
        ))

        decls.put(createDecl(
            "set_volume",
            "Adjusts phone volume level from 0 to 100 percent.",
            listOf(
                Param("level_percent", "INTEGER", "Volume percentage (0-100)", true),
                Param("stream", "STRING", "Stream: media, ring, alarm, notification", false)
            )
        ))

        decls.put(createDecl(
            "set_brightness",
            "Adjusts screen brightness level from 0 to 100 percent.",
            listOf(Param("level_percent", "INTEGER", "Brightness percentage (0-100)", true))
        ))

        decls.put(createDecl(
            "flashlight",
            "Turns device flashlight on or off.",
            listOf(Param("enable", "BOOLEAN", "True to turn on, false to turn off", true))
        ))

        decls.put(createDecl(
            "do_not_disturb",
            "Toggles Do Not Disturb mode.",
            listOf(Param("enable", "BOOLEAN", "True to enable DND, false to disable", true))
        ))

        decls.put(createDecl(
            "open_settings_panel",
            "Opens a system settings panel: wifi, bluetooth, display, sound, apps.",
            listOf(Param("panel_type", "STRING", "wifi, bluetooth, display, sound, apps", true))
        ))

        decls.put(createDecl(
            "get_clipboard",
            "Reads the current text from the clipboard.",
            emptyList()
        ))

        decls.put(createDecl(
            "set_clipboard",
            "Copies text into the clipboard.",
            listOf(Param("text", "STRING", "Text to copy to clipboard", true))
        ))

        decls.put(createDecl(
            "switch_mode",
            "Switches Maya assistant mode between Jarvis and Girlfriend.",
            listOf(Param("mode", "STRING", "'jarvis' or 'girlfriend'", true))
        ))

        decls.put(createDecl(
            "wait",
            "Pauses execution for a number of seconds during agent task sequences.",
            listOf(Param("seconds", "INTEGER", "Seconds to pause (1-10)", true))
        ))

        // Screen & Phone-Control Agent Tools
        decls.put(createDecl(
            "read_screen",
            "Reads the current phone screen and returns a compact list of visible UI elements (text, description, clickable, editable, bounds).",
            emptyList()
        ))

        decls.put(createDecl(
            "tap",
            "Taps on a specific element or screen coordinates.",
            listOf(
                Param("x", "INTEGER", "X coordinate in screen pixels", false),
                Param("y", "INTEGER", "Y coordinate in screen pixels", false),
                Param("target_text", "STRING", "Text or description of the element to tap", false),
                Param("node_id", "STRING", "Resource view ID of the element if known", false)
            )
        ))

        decls.put(createDecl(
            "type_text",
            "Enters text into an active input field or identified node.",
            listOf(
                Param("text", "STRING", "The text to type", true),
                Param("node_id", "STRING", "Resource view ID of the input field if known", false)
            )
        ))

        decls.put(createDecl(
            "scroll",
            "Scrolls the current screen up, down, forward, or backward.",
            listOf(Param("direction", "STRING", "up, down, forward, backward", true))
        ))

        decls.put(createDecl(
            "press",
            "Performs system navigation: back, home, or recents.",
            listOf(Param("action", "STRING", "back, home, recents", true))
        ))

        decls.put(createDecl(
            "take_screenshot",
            "Captures a screenshot of the current screen.",
            emptyList()
        ))

        decls.put(createDecl(
            "read_notifications",
            "Reads recent incoming notifications from messaging apps (e.g. WhatsApp, Telegram).",
            emptyList()
        ))

        toolObj.put("functionDeclarations", decls)
        toolsArray.put(toolObj)
        return toolsArray
    }

    private data class Param(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean
    )

    private fun createDecl(name: String, desc: String, params: List<Param>): JSONObject {
        val decl = JSONObject()
        decl.put("name", name)
        decl.put("description", desc)

        val parameters = JSONObject()
        parameters.put("type", "OBJECT")

        val properties = JSONObject()
        val requiredArray = JSONArray()

        for (param in params) {
            val p = JSONObject()
            p.put("type", param.type)
            p.put("description", param.description)
            properties.put(param.name, p)

            if (param.required) {
                requiredArray.put(param.name)
            }
        }

        parameters.put("properties", properties)
        if (requiredArray.length() > 0) {
            parameters.put("required", requiredArray)
        }
        decl.put("parameters", parameters)
        return decl
    }
}
