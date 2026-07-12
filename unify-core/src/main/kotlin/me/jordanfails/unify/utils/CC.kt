package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.ServerVersion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.bukkit.ChatColor
import java.text.MessageFormat
import java.util.regex.Pattern

object CC {

    // ------------------------------------------------------------------------
    // Unicode Symbols
    // ------------------------------------------------------------------------
    val VERTICAL_LINE = "⎜"
    val DOUBLE_ARROW = "»"
    val CHECK_MARK = "✓"
    val X_MARK = "✗"
    val PIN = "📍"
    val GEM = "❁"
    val HEART = "❤"
    val SWORDS = "⚔"
    val SHIELD = "⛊"
    val SKULL = "☠"
    val PICKAXE = "⛏"
    val ARROW = "➠"
    val ARROW_NEXT = "→"
    val ARROW_LAST = "←"
    val STAR = "✫"
    val STAR_FILLED = "★"


    private val NMS_HANDLER: NMSHandler? by lazy { UnifyCore.instance.nms }

    @JvmStatic
    val LONG_LINE = ChatColor.STRIKETHROUGH.toString() + org.apache.commons.lang3.StringUtils.repeat("-", 53)

    @JvmStatic
    val ADMIN_PREFIX = "${ChatColor.GRAY}[${ChatColor.DARK_RED}${ChatColor.BOLD}ADMIN${ChatColor.GRAY}] "

    @JvmStatic
    val WOOD_ARROW_UP_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzA0MGZlODM2YTZjMmZiZDJjN2E5YzhlYzZiZTUxNzRmZGRmMWFjMjBmNTVlMzY2MTU2ZmE1ZjcxMmUxMCJ9fX0="

    @JvmStatic
    val WOOD_ARROW_DOWN_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzQzNzM0NmQ4YmRhNzhkNTI1ZDE5ZjU0MGE5NWU0ZTc5ZGFlZGE3OTVjYmM1YTEzMjU2MjM2MzEyY2YifX19"

    @JvmStatic
    val WOOD_ARROW_LEFT_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ=="

    @JvmStatic
    val WOOD_ARROW_RIGHT_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTliZjMyOTJlMTI2YTEwNWI1NGViYTcxM2FhMWIxNTJkNTQxYTFkODkzODgyOWM1NjM2NGQxNzhlZDIyYmYifX19"

    @JvmStatic
    val WOOD_ARROW_X_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE2Nzg3YmEzMjU2NGU3YzJmM2EwY2U2NDQ5OGVjYmIyM2I4OTg0NWU1YTY2YjVjZWM3NzM2ZjcyOWVkMzcifX19"

    @JvmStatic
    val WOOD_ARROW_Y_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzUyZmIzODhlMzMyMTJhMjQ3OGI1ZTE1YTk2ZjI3YWNhNmM2MmFjNzE5ZTFlNWY4N2ExY2YwZGU3YjE1ZTkxOCJ9fX0="

    @JvmStatic
    val WOOD_ARROW_Z_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTA1ODJiOWI1ZDk3OTc0YjExNDYxZDYzZWNlZDg1ZjQzOGEzZWVmNWRjMzI3OWY5YzQ3ZTFlMzhlYTU0YWU4ZCJ9fX0="

    @JvmStatic
    val GOLD_DOLLAR_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjUwOWRlOTgxYTc4NmE5ODBiMGJjODcxYWQ4NTViMjBjZTBiNzAxYjdhMmRmMTRjZmZmNmIzYTZlNDUyOTcyMyJ9fX0="

    @JvmStatic
    val IB_ALARM_ON_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTAzNzgwZGZjMmIxYmJmMWFiZjBmMzFkOWVhMmU1Yzc4NTkzMTE4ZTg1ZmViZTZlYjllOTBhMGEyODFiMDBiZSJ9fX0="

    @JvmStatic
    val IB_ALARM_OFF_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGRkMTJhNmMxM2QxZTk1NjU4Mjc5OTVlMjg2Yzk3ODJiYTQ2ZjJkYmE3MzE3OWYzNTc0YjdkMDY5NWNkYjcwMyJ9fX0="

    @JvmStatic
    val IB_WARNING_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGE5MDAzMzliYjk3MWYwMjhhZmU2NzI0YTg1MjE5YmEyMzM5OTE4YmE0OWMxMTlkMmZiODcxZTQ3YWM5OWIzOSJ9fX0="

    @JvmStatic
    val IB_CHAT_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjAyYWYzY2EyZDVhMTYwY2ExMTE0MDQ4Yjc5NDc1OTQyNjlhZmUyYjFiNWVjMjU1ZWU3MmI2ODNiNjBiOTliOSJ9fX0="

    @JvmStatic
    val IB_CHAT_FORBIDDEN_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzJmMDU4ZGRjMjk2OTEzMzI1OTFhYzU1YTBmZDczZjQzMjAxMTc5ODJjZmRiY2U3OTY5OTQxY2ZhOGVkOGM2YiJ9fX0="

    @JvmStatic
    val IB_SPEECH_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjQ4Y2UxY2YxOGFmMDVhNTc2ZDYwODEyMzAwMWI3OTFmZWRiNjIyOTExZWY4ZDM4YTMyMGRhM2JjYmY2ZmQyMCJ9fX0="

    @JvmStatic
    val IB_SOUND_ON_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2RkYjFlM2VjMzg2ZjhkMTg0YzI5ZmMwNGI4ZjZiNzZiMTg3OTVjMzI1YzQyOWM0OGIzNDgzNDMzMDA2N2FjZSJ9fX0="

    @JvmStatic
    val IB_SOUND_OFF_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGVhNmU2YzRmMjkyZmNjODJiZWZlOTEyYjM5MjE3ODQ3MzA4MDZiZGM0YjA0OTE2MzhlNDYzODExMDg4MjdlYiJ9fX0="

    @JvmStatic
    val IB_TALKING_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjgxNDIyZThkZGMwZDMxMDlhYTY1N2I4OWIwYjBlYjFkMjVjYjNiYzhkNTRkYzZjOTljM2M5YzA4MTQ0MDI1NCJ9fX0="

    @JvmStatic
    val IB_MUTED_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGYxMzBmNDg1YzNmNzY5N2YzMjBkZGMxMTI4Y2QzZjE3Y2RiZDM3OTE3NjRmN2E3YmI5NWNmMjUyNzM4NTg4In19fQ=="

    @JvmStatic
    val IB_UNLOCKED_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTRkNjFlYmMyOWM2MDk3MjQwNTNlNDJmNjE1YmM3NDJhMTZlZjY4Njk2MTgyOWE2ZDAxMjcwNDUyOWIxMzA4NSJ9fX0="

    @JvmStatic
    val IB_LOCKED_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmVjYjYyYzYzYjI1NzVlYzhkYjc3MWM1N2M4YjU2MDUxNWJiNTA0MTkwMjM4YTk2MWU2ZTI0M2VmNTYwMmVkNCJ9fX0="

    @JvmStatic
    val IB_BANNER_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzYxODQ2MTBjNTBjMmVmYjcyODViYzJkMjBmMzk0MzY0ZTgzNjdiYjMxNDg0MWMyMzhhNmE1MjFhMWVlMTJiZiJ9fX0="

    @JvmStatic
    val IB_CHEST_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzBhZjkyZDgyMDE0ZTA2NGUzMzhiNDM2NDVmMmNhNTE5MGQ1MmVmN2E4MWExMzc3OTY4MDdkZTA1ODY0OGFmIn19fQ=="

    @JvmStatic
    val IB_CHEST_OPEN_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTZkMmE3MTJjZGQ0NDllZjBlNzc2MDU3YjU5MDMwZGQ0MzNlYThkY2RjYTE2M2QwZmY0MGFmNmU5OTY1ZWYzMiJ9fX0="

    @JvmStatic
    val IB_CHEST_LOOTED_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2FkMDVhYzI4YmE1MjBmZDFlN2NiNDIyMTI4NjFkM2M0ZTk5NjMxMDg1MWE1ZmRiNzljNDlmMTViMjc2MzhiYiJ9fX0="

    @JvmStatic
    val IB_MUSIC_NOTE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWVjYjQ5Y2NjYzEzNmIyZjQ3OTJhYTE5MDY3ZGM2NDVhNGVmYTEyYzM3NzQxM2QxOGNkMjEyNzM4YjE5NjlhYSJ9fX0="

    @JvmStatic
    val IB_MUSIC_NOTE_GRAY_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTE2MjFlODhhY2NiYTY5ZDc4ZDJjYmY4MmZlMDU4Y2NjNjBhY2IxMDFiNGQ3MWU3YWY3MDA3M2I2YTFkYjQ5NiJ9fX0="

    @JvmStatic
    val IB_TEAM_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmU0MTVmOTUxZGM5YzM0MjA0YzIyM2YwZjUwMGNiYzBmMWRmZTc3MmJmNGI5YjNiZDE3MzAyNTFkZTY1ODEwYyJ9fX0="

    @JvmStatic
    val IB_TEAM_GRAY_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjMxNDAwZjM1YmFlMGNiMmJkZDAzMTRhNDI0ZjEzMDdiNjkyMGJkZmE2ODE0MjczNjUzMGY0OTA0NjNhNTEzYSJ9fX0="

    @JvmStatic
    val IB_FORWARD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTNiZjJmYzY5M2IxNmNiOTFiOGM4N2E0YjA4OWZkOWUxODI1ZmNhMDFjZWZiMTY1YzYxODdmYzUzOWIxNTJjOSJ9fX0="

    @JvmStatic
    val IB_BACKWARDS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2JkZjJjMzliYjVjYmEyNDQzMjllMDI4MGMwYjRhNDNlOWMzY2VhMjllMDZhYzIyMjcyMjM4ZmZiM2Q1ZTUzYiJ9fX0="

    @JvmStatic
    val IB_ICON_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmMzNWU4Njg0YzdmNzc2YmVmZWRjNDMxOWQwODE0OGM1NGJlYTM5MzIxZTFiZDVkZWY3YTU1Yjg5ZmRhYTA5OSJ9fX0="

    @JvmStatic
    val IB_GLOBE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0N2EzOTQ5OWRlNDllMjRjODkyYjA5MjU2OTQzMjkyN2RlY2JiNzM5OWUxMTg0N2YzMTA0ZmRiMTY1YjZkYyJ9fX0="

    @JvmStatic
    val IB_INBOX_NEW_MAIL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmJmM2ZjZGNjZmZkOTYzZTQzMzQ4MTgxMDhlMWU5YWUzYTgwNTY2ZDBkM2QyZDRhYjMwNTFhMmNkODExMzQ4YyJ9fX0="

    @JvmStatic
    val IB_INBOX = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmNkNWMwYzZiMDBlNDgxNDcyN2YwZDc2NGFkNTI1YTczODBhYzQ2MWMzNzYwZTI0ZWMyYjUwMjgxZDg0ZGE3OSJ9fX0="

    @JvmStatic
    val TOKEN_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjBhN2I5NGM0ZTU4MWI2OTkxNTlkNDg4NDZlYzA5MTM5MjUwNjIzN2M4OWE5N2M5MzI0OGEwZDhhYmM5MTZkNSJ9fX0="

    @JvmStatic
    val WOOD_NUMBER_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzFhOTQ2M2ZkM2M0MzNkNWUxZDlmZWM2ZDVkNGIwOWE4M2E5NzBiMGI3NGRkNTQ2Y2U2N2E3MzM0OGNhYWIifX19"

    @JvmStatic
    val IB_STACKED_PAPERS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDg4M2Q2NTZlNDljMzhjNmI1Mzc4NTcyZjMxYzYzYzRjN2E1ZGQ0Mzc1YjZlY2JjYTQzZjU5NzFjMmNjNGZmIn19fQ=="

    @JvmStatic
    val SKULL_GREEN_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzNlY2RiODQ3ZTEwMTg0OWYzMzU0NzJhYzAyZGIwZDg4OTk2YTY5MThlZWU5NTc5NmRjZjg2Y2I0N2YyMTdlIn19fQ=="

    @JvmStatic
    val SKULL_BLUE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzdiOWRmZDI4MWRlYWVmMjYyOGFkNTg0MGQ0NWJjZGE0MzZkNjYyNjg0NzU4N2YzYWM3NjQ5OGE1MWM4NjEifX19"

    @JvmStatic
    val SKULL_RED_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2I4NTJiYTE1ODRkYTllNTcxNDg1OTk5NTQ1MWU0Yjk0NzQ4YzRkZDYzYWU0NTQzYzE1ZjlmOGFlYzY1YzgifX19"

    @JvmStatic
    val GREEN_PLUS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjA1NmJjMTI0NGZjZmY5OTM0NGYxMmFiYTQyYWMyM2ZlZTZlZjZlMzM1MWQyN2QyNzNjMTU3MjUzMWYifX19"

    @JvmStatic
    val CAMERA_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTQ0MjJhODJjODk5YTljMTQ1NDM4NGQzMmNjNTRjNGFlN2ExYzRkNzI0MzBlNmU0NDZkNTNiOGIzODVlMzMwIn19fQ=="

    @JvmStatic
    val GLOBE_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjFkZDRmZTRhNDI5YWJkNjY1ZGZkYjNlMjEzMjFkNmVmYTZhNmI1ZTdiOTU2ZGI5YzVkNTljOWVmYWIyNSJ9fX0="

    @JvmStatic
    val DOLLAR_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDdiNjljOWRmYjYxMDY3Yzk0ODRkZjdkMDNlNjNmMTc4OTVjOWNkYTMzMjVjMmM1MzRhNWMyMjM1ODU1NzYzMSJ9fX0="

    @JvmStatic
    val YOUTUBE_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmI3Njg4ZGE0NjU4NmI4NTlhMWNkZTQwY2FlMWNkYmMxNWFiZTM1NjE1YzRiYzUyOTZmYWQwOTM5NDEwNWQwIn19fQ=="

    @JvmStatic
    val FRIEND_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGUzNTA1N2RhMjcxMTZlZGQ5MTY0ZTVjZmJjY2JjZDQ5OTY0ZDBiZjkwYjg3OWEyZTAzY2FmMzAzZTU4M2MyOSJ9fX0="

    @JvmStatic
    val TROPHY_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTM0YTU5MmE3OTM5N2E4ZGYzOTk3YzQzMDkxNjk0ZmMyZmI3NmM4ODNhNzZjY2U4OWYwMjI3ZTVjOWYxZGZlIn19fQ=="

    @JvmStatic
    val GRASS_BLOCK_OUTLINED_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjg3MDNmNjY3MzY1MGQzNjE2MTI5MTAyN2NiMGNmOGZiYjdhMzM5ZDY2ODgzMTc2NzQzZjQwMjQ3MzM1NDg5MyJ9fX0="

    @JvmStatic
    val DIAMOND_BLOCK_SWORD_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjAwZTdiMzNlZTJhNjAwMjc1OGFjZmUwOGM3ZGY2YmQzN2E0OTdkYzlmODAwMGMzY2E5ODI0YTJjZmFiY2FkZCJ9fX0="

    @JvmStatic
    val SUNNY_SKY_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzUzYzg5YTJhZGM0ZWU1YmExZjA1ZTVkNjRlOWI0YmI2YjMyMzJjNzIwMjhlMGNiZTM1ZTFiNzNkMGM1N2RjMSJ9fX0="

    @JvmStatic
    val BELL_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDM1NTNhY2UzMmNmYTEyZjkxOTEzMjMyODQ3NDY3YmViNTZkNjQ1ZjFjOTZjNDE0NWU3OTlkMGZiOTM3YTMwIn19fQ=="

    @JvmStatic
    val MESSAGE_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGFlN2JmNDUyMmIwM2RmY2M4NjY1MTMzNjNlYWE5MDQ2ZmRkZmQ0YWE2ZjFmMDg4OWYwM2MxZTYyMTZlMGVhMCJ9fX0="

    @JvmStatic
    val RUBIKS_CUBE_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWIxZWYyYTQ4MjlhMTFmZDkwM2I1ZTMxMDg4NjYyYThjNTZlNDcxYmI0ODY0M2MwZDlmOTUwMDZkMTgyMDIxMCJ9fX0="

    @JvmStatic
    val PING_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjViZTQ5YmJkZDFkYjM1ZGVmMDRhZDExZjA2ZGVhYWY0NWM5NjY2YzA1YmMwMmJjOGJmMTQ0NGU5OWM3ZSJ9fX0="

    @JvmStatic
    val GMAIL_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzc4YTY2OWFkZWYxY2FkMzQ0YzYwYWM5NDYzMmQyOTVkMTM4OWFjY2VhYjI0YjVkMjA4YTllYmE4YmU0NWI3YyJ9fX0="

    @JvmStatic
    val DISCORD_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzg3M2MxMmJmZmI1MjUxYTBiODhkNWFlNzVjNzI0N2NiMzlhNzVmZjFhODFjYmU0YzhhMzliMzExZGRlZGEifX19"

    @JvmStatic
    val TELEGRAM_ICON = "ewogICJ0aW1lc3RhbXAiIDogMTY1NjQyOTgyMDExOSwKICAicHJvZmlsZUlkIiA6ICIwYzE1OTI3Yjc4OTY0MTA3OTA5MWQyMjkxN2U0NmIyYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJQYXkyV2F5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2RlNGI2MzQ5OGU3MTM0NmYzMGQwY2NkODcwMDliMDk2YWVkZTgxM2U2ODMwZjFmMjY4MjQ1OWE5OTU0MWIyZmYiCiAgICB9CiAgfQp9"


    // ------------------------------------------------------------------------
    // Common Bars
    // ------------------------------------------------------------------------
    val MENU_BAR = "${ChatColor.GRAY}${ChatColor.STRIKETHROUGH}------------------------"
    val CHAT_BAR = "${ChatColor.GRAY}${ChatColor.STRIKETHROUGH}${StringUtils.repeat("-", 35)}"
    val CHAT_BAR_BLUE = "${ChatColor.BLUE}${ChatColor.STRIKETHROUGH}${StringUtils.repeat("-", 35)}"
    val SCOREBOARD_BAR = "${ChatColor.GRAY}${ChatColor.STRIKETHROUGH}----------------------"

    // ------------------------------------------------------------------------
    // Translation Utilities
    // ------------------------------------------------------------------------

    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val legacySerializer: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
    private val basicMiniTags = mapOf(
        "black" to "&0",
        "dark_blue" to "&1",
        "dark_green" to "&2",
        "dark_aqua" to "&3",
        "dark_red" to "&4",
        "dark_purple" to "&5",
        "gold" to "&6",
        "gray" to "&7",
        "grey" to "&7",
        "dark_gray" to "&8",
        "dark_grey" to "&8",
        "blue" to "&9",
        "green" to "&a",
        "aqua" to "&b",
        "red" to "&c",
        "light_purple" to "&d",
        "yellow" to "&e",
        "white" to "&f",
        "bold" to "&l",
        "italic" to "&o",
        "underlined" to "&n",
        "strikethrough" to "&m",
        "obfuscated" to "&k",
        "reset" to "&r"
    )

    /**
     * Translates color codes including custom hex (#RRGGBB) and MiniMessage tags.
     */
    fun translate(input: String): String {
        // Parse MiniMessage first, then serialize to section-colored legacy text for NMS compatibility.
        if (input.contains('<') && input.contains('>')) {
            try {
                return legacySerializer.serialize(miniMessage.deserialize(input))
            } catch (_: Exception) {
                // Invalid MiniMessage input, fall back to legacy translation below.
            }
        }
        
        // Legacy translation with hex support
        var msg = convertBasicMiniTagsToLegacy(input)
        val hexPattern = Pattern.compile("#[a-fA-F0-9]{6}")
        var matcher = hexPattern.matcher(msg)

        while (matcher.find()) {
            val hex = msg.substring(matcher.start(), matcher.end())
            val replaced = hex.replace('#', 'x').toCharArray().joinToString("") { "&$it" }
            msg = msg.replace(hex, replaced)
            matcher = hexPattern.matcher(msg)
        }

        return ChatColor.translateAlternateColorCodes('&', msg)
    }

    private fun convertBasicMiniTagsToLegacy(input: String): String {
        var output = input
        output = output.replace(Regex("</\\s*[^>]+\\s*>", RegexOption.IGNORE_CASE), "&r")
        for ((tag, code) in basicMiniTags) {
            output = output.replace(Regex("<\\s*$tag\\s*>", RegexOption.IGNORE_CASE), code)
        }
        return output
    }

    /**
     * Translates color codes using only legacy (&x) formatting.
     */
    fun translateNoHex(msg: String): String =
        ChatColor.translateAlternateColorCodes('&', msg)

    /**
     * Translates all strings in a list, ignoring null values.
     */
    fun translate(list: List<String?>): List<String> =
        list.filterNotNull().map { translate(it) }



    /**
     * Translates varargs of strings.
     */
    fun translate(vararg strings: String?): Array<String> =
        translate(strings.toList()).toTypedArray()
    /**
     * Creates a stylized clickable text label like "[USE] Command Info".
     */
    fun styleAction(color: ChatColor, clickType: String, text: String): String =
        "${color}${ChatColor.BOLD}$clickType${ChatColor.RESET}$color $text"

    /**
     * Returns a valid ChatColor by name or null if not found.
     */
    fun getValidChatColor(name: String): ChatColor? =
        runCatching { ChatColor.valueOf(name.uppercase()) }.getOrNull()

    /**
     * Formats common color names like "RED" or "GOLD" into Minecraft color codes.
     */
    fun formatNamedColors(input: String): String {
        val colorMap = mapOf(
            "BLACK" to "&0", "DARK_BLUE" to "&1", "DARK_GREEN" to "&2",
            "DARK_AQUA" to "&3", "DARK_RED" to "&4", "DARK_PURPLE" to "&5",
            "GOLD" to "&6", "GRAY" to "&7", "DARK_GRAY" to "&8", "BLUE" to "&9",
            "GREEN" to "&a", "AQUA" to "&b", "RED" to "&c", "LIGHT_PURPLE" to "&d",
            "YELLOW" to "&e", "WHITE" to "&f", "CYAN" to "&3", "LIGHT_BLUE" to "&b"
        )

        return colorMap.entries.fold(input) { acc, (name, code) ->
            acc.replace(name, code, ignoreCase = true)
        }
    }

    /**
     * Checks if string contains only color codes (&, §) or hex codes.
     */
    fun isOnlyColorCodes(input: String): Boolean {
        val standard = "(&|§)[0-9a-fk-orA-FK-OR]"
        val hex = "(&|§)#([0-9a-fA-F]{6})"
        return Pattern.compile("^($standard|$hex)+$").matcher(input).matches()
    }

    /**
     * Returns a colorized boolean — green for true, red for false.
     */
    fun colorBoolean(value: Boolean, trueText: String, falseText: String): String =
        if (value) translate("&a$trueText") else translate("&c$falseText")

    /**
     * Checks if the [ChatColor] is a formatting code (bold, underline, etc.)
     */
    fun isFormattingColor(color: ChatColor): Boolean =
        color in listOf(
            ChatColor.BOLD, ChatColor.ITALIC, ChatColor.UNDERLINE,
            ChatColor.STRIKETHROUGH, ChatColor.MAGIC, ChatColor.RESET
        )

    // ------------------------------------------------------------------------
    // High-Quality Modern HEX Colors
    // ------------------------------------------------------------------------

    val HEX_BLACK = translate("#101010")
    val HEX_DARK_BLUE = translate("#0B3D91")
    val HEX_DARK_GREEN = translate("#006E2E")
    val HEX_DARK_AQUA = translate("#008B8B")
    val HEX_DARK_RED = translate("#8B0000")
    val HEX_DARK_PURPLE = translate("#6A0DAD")
    val HEX_GOLD = translate("#FFB400")
    val HEX_GRAY = translate("#BFBFBF")
    val HEX_DARK_GRAY = translate("#555555")
    val HEX_BLUE = translate("#3C75FF")
    val HEX_GREEN = translate("#00FF66")
    val HEX_AQUA = translate("#00FFFF")
    val HEX_RED = translate("#FF4C4C")
    val HEX_LIGHT_PURPLE = translate("#FF55FF")
    val HEX_YELLOW = translate("#FFFF55")
    val HEX_WHITE = translate("#FFFFFF")

    // Accents
    val HEX_ORANGE = translate("#FFA500")
    val HEX_PINK = translate("#FF77B4")
    val HEX_LIGHT_BLUE = translate("#5AC8FA")
    val HEX_LIME = translate("#B6FF00")
    val HEX_TEAL = translate("#00CACA")
    val HEX_PURPLE = translate("#C77DFF")
    val HEX_NAVY = translate("#172B6C")
    val HEX_BROWN = translate("#9C661F")
    val HEX_ROSE = translate("#FF4F81")
    val HEX_SILVER = translate("#D9D9D9")
    val HEX_LIGHT_GRAY = translate("#CDCDCD")
    val HEX_DARK_GOLD = translate("#B8860B")

    // ------------------------------------------------------------------------
    // Additional Utility Functions
    // ------------------------------------------------------------------------

    /**
     * Strips all Minecraft color codes and hex formatting from a string.
     */
    fun stripColors(input: String): String {
        var s = input.replace(Regex("(?i)&[0-9A-FK-ORX]"), "")
        s = s.replace(Regex("(?i)§[0-9A-FK-ORX]"), "")
        s = s.replace(Regex("#[a-fA-F0-9]{6}"), "")
        return s
    }

    /**
     * Builds a simple color gradient between two hex colors as a list of colorized strings.
     * Perfect for fancy title text or UI menus.
     */
    fun gradient(fromHex: String, toHex: String, text: String): List<String> {
        fun hexToRGB(hex: String): Triple<Int, Int, Int> {
            val clean = hex.removePrefix("#")
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            return Triple(r, g, b)
        }

        val (r1, g1, b1) = hexToRGB(fromHex)
        val (r2, g2, b2) = hexToRGB(toHex)
        val chars = text.toCharArray()
        val stepR = (r2 - r1).toDouble() / (text.length - 1)
        val stepG = (g2 - g1).toDouble() / (text.length - 1)
        val stepB = (b2 - b1).toDouble() / (text.length - 1)

        return chars.mapIndexed { i, c ->
            val r = (r1 + stepR * i).toInt()
            val g = (g1 + stepG * i).toInt()
            val b = (b1 + stepB * i).toInt()
            translate(String.format("#%02X%02X%02X%s", r, g, b, c))
        }
    }
//    fun String.gradient(fromHex: String, toHex: String): List<String> {
//        fun String.toRgb(): Triple<Int, Int, Int> {
//            val hex = removePrefix("#")
//            return Triple(
//                hex.substring(0, 2).toInt(16),
//                hex.substring(2, 4).toInt(16),
//                hex.substring(4, 6).toInt(16)
//            )
//        }
//
//        if (isEmpty()) return emptyList()
//
//        val (r1, g1, b1) = fromHex.toRgb()
//        val (r2, g2, b2) = toHex.toRgb()
//        val last = maxOf(length - 1, 1)
//
//        return mapIndexed { i, c ->
//            val t = i.toDouble() / last
//
//            val r = (r1 + (r2 - r1) * t).toInt()
//            val g = (g1 + (g2 - g1) * t).toInt()
//            val b = (b1 + (b2 - b1) * t).toInt()
//
//            translate(String.format("#%02X%02X%02X%c", r, g, b, c))
//        }
//    }


    /**
     * Format placeholders in string. Example:
     *
     * format("Hello, {0}!", "Steve")
     */
    @JvmStatic
    fun format(string: String, vararg arguments: Any?): String {
        var formatted = string
        if (arguments.isNotEmpty()) {
            for (i in arguments.indices) {
                val argument: Any? = arguments[i]
                formatted = formatted.replace("{${i}}", argument?.toString() ?: "")
            }
        }
        return formatted
    }

    /**
     * Format + translate combined utility for quick use.
     */
    @JvmStatic
    fun formatTranslate(input: String, vararg arguments: Any?): String {
        return translate(MessageFormat.format(input, *arguments))
    }

    @JvmStatic
    fun translateComponent(message: String): Component {
        if(NMS_HANDLER!!.getServerVersion().isBelow(ServerVersion.v1_9_R2)) {
            throw IllegalStateException("The Kyori Component API isn't supported on 1.8.8 and below!")
        }
        return LegacyComponentSerializer.legacy(LegacyComponentSerializer.SECTION_CHAR).deserialize(
            translate(
                message
            )
        )
            .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
    }

    @JvmStatic
    fun component(message: String): Component {
        if(NMS_HANDLER!!.getServerVersion().isBelow(ServerVersion.v1_9_R2)) {
            throw IllegalStateException("The Kyori Component API isn't supported on 1.8.8 and below!")
        }
        return MiniMessage.miniMessage().deserialize(message)
            .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
    }
}
